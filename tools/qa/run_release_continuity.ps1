param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string[]]$PreservationCases,
    [Parameter(Mandatory=$true)][string]$KeyDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
# Build/sign only the platform probe APK, then inspect a real, already installed Release.
# Never installs/replaces/uninstalls the main app or writes its database.
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outputPath=[IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw 'Evidence directory already exists.' }
foreach($caseId in $PreservationCases) { if($caseId -cnotmatch '^[a-f0-9]{12}$') {throw 'Invalid preservation fixture id.'} }
New-Item -ItemType Directory -Path $outputPath | Out-Null
$state=[ordered]@{outcome='running';main_apk_modified=$false;cases=@{};production_qualified=$false}
$appId='com.unicornwhodev.visiondatasetstudio'
$runner="$appId.ReleaseContinuityInstrumentation"
try {
    & python -X utf8 (Join-Path $repoRoot 'tools/gradle_bootstrap.py') :app:assembleDebugAndroidTest "-PvdsInstrumentationRunner=$runner" --console=plain *> (Join-Path $outputPath 'build.log')
    if($LASTEXITCODE -ne 0) {throw 'Actual probe APK compilation failed.'}
    $sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{$env:ANDROID_SDK_ROOT}
    $signer=Join-Path $sdk 'build-tools/36.0.0/apksigner.bat'
    $aapt=Join-Path $sdk 'build-tools/36.0.0/aapt.exe'
    $source=Join-Path $repoRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    $manifest=& $aapt dump xmltree $source AndroidManifest.xml
    if($LASTEXITCODE -ne 0 -or ($manifest -join "`n") -notmatch [regex]::Escape($runner)) {throw 'Compiled APK does not declare the platform probe.'}
    $manifest | Set-Content -LiteralPath (Join-Path $outputPath 'test-manifest.txt') -Encoding utf8
    $keyPath=[IO.Path]::GetFullPath($KeyDirectory)
    if($keyPath -eq $repoRoot -or $keyPath.StartsWith($repoRoot+'\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Private key must remain outside Git.'}
    $pin=Get-Content -LiteralPath (Join-Path $repoRoot 'config/release-signing.json') -Raw | ConvertFrom-Json
    $keyReceipt=Get-Content -LiteralPath (Join-Path $keyPath 'receipt.json') -Raw | ConvertFrom-Json
    if($keyReceipt.certificate_sha256 -ne $pin.certificate_sha256 -or (Get-FileHash -LiteralPath (Join-Path $keyPath 'release.p12')).Hash.ToLowerInvariant() -ne $keyReceipt.keystore_sha256) {throw 'Signing identity mismatch.'}
    $encrypted=Join-Path $keyPath 'password.dpapi'
    if(Test-Path -LiteralPath $encrypted) {
        $secure=(Get-Content -LiteralPath $encrypted -Raw).Trim() | ConvertTo-SecureString
        $credential=[PSCredential]::new('signing',$secure)
        $password=$credential.GetNetworkCredential().Password
    } else { $password=[IO.File]::ReadAllText((Join-Path $keyPath 'recovery-password.txt')) }
    $env:VDS_PROBE_PASSWORD=$password
    $target=Join-Path $outputPath 'release-continuity-probe.apk'
    & $signer sign --ks (Join-Path $keyPath 'release.p12') --ks-key-alias $pin.alias --ks-pass env:VDS_PROBE_PASSWORD --key-pass env:VDS_PROBE_PASSWORD --out $target $source *> (Join-Path $outputPath 'sign.log')
    if($LASTEXITCODE -ne 0) {throw 'Probe APK signing failed.'}
    [Environment]::SetEnvironmentVariable('VDS_PROBE_PASSWORD',$null,'Process');$password=$null;$credential=$null
    $verification=& $signer verify --print-certs $target
    if($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch ('certificate SHA-256 digest: '+[regex]::Escape($pin.certificate_sha256))) {throw 'Signed probe certificate mismatch.'}
    $state.probe_sha256=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()
    $state.certificate_sha256=$pin.certificate_sha256
    $install=& adb -s $Serial install --no-streaming -r $target
    $install | Set-Content -LiteralPath (Join-Path $outputPath 'install-tests.txt') -Encoding utf8
    if($LASTEXITCODE -ne 0 -or ($install -join "`n") -notmatch '(?m)^Success\s*$') {throw 'Probe APK installation failed.'}
    foreach($caseId in $PreservationCases) {
        $result=& adb -s $Serial shell am instrument -w -r -e preservationCase $caseId "$appId.test/$runner"
        $text=$result -join "`n"
        $text | Set-Content -LiteralPath (Join-Path $outputPath "$caseId.txt") -Encoding utf8
        $match=[regex]::Match($text,'(?m)^INSTRUMENTATION_RESULT: release_update_evidence=(.*)$')
        if($LASTEXITCODE -ne 0 -or -not $match.Success -or $text -notmatch 'INSTRUMENTATION_CODE: -1') {throw "Release continuity assertions did not pass for $caseId."}
        $state.cases[$caseId]=$match.Groups[1].Value | ConvertFrom-Json
    }
    $state.outcome='actual_release_continuity_passed'
    Write-Output 'Actual signed Release retains the prepared human data and original model.'
} catch {
    $state.outcome='failed';$state.error=$_.Exception.Message
    throw
} finally {
    [Environment]::SetEnvironmentVariable('VDS_PROBE_PASSWORD',$null,'Process');$password=$null;$credential=$null
    $state | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $outputPath 'status.json') -Encoding utf8
}
