$ErrorActionPreference = 'Stop'
foreach ($port in 8762,9090) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect('localhost',$port)
        throw "Port $port is already in use. Stop the existing system before starting another copy."
    } catch [System.Net.Sockets.SocketException] {
        # A refused connection means this fixed port is available.
    } finally { $client.Dispose() }
}
New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot 'verification') | Out-Null
$modules = 'eureka-server','user-service','movie-service','showtime-service','booking-service','api-gateway'
$processes = @()
foreach ($module in $modules) {
    $process = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + (Join-Path $PSScriptRoot 'service.ps1') + '"'),'-Service',$module) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot "verification\$module-start.log") -RedirectStandardError (Join-Path $PSScriptRoot "verification\$module-start-error.log") -PassThru
    $processes += [pscustomobject]@{Service=$module;LauncherPid=$process.Id}
    if ($module -eq 'eureka-server') {
        $ready = $false
        for ($i=0; $i -lt 60; $i++) {
            try { $response = Invoke-WebRequest 'http://localhost:8762/' -TimeoutSec 2; if ($response.StatusCode -eq 200) { $ready=$true; break } } catch {}
            Start-Sleep -Seconds 1
        }
        if (!$ready) { throw 'Eureka did not become ready; inspect verification/eureka-server-start.log.' }
    }
}
$processes | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'verification\processes.json')
$processes | Format-Table
