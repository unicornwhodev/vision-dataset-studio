param(
    [Parameter(Mandatory=$true)][string]$PrimaryDirectory,
    [Parameter(Mandatory=$true)][string]$BackupDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$primaryPath = [IO.Path]::GetFullPath($PrimaryDirectory)
$backupPath = [IO.Path]::GetFullPath($BackupDirectory)
foreach ($path in @($primaryPath, $backupPath)) {
    if ($path.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or $path -eq $repoRoot) {
        throw 'The key and its backup must be outside the repository.'
    }
    if (Test-Path -LiteralPath $path) { throw 'Refusing to replace an existing signing directory.' }
    $ancestor = [IO.DirectoryInfo]::new([IO.Path]::GetDirectoryName($path))
    while ($ancestor) {
        if ($ancestor.Exists -and ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Signing directories must not have a reparse-point ancestor.'
        }
        $ancestor = $ancestor.Parent
    }
}
if ($primaryPath -eq $backupPath) { throw 'Use a separate backup directory.' }
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
foreach ($path in @($primaryPath, $backupPath)) {
    New-Item -ItemType Directory -Path $path | Out-Null
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner($sid)
    foreach ($identity in @($sid, $systemSid)) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $path -AclObject $acl
}
$randomBytes = [byte[]]::new(48)
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($randomBytes)
$alias = 'vision-dataset-studio-release'
$keystore = Join-Path $primaryPath 'release.p12'
$certificate = Join-Path $primaryPath 'certificate.der'
$env:VDS_KEYGEN_PASSWORD = $password
try {
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias $alias `
        -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10950 `
        -dname 'CN=Vision Dataset Studio, O=Unicorn Who Dev' `
        -storepass:env VDS_KEYGEN_PASSWORD -keypass:env VDS_KEYGEN_PASSWORD `
        2>&1 | Out-File -LiteralPath (Join-Path $primaryPath 'creation.log') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Key creation failed; inspect the private creation log.' }
    & $keytool -exportcert -keystore $keystore -alias $alias -file $certificate `
        -storepass:env VDS_KEYGEN_PASSWORD 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    $password | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString |
        Set-Content -LiteralPath (Join-Path $primaryPath 'password.dpapi') -Encoding ascii
    # This portable recovery secret is written only to the explicitly chosen private backup.
    [IO.File]::WriteAllText((Join-Path $backupPath 'recovery-password.txt'), $password, [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath $keystore -Destination (Join-Path $backupPath 'release.p12')
    Copy-Item -LiteralPath $certificate -Destination (Join-Path $backupPath 'certificate.der')
    foreach ($name in @('release.p12', 'certificate.der')) {
        if ((Get-FileHash -LiteralPath (Join-Path $primaryPath $name)).Hash -ne
            (Get-FileHash -LiteralPath (Join-Path $backupPath $name)).Hash) { throw 'Backup hash mismatch.' }
    }
    $env:VDS_KEYGEN_PASSWORD = [IO.File]::ReadAllText((Join-Path $backupPath 'recovery-password.txt'))
    $restoredCert = Join-Path $backupPath 'restore-check.der'
    & $keytool -exportcert -keystore (Join-Path $backupPath 'release.p12') -alias $alias `
        -file $restoredCert -storepass:env VDS_KEYGEN_PASSWORD 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $restoredCert).Hash -ne
        (Get-FileHash -LiteralPath $certificate).Hash) { throw 'Backup could not restore the expected certificate.' }
    $receipt = [ordered]@{
        application_id = 'com.unicornwhodev.visiondatasetstudio'
        alias = $alias
        certificate_sha256 = (Get-FileHash -LiteralPath $certificate).Hash.ToLowerInvariant()
        keystore_sha256 = (Get-FileHash -LiteralPath $keystore).Hash.ToLowerInvariant()
        algorithm = 'RSA-4096 / SHA256withRSA'
        created_at = [DateTime]::UtcNow.ToString('o')
        backup_bytes_verified = $true
        backup_password_and_certificate_verified = $true
        offsite_backup_verified = $false
    }
    $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $primaryPath 'receipt.json') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $primaryPath 'receipt.json') -Destination (Join-Path $backupPath 'receipt.json')
    @'
Private Android release signing backup. Never put this directory in Git or a release.
release.p12 and recovery-password.txt together allow signing future application updates.
Keep both files. Copy this directory to an independently protected offline backup.
The primary password.dpapi is tied to the current Windows account; the recovery password is portable.
This key cannot update rc4 or rc5 Debug installations signed with different certificates.
'@ | Set-Content -LiteralPath (Join-Path $backupPath 'RECOVERY.txt') -Encoding utf8
    Write-Output ('Signing certificate SHA-256: ' + $receipt.certificate_sha256)
    Write-Output 'Private key created; separate backup bytes and recovery credential verified.'
} finally {
    [Environment]::SetEnvironmentVariable('VDS_KEYGEN_PASSWORD', $null, 'Process')
    $password = $null
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
