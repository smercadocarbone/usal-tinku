#!/usr/bin/env bash
# Semilla de reservas y datos de prueba para los flujos de M4 (reservas) y M5
# (pagos): tarifas de tutores, franjas de disponibilidad, autorizaciones del menor,
# una reserva directa (estudiante adulto → tutor) y una solicitud del menor aprobada
# por su Adulto Responsable — ambas quedan en estado pendiente_pago.
# Requiere: seed-usuarios.sh + seed-matching.sh ya corridos, app corriendo en dev.
# Idempotente (no duplica franjas, autorizaciones ni reservas si se re-ejecuta).
# Uso: scripts/seed-reservas.sh [base_url]   (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
PASSWORD="Password123!"
PRECIO=15000

JORGE_DNI="30224455"
MARIA_DNI="30224456"
ANA_DNI="30112233"
CARLOS_DNI="30112234"
SOFIA_DNI="50112233"

psql_tAc() { $COMPOSE exec -T db psql -U tinku_dev -d tinku -tAc "$1"; }
psql_id() { psql_tAc "SELECT id FROM identidad.usuarios WHERE dni='$1'"; }
exists1() { [ "$(psql_tAc "$1")" = "1" ]; }

login() {
    curl -s -X POST "$BASE/api/usuarios/login" -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$1\",\"password\":\"$PASSWORD\"}" |
        python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'
}

# Resolve UUIDs
JORGE_ID=$(psql_id "$JORGE_DNI")
MARIA_ID=$(psql_id "$MARIA_DNI")
ANA_ID=$(psql_id "$ANA_DNI")
CARLOS_ID=$(psql_id "$CARLOS_DNI")
SOFIA_ID=$(psql_id "$SOFIA_DNI")

# ── Tiempos ───────────────────────────────────────────────────────────────────
# Franja: hoy en America/Argentina/Buenos_Aires, ahora+30min → +150min
# Reserva directa (Ana): 20min después del inicio de la franja
# Solicitud del menor (Sofía): 70min después del inicio de la franja
read -r H_INI H_FIN RES_ISO SOL_ISO FECHA <<< "$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
Z = timezone(timedelta(hours=-3))
now = datetime.now(Z).replace(second=0, microsecond=0)
ini = now + timedelta(minutes=30)
fin = ini + timedelta(minutes=120)
res = ini + timedelta(minutes=20)
sol = ini + timedelta(minutes=70)
print(ini.strftime("%H:%M"), fin.strftime("%H:%M"), res.isoformat(), sol.isoformat(), ini.date().isoformat(), sep="\t")
PY
)"

echo "Franja: $H_INI–$H_FIN | Reserva directa: $RES_ISO | Solicitud menor: $SOL_ISO"

# ── Tarifas (idempotente: PUT actualiza) ──────────────────────────────────────
echo "==> Tarifas de tutores (PUT /api/pagos/tarifa)"
for dni in "$JORGE_DNI" "$MARIA_DNI"; do
    token=$(login "$dni")
    code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/pagos/tarifa" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"precioSesion\":$PRECIO}")
    echo "  tutor $dni: $PRECIO → HTTP $code"
done

# ── Franjas de disponibilidad (fecha específica de hoy, skip si ya existe) ────
echo "==> Franjas de disponibilidad (hoy $FECHA $H_INI–$H_FIN)"
FRANJA_BODY="{\"fechaEspecifica\":\"$FECHA\",\"horaInicio\":\"$H_INI\",\"horaFin\":\"$H_FIN\"}"
for pair in "$JORGE_DNI:$JORGE_ID" "$MARIA_DNI:$MARIA_ID"; do
    dni="${pair%%:*}"; tid="${pair#*:}"
    if exists1 "SELECT 1 FROM reservas.franjas_disponibilidad WHERE tutor_id='$tid' AND fecha_especifica='$FECHA' AND hora_inicio='$H_INI' AND hora_fin='$H_FIN'"; then
        echo "  tutor $dni: ya existe, skip"; continue
    fi
    token=$(login "$dni")
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/tutores/franjas" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "$FRANJA_BODY")
    echo "  tutor $dni → HTTP $code"
done

