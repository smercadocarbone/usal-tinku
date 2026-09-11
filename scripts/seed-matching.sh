#!/usr/bin/env bash
# M2-F: deja a los tutores del seed listos para /api/busquedas — aprueba la
# credencial, activa matching, les carga tema_ids (PUT /api/tutores/me/temas)
# y repuebla embeddings desde el servicio Python (/recompute-embeddings, 2c).
# Idempotente. Requiere: app corriendo (perfil dev) + contenedor `matching`.
# Uso: scripts/seed-matching.sh [base_url]   (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
MOTOR="${MOTOR:-http://localhost:8000}"
PASSWORD="Password123!"
COMPOSE="${COMPOSE:-docker compose}"

echo "==> Aprobando credencial y activando matching (SQL sobre la BD de dev)"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -v ON_ERROR_STOP=1 <<'SQL'
UPDATE identidad.credenciales_academicas
   SET estado = 'APROBADO', revisado_at = now()
 WHERE estado = 'PENDIENTE'
   AND tutor_id IN (SELECT id FROM identidad.usuarios WHERE dni IN ('30224455','30224456'));

UPDATE identidad.usuarios SET activo_para_matching = true
 WHERE dni IN ('30224455','30224456');
SQL

# (dni, Filtro de WHERE sobre matching.trayectos → QUÉ temas le pertenecen)
# Jorge: materias del secundario. Maria: materias de su carrera universitaria.
for spec in \
    "30224455|'secundario' AND tr.materia IN ('Matemática','Física','Química')" \
    "30224456|'universitario' AND tr.anio_o_carrera='Ingeniería en Sistemas de Información'"; do
    IFS='|' read -r dni where <<<"$spec"

    token=$(curl -s -X POST "$BASE/api/usuarios/login" \
        -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$dni\",\"password\":\"$PASSWORD\"}" |
        python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

    # Los ids del catálogo vigente se leen de la BD (el GET /api/catalogos es
    # solo lectura/consulta; acá se necesita la lista plana, más simple por SQL).
    ids=$($COMPOSE exec -T db psql -U tinku_dev -d tinku -tAc \
        "SELECT t.id::text
           FROM matching.temas t
           JOIN matching.trayectos tr ON tr.id = t.trayecto_id
          WHERE tr.nivel = $where
          ORDER BY tr.nivel, tr.anio_o_carrera, tr.materia, t.orden")
    json_ids=$(python3 -c 'import sys,json;print(json.dumps([l.strip() for l in sys.stdin if l.strip()]))' <<<"$ids")
    n=$(python3 -c 'import sys,json;print(len(json.load(sys.stdin)))' <<<"$json_ids")

    code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/tutores/me/temas" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d "{\"tema_ids\":$json_ids}")
    echo "  tutor $dni ($where): $n temas → HTTP $code"
done

echo "==> Recomputed de embeddings (contrato 2c, contenedor matching)"
recompute=$(curl -s -w '\n%{http_code}' -X POST "$MOTOR/recompute-embeddings")
body=${recompute%$'\n'*}; http=${recompute##*$'\n'}
if [ "$http" != "200" ]; then
    echo "  ERROR: recompute devolvió HTTP $http — $body (¿el contenedor matching está levantado y el modelo descargado?)"
    exit 1
fi
echo "  $body"

echo "==> Estado final"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT u.dni,
          u.activo_para_matching,
          CARDINALITY(p.tema_ids) AS temas,
          p.embedding IS NOT NULL AS tiene_embedding
     FROM identidad.usuarios u
     LEFT JOIN matching.perfiles_tutor_matching p ON p.tutor_id = u.id
    WHERE u.tipo = 'TUTOR';"