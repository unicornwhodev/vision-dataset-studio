param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$KeyDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
# Sign a genuinely compiled instrumentation APK. No main APK is changed.
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$source=(Resolve-Path -LiteralPath $Apk).Path
$keyPath=(Resolve-Path -LiteralPath $KeyDirectory).Path
if($keyPath -eq $repoRoot -or $keyPath.StartsWith($repoRoot+'\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Keep the private key outside Git.'}
if(Test-Path -LiteralPath $OutputDirectory) {throw 'Refusing to overwrite previous evidence.'}
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{$env:ANDROID_SDK_ROOT}
$signer=Join-Path $sdk 'build-tools/36.0.0/apksigner.bat'
$aapt=Join-Path $sdk 'build-tools/36.0.0/aapt.exe'
$badging=& $aapt dump badging $source
if($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='com.unicornwhodev.visiondatasetstudio.test'") {throw 'Expected the real instrumentation test APK.'}
$pin=Get-Content -LiteralPath (Join-Path $repoRoot 'config/release-signing.json') -Raw | ConvertFrom-Json
$keyReceipt=Get-Content -LiteralPath (Join-Path $keyPath 'receipt.json') -Raw | ConvertFrom-Json
if($keyReceipt.certificate_sha256 -ne $pin.certificate_sha256 -or (Get-FileHash -LiteralPath (Join-Path $keyPath 'release.p12')).Hash.ToLowerInvariant() -ne $keyReceipt.keystore_sha256) {throw 'Signing identity mismatch.'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$state=[ordered]@{outcome='running';source_sha256=(Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant();main_apk_modified=$false;tests_executed=$false}
try {
    $encrypted=Join-Path $keyPath 'password.dpapi'
    if(Test-Path -LiteralPath $encrypted) {
        $secure=(Get-Content -LiteralPath $encrypted -Raw).Trim() | ConvertTo-SecureString
        $credential=[PSCredential]::new('signing',$secure)
        $password=$credential.GetNetworkCredential().Password
    } else {$password=[IO.File]::ReadAllText((Join-Path $keyPath 'recovery-password.txt'))}
    $env:VDS_TEST_SIGN_PASSWORD=$password
    $target=Join-Path $OutputDirectory 'instrumentation.apk'
    & $signer sign --ks (Join-Path $keyPath 'release.p12') --ks-key-alias $pin.alias --ks-pass env:VDS_TEST_SIGN_PASSWORD --key-pass env:VDS_TEST_SIGN_PASSWORD --out $target $source *> (Join-Path $OutputDirectory 'sign.log')
    if($LASTEXITCODE -ne 0) {throw 'Test APK signing failed.'}
    $verify=& $signer verify --print-certs $target
    if($LASTEXITCODE -ne 0 -or ($verify -join "`n") -notmatch ('certificate SHA-256 digest: '+[regex]::Escape($pin.certificate_sha256))) {throw 'Signed test certificate mismatch.'}
    $state.sha256=(Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant()
    $state.certificate_sha256=$pin.certificate_sha256
    $state.outcome='test_apk_signed'
    Write-Output ('Signed test APK: '+$target)
} catch {$state.outcome='failed';throw} finally {
    [Environment]::SetEnvironmentVariable('VDS_TEST_SIGN_PASSWORD',$null,'Process');$password=$null;$credential=$null
    $state | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'status.json') -Encoding utf8
}
