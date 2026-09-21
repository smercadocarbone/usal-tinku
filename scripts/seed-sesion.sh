#!/usr/bin/env bash
# Semilla de UNA sesión lista para probar la videollamada (M3 aula) en segundos:
# crea una franja de Jorge que cubre AHORA, una Reserva Ana→Jorge a +5min,
# la confirma en modo Bypass (ADR-M5-01, sin cobro real) y espera a que el job
# de T-5 (CrearSalaJob) deje la sala de LiveKit creada. Al final imprime la URL
# del aula y las credenciales de los 2 participantes.
#
# Requisitos:
#   - seed-usuarios.sh + seed-reservas.sh ya corridos (Ana 30112233, Jorge 30224455).
#   - LiveKit configurado en .env (LIVEKIT_URL/LIVEKIT_API_KEY/LIVEKIT_API_SECRET)
#     y el backend levantado con esos valores (make up / docker compose up -d).
#   - Para VER video real: 2 navegadores distintos (o incógnito), cámara/mic.
#
# Uso: scripts/seed-sesion.sh [base_url]   (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
PASSWORD="Password123!"
FRONT="http://localhost:3000"

ANA_DNI="30112233"
JORGE_DNI="30224455"

psql_tAc() { $COMPOSE exec -T db psql -U tinku_dev -d tinku -tAc "$1"; }
psql_id() { psql_tAc "SELECT id FROM identidad.usuarios WHERE dni='$1'" | tr -d ' '; }
exists1() { [ "$(psql_tAc "$1")" = "1" ]; }

login() {
    curl -s -X POST "$BASE/api/usuarios/login" -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$1\",\"password\":\"$PASSWORD\"}" |
        python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'
}

ANA_ID=$(psql_id "$ANA_DNI")
JORGE_ID=$(psql_id "$JORGE_DNI")
[ -n "$ANA_ID" ] && [ -n "$JORGE_ID" ] || {
    echo "ERROR: corré primero seed-usuarios.sh (Ana $ANA_DNI y tutor $JORGE_DNI)." >&2
    exit 1
}

# ── Tiempos en America/Argentina/Buenos_Aires ────────────────────────────────
# Reserva a +18min (FR-RES-013: mín. 15 de anticipación) → la sala se crea a
# T-5 ≈ +13min. La espera es corta y el no-show cae a +28min (margen de sobra).
read -r FECHA H_INI H_FIN H_RES RES_ISO LIMITE_NO_SHOW <<< "$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
Z = timezone(timedelta(hours=-3))
now = datetime.now(Z).replace(second=0, microsecond=0)
res = now + timedelta(minutes=18)
print(now.date().isoformat(), now.strftime("%H:%M"), (now + timedelta(minutes=90)).strftime("%H:%M"),
      res.strftime("%H:%M"), res.isoformat(), (res + timedelta(minutes=10)).isoformat(), sep="\t")
PY
)"
echo "Franja: hoy $FECHA $H_INI-$H_FIN | Reserva: $RES_ISO (no-show a las $LIMITE_NO_SHOW)"

# ── Franja de Jorge que cubre AHORA (skip si ya existe una así) ──────────────
if exists1 "SELECT 1 FROM reservas.franjas_disponibilidad
            WHERE tutor_id='$JORGE_ID' AND activa
              AND fecha_especifica='$FECHA'
              AND hora_inicio <= '$H_RES'::time AND hora_fin > '$H_RES'::time"; then
    echo "==> Franja de $JORGE_DNI cubriendo ahora: ya existe, skip"
else
    echo "==> Publicando franja de $JORGE_DNI: $H_INI-$H_FIN"
    token=$(login "$JORGE_DNI")
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/tutores/franjas" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"fechaEspecifica\":\"$FECHA\",\"horaInicio\":\"$H_INI\",\"horaFin\":\"$H_FIN\"}")
    echo "  → HTTP $code"
fi

# ── Reserva directa Ana → Jorge (si no queda una confirmada para hoy) ────────
if exists1 "SELECT 1 FROM reservas.reservas
            WHERE pagador_id='$ANA_ID' AND tutor_id='$JORGE_ID'
              AND estado='CONFIRMADA' AND horario::date = '$FECHA'"; then
    echo "==> Reserva confirmada de $ANA_DNI→$JORGE_DNI: ya existe, reuso"
else
    echo "==> Creando reserva $ANA_DNI → $JORGE_DNI ($RES_ISO)"
    token=$(login "$ANA_DNI")
    RES_ID=$(curl -s -X POST "$BASE/api/reservas" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"tutorId\":\"$JORGE_ID\",\"horario\":\"$RES_ISO\"}" |
        python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
    echo "  reserva $RES_ID"

    echo "==> Pagando en modo Bypass (POST /api/pagos/preferencia)"
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/pagos/preferencia" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"reservaId\":\"$RES_ID\"}")
    echo "  → HTTP $code"
fi

SESION_ID=$(psql_tAc "SELECT id FROM aula.sesiones_aprendizaje
                      ORDER BY id DESC LIMIT 1" | tr -d ' ')

echo "==> Esperando a que CrearSalaJob cree la sala en LiveKit (T-5 = $H_RES - 5min)"
for i in $(seq 1 130); do
    [ "$(psql_tAc "SELECT (livekit_room_id IS NOT NULL)::int FROM aula.sesiones_aprendizaje WHERE id='$SESION_ID'")" = "1" ] && break
    [ $((i % 6)) -eq 0 ] && echo "  ... $((i * 10))s"
    sleep 10
done
ROOM=$(psql_tAc "SELECT livekit_room_id FROM aula.sesiones_aprendizaje WHERE id='$SESION_ID'" | tr -d ' ')

[ -n "$ROOM" ] || {
    echo "ERROR: la sala no se creó en 2min. Revisá LIVEKIT_URL/API_KEY/API_SECRET en .env y los logs del backend." >&2
    exit 1
}

echo ""
echo "░░ Próxima sesión lista para probar la videollamada ░░"
echo "  Sala (room id):  $ROOM"
echo "  Aula (URL):      $FRONT/aula/$SESION_ID"
echo ""
echo "  1. Abrí $FRONT en DOS navegadores distintos (o normal + incógnito)."
echo "  2. Login 1: Ana  ($ANA_DNI / $PASSWORD)"
echo "  3. Login 2: Jorge ($JORGE_DNI / $PASSWORD)"
echo "  4. En ambos navegadores, pegá la URL del Aula y aceptá cámara/mic."
echo ""
echo "  Tip: la videollamada es 1:1; si no ves al otro, revisá que ambos"
echo "  usen navegadores/datos distintos (no pestañas del mismo navegador)."
echo "  El no-show se decide a las $LIMITE_NO_SHOW: entrá antes o finalizá a mano."