<#
.SYNOPSIS
    wildfly-healthcheck-watchdog.ps1

    Verifica el endpoint de health check de WildFly. Si no responde con
    HTTP 200 (tras varios reintentos), mata la JVM de WildFly.

.DESCRIPTION
    Equivalente en PowerShell del script wildfly-healthcheck-watchdog.sh
    para Linux. Pensado para ejecutarse via el Programador de tareas
    (Task Scheduler) de Windows.

.EXAMPLE
    .\wildfly-healthcheck-watchdog.ps1

.EXAMPLE
    .\wildfly-healthcheck-watchdog.ps1 -HealthUrl "http://localhost:8080/healthcheck/api/health" -MaxRetries 5

.EXAMPLE
    # Via variables de entorno, igual que en la version de Linux
    $env:HEALTH_URL = "http://localhost:8080/healthcheck/api/health"
    $env:MAX_RETRIES = "5"
    .\wildfly-healthcheck-watchdog.ps1
#>

[CmdletBinding()]
param(
    # URL del health check
    [string]$HealthUrl = $(if ($env:HEALTH_URL) { $env:HEALTH_URL } else { "http://localhost:8080/healthcheck/api/health" }),

    # Codigo HTTP que se considera "todo OK"
    [int]$ExpectedHttpCode = $(if ($env:EXPECTED_HTTP_CODE) { [int]$env:EXPECTED_HTTP_CODE } else { 200 }),

    # Timeout (segundos) por cada intento
    [int]$TimeoutSeconds = $(if ($env:CURL_TIMEOUT) { [int]$env:CURL_TIMEOUT } else { 5 }),

    # Numero de reintentos antes de considerar el servicio caido
    [int]$MaxRetries = $(if ($env:MAX_RETRIES) { [int]$env:MAX_RETRIES } else { 3 }),

    # Segundos de espera entre reintentos
    [int]$RetryDelaySeconds = $(if ($env:RETRY_DELAY) { [int]$env:RETRY_DELAY } else { 5 }),

    # Archivo con el PID de WildFly (opcional). Si no existe o no es
    # valido, se busca el proceso por -ProcessNameFilter.
    [string]$WildFlyPidFile = $(if ($env:WILDFLY_PID_FILE) { $env:WILDFLY_PID_FILE } else { "C:\wildfly\wildfly.pid" }),

    # Nombre del servicio de Windows de WildFly, si esta instalado como
    # servicio (ej. con el WildFly Windows Service wrapper). Si se
    # especifica y existe, se usa Stop-Service en lugar de matar el
    # proceso java.exe directamente.
    [string]$WildFlyServiceName = $(if ($env:WILDFLY_SERVICE_NAME) { $env:WILDFLY_SERVICE_NAME } else { "" }),

    # Filtro para localizar el proceso java.exe de WildFly cuando no se
    # usa PID file ni servicio. Se busca en la linea de comandos del
    # proceso (requiere WMI/CIM).
    [string]$ProcessNameFilter = $(if ($env:WILDFLY_PROCESS_PATTERN) { $env:WILDFLY_PROCESS_PATTERN } else { "jboss-modules.jar" }),

    # Archivo de log
    [string]$LogFile = $(if ($env:LOG_FILE) { $env:LOG_FILE } else { "C:\logs\wildfly-healthcheck-watchdog.log" }),

    # Comando opcional (scriptblock como string) a ejecutar DESPUES de
    # matar la JVM, ej. para reiniciar el servicio o notificar.
    [string]$PostKillCommand = $(if ($env:POST_KILL_COMMAND) { $env:POST_KILL_COMMAND } else { "" })
)

function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] $Message"
    Write-Host $line
    try {
        $logDir = Split-Path -Path $LogFile -Parent
        if ($logDir -and -not (Test-Path $logDir)) {
            New-Item -ItemType Directory -Path $logDir -Force | Out-Null
        }
        Add-Content -Path $LogFile -Value $line -ErrorAction Stop
    } catch {
        Write-Warning "No se pudo escribir en el log '$LogFile': $_"
    }
}

