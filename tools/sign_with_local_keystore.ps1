param(
    [Parameter(Mandatory=$true)][string]$KeyDirectory,
    [Parameter(Mandatory=$true)][ValidateSet('release','qualification','release-tests')][string]$Kind,
    [Parameter(Mandatory=$true)][string]$ArtifactDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$keyPath = [IO.Path]::GetFullPath($KeyDirectory)
if ($keyPath.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or $keyPath -eq $repoRoot) {
    throw 'The signing key must remain outside the repository.'
}
$pin = Get-Content -LiteralPath (Join-Path $repoRoot 'config/release-signing.json') -Raw | ConvertFrom-Json
$receipt = Get-Content -LiteralPath (Join-Path $keyPath 'receipt.json') -Raw | ConvertFrom-Json
if ($receipt.certificate_sha256 -ne $pin.certificate_sha256 -or
    (Get-FileHash -LiteralPath (Join-Path $keyPath 'release.p12')).Hash.ToLowerInvariant() -ne $receipt.keystore_sha256) {
    throw 'The key differs from its receipt or the pinned distribution identity.'
}
$encrypted = Join-Path $keyPath 'password.dpapi'
if (Test-Path -LiteralPath $encrypted) {
    $secure = (Get-Content -LiteralPath $encrypted -Raw).Trim() | ConvertTo-SecureString
    $credential = [PSCredential]::new('signing', $secure)
    $password = $credential.GetNetworkCredential().Password
} else {
    $password = [IO.File]::ReadAllText((Join-Path $keyPath 'recovery-password.txt'))
}
try {
    $env:VDS_RELEASE_KEYSTORE = Join-Path $keyPath 'release.p12'
    $env:VDS_RELEASE_KEY_ALIAS = $pin.alias
    $env:VDS_RELEASE_STORE_PASSWORD = $password
    $env:VDS_RELEASE_KEY_PASSWORD = $password
    if ($Kind -eq 'release') {
        & python -X utf8 (Join-Path $PSScriptRoot 'sign_release_candidate.py') --candidate $ArtifactDirectory --certificate-sha256 $pin.certificate_sha256
    } elseif ($Kind -eq 'release-tests') {
        & python -X utf8 (Join-Path $PSScriptRoot 'qa/sign_release_test_apks.py') --build $ArtifactDirectory
    } else {
        & python -X utf8 (Join-Path $PSScriptRoot 'qa/sign_qualification_apks.py') --output $ArtifactDirectory
    }
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed. The verified signing identity was not changed.' }
} finally {
    foreach ($name in @('VDS_RELEASE_KEYSTORE','VDS_RELEASE_KEY_ALIAS','VDS_RELEASE_STORE_PASSWORD','VDS_RELEASE_KEY_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    $password = $null
    $credential = $null
}
