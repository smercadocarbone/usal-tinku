#!/usr/bin/env bash
# Smoke E2E contra el stack REAL (AUD-031): base + matching-service (modelo real) +
# backend en perfil dev, sin mocks. Recorre el camino crítico por la API:
#   registro → login → búsqueda semántica → reserva → pago en Modo Bypass → confirmada.
# Verifica los contratos entre los tres procesos, que ningún test unitario cubre.
#
# Requiere el stack de docker-compose.yml levantado con el mismo token en backend y
# matching (TINKU_MATCHING_TOKEN = MATCHING_SERVICE_TOKEN) y el seed de tutores de dev.
# Uso: scripts/smoke-e2e.sh [base_url]   (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
TUTOR_DNI="30111222"            # María Pérez, TutorSeedRunner (perfil dev)
TUTOR_PASSWORD="password123"
EST_DNI="9$(date +%s | tail -c 8)"
EST_PASSWORD="SmokeTest-2026"

falla() { echo "✗ $*" >&2; exit 1; }
paso() { echo "==> $*"; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
psql_tAc() { $COMPOSE exec -T db psql -U tinku_dev -d tinku -tAc "$1"; }
login() {
    curl -sf -X POST "$BASE/api/usuarios/login" -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$1\",\"password\":\"$2\"}" | json 'd["token"]'
}

paso "Esperando al backend"
for _ in $(seq 1 90); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/usuarios/me" || true)
    [ "$code" = "403" ] && break
    sleep 5
done
[ "$code" = "403" ] || falla "el backend no respondió (último código: $code)"

paso "Esperando al matching-service (carga el modelo al arrancar)"
for _ in $(seq 1 120); do
    $COMPOSE exec -T matching python -c "import urllib.request as u; u.urlopen('http://localhost:8000/health', timeout=5)" \
        > /dev/null 2>&1 && ok=1 && break
    sleep 5
done
[ "${ok:-}" = "1" ] || falla "el matching-service no respondió /health"

paso "Embeddings de los tutores del seed (recompute real del matching-service)"
$COMPOSE exec -T matching python -c "import os,urllib.request as u; r=u.Request('http://localhost:8000/recompute-embeddings', method='POST', headers={'X-Matching-Token': os.environ['TINKU_MATCHING_TOKEN']}); print(u.urlopen(r, timeout=900).read().decode())"

read -r FECHA HORARIO <<< "$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
ar = timezone(timedelta(hours=-3))
d = (datetime.now(ar) + timedelta(days=1)).replace(hour=18, minute=0, second=0, microsecond=0)
print(d.date().isoformat(), d.isoformat())
PY
)"

paso "Tutor: tarifa y franja de mañana 17–20 (máximo 180 min, FR-RES-024)"
TUTOR_TOKEN=$(login "$TUTOR_DNI" "$TUTOR_PASSWORD") || falla "login del tutor del seed"
TUTOR_ID=$(psql_tAc "SELECT id FROM identidad.usuarios WHERE dni='$TUTOR_DNI'")
curl -sf -X PUT "$BASE/api/pagos/tarifa" -H "Authorization: Bearer $TUTOR_TOKEN" \
    -H 'Content-Type: application/json' -d '{"precioHora":12000}' > /dev/null || falla "PUT tarifa"
curl -sf -X POST "$BASE/api/tutores/franjas" -H "Authorization: Bearer $TUTOR_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"fechaEspecifica\":\"$FECHA\",\"horaInicio\":\"17:00\",\"horaFin\":\"20:00\"}" > /dev/null \
    || falla "POST franja"

paso "Registro y login de un estudiante nuevo ($EST_DNI)"
DATOS="{\"dniDeclarado\":\"$EST_DNI\",\"nombreDeclarado\":\"Smoke\",\"apellidoDeclarado\":\"Test\",\"fechaNacimientoDeclarada\":\"2000-01-01\",\"email\":\"smoke$EST_DNI@tinku.test\",\"password\":\"$EST_PASSWORD\",\"capacidadEstudiante\":true,\"capacidadAdultoResponsable\":false}"
printf 'foto' > /tmp/smoke-dni.png
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/usuarios/registro" \
    -F "datos=$DATOS;type=application/json" -F "fotoDni=@/tmp/smoke-dni.png;type=image/png")
[ "$code" = "201" ] || falla "registro devolvió $code"
TOKEN=$(login "$EST_DNI" "$EST_PASSWORD") || falla "login del estudiante"

paso "Búsqueda semántica: 'función cuadrática' tiene que traer a la tutora de Matemática"
RESULTADOS=$(curl -sf -X POST "$BASE/api/busquedas" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"texto_busqueda":"no entiendo la función cuadrática"}') \
    || falla "POST /api/busquedas"
echo "$RESULTADOS" | json "'$TUTOR_ID' in [r['tutor_id'] for r in d]" | grep -q True \
    || falla "la búsqueda no trajo a la tutora: $RESULTADOS"

paso "Reserva de 60 min mañana 18:00"
RESERVA=$(curl -sf -X POST "$BASE/api/reservas" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"tutorId\":\"$TUTOR_ID\",\"horario\":\"$HORARIO\",\"duracionMinutos\":60}") || falla "POST /api/reservas"
RESERVA_ID=$(echo "$RESERVA" | json 'd["id"]')
[ "$(echo "$RESERVA" | json 'd["estado"]')" = "pendiente_pago" ] || falla "estado inicial: $RESERVA"

paso "Pago en Modo Bypass (solo existe fuera de prod, FASE2-07)"
psql_tAc "INSERT INTO pagos.pasarela_estado (id, habilitada, updated_at) VALUES (1, false, now())
          ON CONFLICT (id) DO UPDATE SET habilitada = false, updated_at = now()" > /dev/null
PREF=$(curl -sf -X POST "$BASE/api/pagos/preferencia" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{\"reservaId\":\"$RESERVA_ID\"}") || falla "POST preferencia"
[ "$(echo "$PREF" | json 'd["bypass"]')" = "True" ] || falla "la preferencia no salió en bypass: $PREF"

ESTADO=$(curl -sf "$BASE/api/reservas/$RESERVA_ID" -H "Authorization: Bearer $TOKEN" | json 'd["estado"]')
[ "$ESTADO" = "confirmada" ] || falla "la reserva quedó en '$ESTADO'"

echo "✓ Smoke E2E OK: registro → búsqueda → reserva → pago → confirmada"
