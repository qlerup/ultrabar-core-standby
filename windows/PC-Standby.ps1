#Requires -Version 5.1
[CmdletBinding(DefaultParameterSetName = 'Install')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Install')]
    [ValidateScript({
        $parsed = $null
        [Net.IPAddress]::TryParse($_, [ref]$parsed) -and
            $parsed.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork -and
            $_ -eq $parsed.ToString() -and
            $parsed.GetAddressBytes()[0] -in (1..126 + 128..223)
    })]
    [string]$CoreAddress,

    [Parameter(Mandatory, ParameterSetName = 'Run')]
    [switch]$Run,

    [Parameter(Mandatory, ParameterSetName = 'Remove')]
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
$port = 47431
$taskName = 'UltraBar PC Standby'
$ruleName = 'UltraBarPCStandby'
$installDir = Join-Path $env:ProgramFiles 'UltraBar PC Standby'
$installedScript = Join-Path $installDir 'PC-Standby.ps1'
$keyFile = Join-Path $installDir 'pairing.key'

if ($Run) {
    $key = [IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'pairing.key'))
    if ($key.Length -ne 16) { throw 'The pairing key is invalid. Run setup again.' }
    $hmac = New-Object Security.Cryptography.HMACSHA256
    $hmac.Key = $key
    $udp = New-Object Net.Sockets.UdpClient($port)
    try {
        while ($true) {
            $sender = New-Object Net.IPEndPoint([Net.IPAddress]::Any, 0)
            try {
                $nonce = $udp.Receive([ref]$sender)
                if ($nonce.Length -ne 32) { continue }
                $reply = $hmac.ComputeHash($nonce)
                [void]$udp.Send($reply, $reply.Length, $sender)
            } catch [Net.Sockets.SocketException] {
                Start-Sleep -Milliseconds 250
            }
        }
    } finally {
        $udp.Dispose()
        $hmac.Dispose()
    }
    return
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Open Windows PowerShell as Administrator and run this command again.'
}

if ($Remove) {
    if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
        Stop-ScheduledTask -TaskName $taskName
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    }
    Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    foreach ($path in @($installedScript, $keyFile)) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
    }
    return
}

$route = Find-NetRoute -RemoteIPAddress $CoreAddress | Where-Object IPAddress | Select-Object -First 1
if (!$route) { throw 'Windows could not find a route to Core.' }
$profile = Get-NetConnectionProfile -InterfaceIndex $route.InterfaceIndex
if ($profile.NetworkCategory -ne 'Private') { throw 'Connect the PC and Core to a trusted Private network.' }

[void](New-Item -ItemType Directory -Path $installDir -Force)
$directory = Get-Item -LiteralPath $installDir
if ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw 'The installation directory must be a regular Program Files directory.'
}
# Pairing code and the startup script are readable only by SYSTEM and administrators.
$acl = New-Object Security.AccessControl.DirectorySecurity
$administrators = New-Object Security.Principal.SecurityIdentifier('S-1-5-32-544')
$acl.SetOwner($administrators)
$acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($administrators, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
    $access = New-Object Security.AccessControl.FileSystemAccessRule(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($access)
}
Set-Acl -LiteralPath $installDir -AclObject $acl
foreach ($path in @($installedScript, $keyFile)) {
    if ((Test-Path -LiteralPath $path) -and
            ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The installed files must be regular files.'
    }
}
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $taskName
}
if ($PSCommandPath -ne $installedScript) {
    Copy-Item -LiteralPath $PSCommandPath -Destination $installedScript -Force
}
if (!(Test-Path -LiteralPath $keyFile)) {
    $key = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($key) } finally { $rng.Dispose() }
    [IO.File]::WriteAllBytes($keyFile, $key)
}

$powerShell = Join-Path $PSHOME 'powershell.exe'
$existingRule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($existingRule) {
    $existingRule | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress $CoreAddress
} else {
    New-NetFirewallRule -Name $ruleName -DisplayName $taskName -Direction Inbound -Action Allow `
        -Program $powerShell -Protocol UDP -LocalPort $port -RemoteAddress $CoreAddress -Profile Private |
        Out-Null
}

$action = New-ScheduledTaskAction -Execute $powerShell `
    -Argument ('-NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File "' + $installedScript + '" -Run')
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings `
    -User 'SYSTEM' -Force | Out-Null
Start-ScheduledTask -TaskName $taskName

$pairingCode = [BitConverter]::ToString([IO.File]::ReadAllBytes($keyFile)).Replace('-', '').ToLowerInvariant()
Write-Host "Windows PC IPv4 address: $($route.IPAddress)"
Write-Host "Pairing code: $pairingCode"
