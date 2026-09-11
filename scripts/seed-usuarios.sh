#!/usr/bin/env bash
# Crea usuarios de prueba contra la API local (stack dockerizado, puerto 8080).
# En perfil dev el OCR hace eco de lo declarado: la fotoDNI ficticia alcanza.
# Uso: scripts/seed-usuarios.sh [base_url]   (default http://localhost:8080)
set -euo pipefail

BASE="${1:-http://localhost:8080}"
PASSWORD="Password123!"
FOTO=$(mktemp)
dd if=/dev/zero of="$FOTO" bs=1 count=1 2>/dev/null
TUTOR_ID=""


echo "==> Registrando adultos"
for spec in \
    "30112233|Ana|Gomez|1985-03-12|estudiante|true|false" \
    "30112234|Carlos|Perez|1990-07-01|estudiante|false|true" \
    "30112235|Lucia|Rodriguez|1982-11-20|estudiante|true|false"; do
    IFS='|' read -r dni nombre apellido fnac rol cap_est cap_ar <<<"$spec"
    json="{\"dniDeclarado\":\"$dni\",\"nombreDeclarado\":\"$nombre\",\"apellidoDeclarado\":\"$apellido\",\"fechaNacimientoDeclarada\":\"$fnac\",\"password\":\"$PASSWORD\",\"capacidadEstudiante\":$cap_est,\"capacidadAdultoResponsable\":$cap_ar}"
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/usuarios/registro" \
        -F "datos=$json;type=application/json" -F "fotoDni=@$FOTO;type=image/png")
    echo "  adulto $dni ($rol): HTTP $code"
done

echo "==> Registrando tutores y completando onboarding (credencial)"
for spec in \
    "30224455|Jorge|Martinez|1988-05-06|TITULO" \
    "30224456|Maria|Fernandez|1985-09-19|MATRICULA"; do
    IFS='|' read -r dni nombre apellido fnac cred <<<"$spec"
    json="{\"dniDeclarado\":\"$dni\",\"nombreDeclarado\":\"$nombre\",\"apellidoDeclarado\":\"$apellido\",\"fechaNacimientoDeclarada\":\"$fnac\",\"password\":\"$PASSWORD\"}"
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/tutores/registro" \
        -F "datos=$json;type=application/json" -F "fotoDni=@$FOTO;type=image/png")
    echo "  tutor $dni: HTTP $code"

    token=$(curl -s -X POST "$BASE/api/usuarios/login" \
        -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$dni\",\"password\":\"$PASSWORD\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

    cjson="{\"tipoDocumento\":\"$cred\"}"
    ccode=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/tutores/credenciales" \
        -H "Authorization: Bearer $token" \
        -F "datos=$cjson;type=application/json" -F "archivo=@$FOTO;type=application/pdf")
    echo "  tutor $dni credencial: HTTP $ccode"
    TUTOR_ID="$dni"
done

echo "==> Logueando al adulto responsable para dar de alta a un menor"
AR_DNI="30112234"
ar_token=$(curl -s -X POST "$BASE/api/usuarios/login" \
    -H 'Content-Type: application/json' \
    -d "{\"dni\":\"$AR_DNI\",\"password\":\"$PASSWORD\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "  token adulto responsable: $ar_token"

mjson='{"dniDeclarado":"50112233","nombreDeclarado":"Sofia","apellidoDeclarado":"Perez","fechaNacimientoDeclarada":"2012-04-15","password":"'"$PASSWORD"'","consentimientoExplicito":true,"versionTextoConsentimiento":"v1.0"}'
mcode=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/usuarios/menores" \
    -H "Authorization: Bearer $ar_token" \
    -F "datos=$mjson;type=application/json" -F "fotoDni=@$FOTO;type=image/png")
echo "  menor 50112233: HTTP $mcode"

echo "==> Verificando login de todos"
for spec in "30112233|estudiante" "30112234|adulto responsable" "30112235|estudiante" "30224455|tutor" "30224456|tutor" "50112233|menor"; do
    IFS='|' read -r dni rol <<<"$spec"
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/usuarios/login" \
        -H 'Content-Type: application/json' \
        -d "{\"dni\":\"$dni\",\"password\":\"$PASSWORD\"}")
    echo "  login $dni ($rol): HTTP $code"
done

rm -f "$FOTO"
echo "Listo. Password para todos: $PASSWORD"