# ── Autorizaciones: Carlos autoriza a Jorge y Maria para Sofía ────────────────
echo "==> Autorizaciones de tutores para la menor (POST /api/autorizaciones)"
TOKEN_CARLOS=$(login "$CARLOS_DNI")
for TUTOR_ID in "$JORGE_ID" "$MARIA_ID"; do
    if exists1 "SELECT 1 FROM identidad.autorizaciones_tutor WHERE adulto_responsable_id='$CARLOS_ID' AND menor_id='$SOFIA_ID' AND tutor_id='$TUTOR_ID'"; then
        echo "  tutor $TUTOR_ID ya autorizado, skip"; continue
    fi
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/autorizaciones" \
        -H "Authorization: Bearer $TOKEN_CARLOS" -H 'Content-Type: application/json' \
        -d "{\"menorId\":\"$SOFIA_ID\",\"tutorId\":\"$TUTOR_ID\"}")
    echo "  autorizar tutor $TUTOR_ID → HTTP $code"
done

# ── Reserva directa: Ana (estudiante adulto) reserva a Jorge ─────────────────
echo "==> Reserva directa: Ana → Jorge ($RES_ISO)"
if exists1 "SELECT 1 FROM reservas.reservas WHERE pagador_id='$ANA_ID' AND tutor_id='$JORGE_ID' AND horario='$RES_ISO'::timestamptz"; then
    echo "  ya existe, skip"
else
    token=$(login "$ANA_DNI")
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/reservas" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"tutorId\":\"$JORGE_ID\",\"horario\":\"$RES_ISO\"}")
    echo "  Ana → Jorge → HTTP $code"
fi

# ── Solicitud del menor + aprobación del AR ───────────────────────────────────
echo "==> Solicitud de Sofía → Jorge ($SOL_ISO) + aprobación de Carlos"
if exists1 "SELECT 1 FROM reservas.reservas WHERE pagador_id='$CARLOS_ID' AND tutor_id='$JORGE_ID' AND horario='$SOL_ISO'::timestamptz"; then
    echo "  reserva ya existe, skip"
else
    token_sofia=$(login "$SOFIA_DNI")
    sol_resp=$(curl -s -X POST "$BASE/api/solicitudes" \
        -H "Authorization: Bearer $token_sofia" -H 'Content-Type: application/json' \
        -d "{\"tutorId\":\"$JORGE_ID\",\"horarioPropuesto\":\"$SOL_ISO\"}")
    SOL_ID=$(echo "$sol_resp" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])' 2>/dev/null || true)

    if [ -z "$SOL_ID" ]; then
        echo "  solicitud falló: $sol_resp"
    else
        echo "  solicitud creada: $SOL_ID"
        code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/solicitudes/$SOL_ID/aprobar" \
            -H "Authorization: Bearer $TOKEN_CARLOS")
        echo "  aprobación de Carlos → HTTP $code"
    fi
fi

# ── Estado final ──────────────────────────────────────────────────────────────
echo ""
echo "==> Tarifas"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT u.dni, t.precio_sesion FROM pagos.tarifas_tutor t JOIN identidad.usuarios u ON u.id=t.tutor_id;"

echo "==> Franjas activas"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT u.dni, f.fecha_especifica, f.hora_inicio, f.hora_fin
     FROM reservas.franjas_disponibilidad f
     JOIN identidad.usuarios u ON u.id=f.tutor_id
    WHERE f.activa;"

echo "==> Autorizaciones de la menor"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT ar.dni AS adulto, tutor.dni AS tutor, a.no_confiable
     FROM identidad.autorizaciones_tutor a
     JOIN identidad.usuarios ar ON ar.id=a.adulto_responsable_id
     JOIN identidad.usuarios tutor ON tutor.id=a.tutor_id
     JOIN identidad.usuarios menor ON menor.id=a.menor_id
    WHERE menor.dni='$SOFIA_DNI';"

echo "==> Reservas (pendientes de pago)"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT r.id,
          pagador.dni AS pagador,
          tutor.dni AS tutor,
          r.horario,
          r.precio,
          r.estado
     FROM reservas.reservas r
     JOIN identidad.usuarios pagador ON pagador.id=r.pagador_id
     JOIN identidad.usuarios tutor ON tutor.id=r.tutor_id
    WHERE r.estado = 'pendiente_pago';"