function Test-Health {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec $TimeoutSeconds `
            -UseBasicParsing -Method Get -ErrorAction Stop

        if ([int]$response.StatusCode -eq $ExpectedHttpCode) {
            return $true
        } else {
            Write-Log "Health check fallo. Codigo HTTP recibido: $($response.StatusCode) (esperado: $ExpectedHttpCode)"
            return $false
        }
    } catch [System.Net.WebException] {
        # Cuando el servidor responde con un codigo de error (4xx/5xx),
        # Invoke-WebRequest lanza excepcion pero aun trae el StatusCode.
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            Write-Log "Health check fallo. Codigo HTTP recibido: $code (esperado: $ExpectedHttpCode)"
        } else {
            Write-Log "Health check fallo. Sin respuesta del servidor (posible timeout o connection refused): $($_.Exception.Message)"
        }
        return $false
    } catch {
        Write-Log "Health check fallo. Error: $($_.Exception.Message)"
        return $false
    }
}

function Get-WildFlyProcess {
    # 1) Intentar por PID file
    if (Test-Path $WildFlyPidFile) {
        try {
            $storedPid = Get-Content $WildFlyPidFile -ErrorAction Stop | Select-Object -First 1
            $proc = Get-Process -Id ([int]$storedPid) -ErrorAction Stop
            return $proc
        } catch {
            Write-Log "PID file '$WildFlyPidFile' invalido o proceso ya no existe. Se intenta busqueda alterna."
        }
    }

    # 2) Buscar java.exe cuya linea de comandos contenga el filtro (requiere CIM/WMI)
    try {
        $candidates = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop |
            Where-Object { $_.CommandLine -and $_.CommandLine -like "*$ProcessNameFilter*" }

        if ($candidates) {
            $first = $candidates | Select-Object -First 1
            return Get-Process -Id $first.ProcessId -ErrorAction Stop
        }
    } catch {
        Write-Log "No se pudo consultar procesos via CIM/WMI: $($_.Exception.Message)"
    }

    return $null
}

function Stop-WildFly {
    # Preferir el servicio de Windows si se configuro uno
    if ($WildFlyServiceName) {
        $svc = Get-Service -Name $WildFlyServiceName -ErrorAction SilentlyContinue
        if ($svc) {
            Write-Log "Deteniendo servicio de Windows '$WildFlyServiceName' (equivalente a matar la JVM)."
            try {
                Stop-Service -Name $WildFlyServiceName -Force -ErrorAction Stop
                Write-Log "Servicio '$WildFlyServiceName' detenido correctamente."
                Invoke-PostKillCommand
                return $true
            } catch {
                Write-Log "ERROR: No se pudo detener el servicio '$WildFlyServiceName': $($_.Exception.Message)"
                return $false
            }
        } else {
            Write-Log "ADVERTENCIA: WildFlyServiceName='$WildFlyServiceName' fue especificado pero no existe ese servicio. Se intenta por proceso."
        }
    }

    $proc = Get-WildFlyProcess
    if (-not $proc) {
        Write-Log "ERROR: No se encontro el proceso de WildFly (ni por PID file '$WildFlyPidFile' ni por filtro '$ProcessNameFilter'). No se puede matar la JVM."
        return $false
    }

    Write-Log "Matando JVM de WildFly. PID=$($proc.Id), Nombre=$($proc.ProcessName)"
    try {
        Stop-Process -Id $proc.Id -Force -ErrorAction Stop
        Write-Log "Proceso $($proc.Id) terminado correctamente."
        Invoke-PostKillCommand
        return $true
    } catch {
        Write-Log "ERROR: No se pudo terminar el proceso $($proc.Id): $($_.Exception.Message) (¿ejecutando como Administrador?)"
        return $false
    }
}

function Invoke-PostKillCommand {
    if ($PostKillCommand) {
        Write-Log "Ejecutando PostKillCommand: $PostKillCommand"
        try {
            $output = Invoke-Expression $PostKillCommand 2>&1
            $output | ForEach-Object { Write-Log "  $_" }
        } catch {
            Write-Log "ERROR ejecutando PostKillCommand: $($_.Exception.Message)"
        }
    }
}

# ------------------------------- Main ---------------------------------

$attempt = 1
$healthy = $false

while ($attempt -le $MaxRetries) {
    if (Test-Health) {
        Write-Log "Health check OK ($HealthUrl) en el intento $attempt."
        $healthy = $true
        break
    }

    Write-Log "Intento $attempt/$MaxRetries fallido."
    if ($attempt -lt $MaxRetries) {
        Start-Sleep -Seconds $RetryDelaySeconds
    }
    $attempt++
}

if ($healthy) {
    exit 0
}

Write-Log "Health check fallo $MaxRetries veces consecutivas. Se procede a matar la JVM de WildFly."
$killed = Stop-WildFly

if ($killed) {
    exit 1
} else {
    exit 2
}
