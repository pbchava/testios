#!/usr/bin/env bash
#
# wildfly-healthcheck-watchdog.sh
#
# Verifica el endpoint de health check de WildFly. Si no responde con
# HTTP 200 (tras varios reintentos), mata la JVM de WildFly con kill -9.
#
# Pensado para ejecutarse via cron o systemd timer.
#
# Uso:
#   ./wildfly-healthcheck-watchdog.sh
#
# Configuracion: ver bloque de variables mas abajo, o exportar variables
# de entorno con el mismo nombre antes de invocar el script.

set -u -o pipefail

# ----------------------------- Configuracion --------------------------------

# URL del health check
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/healthcheck/api/health}"

# Codigo HTTP que se considera "todo OK"
EXPECTED_HTTP_CODE="${EXPECTED_HTTP_CODE:-200}"

# Timeout (segundos) por cada intento de curl
CURL_TIMEOUT="${CURL_TIMEOUT:-5}"

# Numero de reintentos antes de considerar el servicio caido
MAX_RETRIES="${MAX_RETRIES:-3}"

# Segundos de espera entre reintentos
RETRY_DELAY="${RETRY_DELAY:-5}"

# Como identificar el proceso de WildFly a matar. Se usa, en este orden:
#   1) WILDFLY_PID_FILE  si existe y contiene un PID valido
#   2) WILDFLY_PROCESS_PATTERN buscado con pgrep
WILDFLY_PID_FILE="${WILDFLY_PID_FILE:-/var/run/wildfly/wildfly.pid}"
WILDFLY_PROCESS_PATTERN="${WILDFLY_PROCESS_PATTERN:-jboss-modules.jar}"

# Senal a usar para matar la JVM. SIGKILL (9) es forzoso; puedes probar
# primero SIGTERM (15) cambiando esto, aunque en un healthcheck fallido
# normalmente se prefiere forzar el kill.
KILL_SIGNAL="${KILL_SIGNAL:-9}"

# Log del watchdog
LOG_FILE="${LOG_FILE:-/var/log/wildfly-healthcheck-watchdog.log}"

# Comando opcional a ejecutar DESPUES de matar la JVM (por ejemplo, para
# que systemd la vuelva a levantar, o para notificar a Slack/monitoreo).
# Dejar vacio si no se requiere.
POST_KILL_COMMAND="${POST_KILL_COMMAND:-}"

# ------------------------------------------------------------------------

log() {
    local msg="$1"
    local ts
    ts="$(date '+%Y-%m-%d %H:%M:%S')"
    echo "[$ts] $msg" | tee -a "$LOG_FILE"
}

# Devuelve 0 si el health check respondio con el codigo esperado
check_health() {
    local http_code
    http_code="$(curl -s -o /dev/null -w '%{http_code}' \
        --max-time "$CURL_TIMEOUT" \
        "$HEALTH_URL" 2>/dev/null)"

    if [[ "$http_code" == "$EXPECTED_HTTP_CODE" ]]; then
        return 0
    else
        log "Health check fallo. Codigo HTTP recibido: '${http_code:-sin respuesta}' (esperado: $EXPECTED_HTTP_CODE)"
        return 1
    fi
}

# Encuentra el PID de WildFly
find_wildfly_pid() {
    local pid=""

    if [[ -f "$WILDFLY_PID_FILE" ]]; then
        pid="$(cat "$WILDFLY_PID_FILE" 2>/dev/null)"
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        fi
    fi

    # Fallback: buscar el proceso por patron (jboss-modules.jar es el jar
    # de arranque de WildFly/JBoss, asi que suele ser un patron confiable)
    pid="$(pgrep -f "$WILDFLY_PROCESS_PATTERN" | head -n 1)"
    if [[ -n "$pid" ]]; then
        echo "$pid"
        return 0
    fi

    return 1
}

kill_wildfly() {
    local pid
    if ! pid="$(find_wildfly_pid)"; then
        log "ERROR: No se encontro el proceso de WildFly (ni por PID file '$WILDFLY_PID_FILE' ni por patron '$WILDFLY_PROCESS_PATTERN'). No se puede matar la JVM."
        return 1
    fi

    log "Matando JVM de WildFly. PID=$pid, señal=$KILL_SIGNAL"
    if kill -"$KILL_SIGNAL" "$pid" 2>/dev/null; then
        log "Señal enviada correctamente al PID $pid."
    else
        log "ERROR: No se pudo enviar la señal al PID $pid (¿permisos insuficientes? prueba con sudo)."
        return 1
    fi

    if [[ -n "$POST_KILL_COMMAND" ]]; then
        log "Ejecutando POST_KILL_COMMAND: $POST_KILL_COMMAND"
        eval "$POST_KILL_COMMAND" >>"$LOG_FILE" 2>&1
    fi

    return 0
}

main() {
    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || true

    local attempt=1
    while (( attempt <= MAX_RETRIES )); do
        if check_health; then
            log "Health check OK ($HEALTH_URL) en el intento $attempt."
            exit 0
        fi
        log "Intento $attempt/$MAX_RETRIES fallido."
        if (( attempt < MAX_RETRIES )); then
            sleep "$RETRY_DELAY"
        fi
        (( attempt++ ))
    done

    log "Health check fallo $MAX_RETRIES veces consecutivas. Se procede a matar la JVM de WildFly."
    kill_wildfly
    exit 1
}

main "$@"
