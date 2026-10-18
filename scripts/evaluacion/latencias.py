#!/usr/bin/env python3
"""T-TES-11b §1.b: latencias p50/p95 de los endpoints críticos con una carga chica y reproducible.

Solo biblioteca estándar. Corre contra un entorno con el seed (`dev` o `seed-demo`), NUNCA contra
producción con datos reales. Mide, con N usuarios concurrentes × M requests por endpoint (después de
un calentamiento que se descarta):

  - búsqueda       POST /api/busquedas                (textos del set etiquetado, rotando)
  - reserva        POST /api/reservas                 (horarios distintos de los próximos días)
  - pago_bypass    POST /api/pagos/preferencia        (con la pasarela apagada confirma sin MercadoPago)
  - token_aula     POST /api/sesiones/{id}/token      (solo con --sesion: hace falta una sala abierta)

Uso:
  python3 scripts/evaluacion/latencias.py --base http://localhost:8080 \
      --alumno 30112233 --password 'password123' [--admin-financiero DNI] \
      [--sesion UUID --sesion-dni DNI] [--concurrencia 5] [--repeticiones 20] [--desde-dias 2] [--salida DIR]

`--admin-financiero` apaga la pasarela (modo bypass) antes de medir el pago; sin él, se asume que
ya está apagada. El reporte queda en `latencias.csv` y `latencias.md` dentro de `--salida`.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import statistics
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
CONSULTAS = RAIZ / "matching-service/tests/eval_relevancia/consultas_etiquetadas.csv"
ZONA_AR = dt.timezone(dt.timedelta(hours=-3))
UMBRALES_P95_MS = {"busqueda": 1500, "reserva": 800, "pago_bypass": 800, "token_aula": 500}


def pedir(base: str, metodo: str, ruta: str, token: str | None = None, cuerpo: dict | None = None,
          timeout: float = 60.0) -> tuple[int, float, object]:
    datos = None if cuerpo is None else json.dumps(cuerpo).encode()
    req = urllib.request.Request(base.rstrip("/") + ruta, data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            crudo = r.read()
            estado = r.status
    except urllib.error.HTTPError as e:
        crudo = e.read()
        estado = e.code
    ms = (time.perf_counter() - inicio) * 1000
    try:
        cuerpo_resp = json.loads(crudo) if crudo else None
    except ValueError:
        cuerpo_resp = None
    return estado, ms, cuerpo_resp


def login(base: str, dni: str, password: str) -> str:
    estado, _, cuerpo = pedir(base, "POST", "/api/usuarios/login", cuerpo={"dni": dni, "password": password})
    if estado != 200:
        raise SystemExit(f"No se pudo iniciar sesión con {dni}: HTTP {estado} {cuerpo}")
    return cuerpo["token"]


def textos_busqueda() -> list[str]:
    with CONSULTAS.open(encoding="utf-8") as f:
        return [r["texto"] for r in csv.DictReader(f) if r["grupo"] == "relevante"]


def horarios(cantidad: int, desde_dias: int = 2) -> list[str]:
    """Horarios distintos (30 min) entre las 8 y las 22 hora argentina, desde dentro de `desde_dias`."""
    hoy = dt.datetime.now(ZONA_AR).replace(hour=0, minute=0, second=0, microsecond=0)
    salida = []
    dia = desde_dias
    while len(salida) < cantidad:
        for h in range(8, 22):
            for m in (0, 30):
                salida.append((hoy + dt.timedelta(days=dia, hours=h, minutes=m)).astimezone(dt.timezone.utc)
                              .strftime("%Y-%m-%dT%H:%M:%SZ"))
        dia += 1
    return salida[:cantidad]


class Medidor:
    def __init__(self) -> None:
        self.muestras: list[tuple[str, int, float]] = []
        self._lock = threading.Lock()

    def registrar(self, endpoint: str, estado: int, ms: float) -> None:
        with self._lock:
            self.muestras.append((endpoint, estado, ms))


def correr(nombre: str, tareas: list, concurrencia: int, medidor: Medidor | None) -> list:
    resultados = []

    def una(t):
        estado, ms, cuerpo = t()
        if medidor is not None:
            medidor.registrar(nombre, estado, ms)
        return estado, cuerpo

    with ThreadPoolExecutor(max_workers=concurrencia) as ex:
        resultados = list(ex.map(una, tareas))
    return resultados


def percentil(valores: list[float], p: float) -> float:
    if not valores:
        return float("nan")
    orden = sorted(valores)
    k = max(0, min(len(orden) - 1, round(p / 100 * len(orden) + 0.5) - 1))  # nearest-rank
    return orden[k]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--alumno", required=True, help="DNI de un Estudiante adulto del seed")
    ap.add_argument("--password", required=True)
    ap.add_argument("--admin-financiero", help="DNI de un Admin de Soporte Financiero (apaga la pasarela)")
    ap.add_argument("--sesion", help="id de una Sesión con la sala abierta (para medir el token)")
    ap.add_argument("--sesion-dni", help="DNI de un participante de esa Sesión")
    ap.add_argument("--concurrencia", type=int, default=5)
    ap.add_argument("--repeticiones", type=int, default=20, help="requests por usuario concurrente")
    ap.add_argument("--calentamiento", type=int, default=10)
    ap.add_argument("--desde-dias", type=int, default=2,
                    help="las reservas empiezan dentro de estos días (cambiarlo al repetir con el mismo alumno)")
    ap.add_argument("--salida", type=Path, default=Path("."))
    args = ap.parse_args()

    base = args.base
    total = args.concurrencia * args.repeticiones
    token = login(base, args.alumno, args.password)
    medidor = Medidor()

    if args.admin_financiero:
        t_admin = login(base, args.admin_financiero, args.password)
        estado, _, cuerpo = pedir(base, "PATCH", "/api/admin/financiero/pasarela", t_admin, {"habilitada": False})
        if estado != 200:
            raise SystemExit(f"No se pudo apagar la pasarela: HTTP {estado} {cuerpo}")

    # Búsqueda
    textos = textos_busqueda()
    busq = lambda i: (lambda: pedir(base, "POST", "/api/busquedas", token, {"texto_busqueda": textos[i % len(textos)]}))  # noqa: E731
    correr("busqueda", [busq(i) for i in range(args.calentamiento)], 1, None)
    correr("busqueda", [busq(i) for i in range(total)], args.concurrencia, medidor)

    # Tutores para reservar: los que devuelve una búsqueda amplia.
    _, _, lista = pedir(base, "POST", "/api/busquedas", token, {"filtro_materia": "Matemática"})
    tutores = [r["tutorId"] for r in (lista or [])]
    if not tutores:
        raise SystemExit("La búsqueda no devolvió Tutores: ¿corrió el seed y POST /recompute-embeddings?")

    # Reserva (cada una en un horario distinto; rota entre Tutores)
    hs = horarios(args.calentamiento + total, args.desde_dias)
    res = lambda i: (lambda: pedir(base, "POST", "/api/reservas", token,  # noqa: E731
                                   {"tutorId": tutores[i % len(tutores)], "horario": hs[i], "duracionMinutos": 30}))
    calent = correr("reserva", [res(i) for i in range(args.calentamiento)], 1, None)
    medidas = correr("reserva", [res(args.calentamiento + i) for i in range(total)], args.concurrencia, medidor)
    reservas = [c["id"] for e, c in calent + medidas if e in (200, 201) and isinstance(c, dict) and "id" in c]

    # Pago en bypass (una vez por reserva creada)
    pago = lambda rid: (lambda: pedir(base, "POST", "/api/pagos/preferencia", token, {"reservaId": rid}))  # noqa: E731
    n_cal = min(args.calentamiento, len(reservas) // 5)
    correr("pago_bypass", [pago(r) for r in reservas[:n_cal]], 1, None)
    correr("pago_bypass", [pago(r) for r in reservas[n_cal:]], args.concurrencia, medidor)

    # Token del aula
    if args.sesion and args.sesion_dni:
        t_ses = login(base, args.sesion_dni, args.password)
        tok = lambda: pedir(base, "POST", f"/api/sesiones/{args.sesion}/token?clasificadorListo=true", t_ses)  # noqa: E731
        correr("token_aula", [tok for _ in range(args.calentamiento)], 1, None)
        correr("token_aula", [tok for _ in range(total)], args.concurrencia, medidor)

    args.salida.mkdir(parents=True, exist_ok=True)
    with (args.salida / "latencias.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["endpoint", "http", "ms"])
        for e, s, ms in medidor.muestras:
            w.writerow([e, s, f"{ms:.1f}"])

    lineas = ["# Latencias de endpoints críticos (T-TES-11b §1.b)", "",
              f"- Fecha: {dt.datetime.now(ZONA_AR):%Y-%m-%d %H:%M} (hora argentina) · Base: `{base}`",
              f"- Carga: {args.concurrencia} concurrentes × {args.repeticiones} requests por endpoint "
              f"(calentamiento de {args.calentamiento}, descartado)", "",
              "| Endpoint | Muestras | Errores (no 2xx) | p50 (ms) | p95 (ms) | Máx (ms) | Umbral p95 | Cumple |",
              "|---|---|---|---|---|---|---|---|"]
    for e in ("busqueda", "reserva", "pago_bypass", "token_aula"):
        ok = [ms for x, s, ms in medidor.muestras if x == e and 200 <= s < 300]
        err = sum(1 for x, s, _ in medidor.muestras if x == e and not 200 <= s < 300)
        if not ok and not err:
            lineas.append(f"| {e} | 0 | — | — | — | — | {UMBRALES_P95_MS[e]} | no medido |")
            continue
        p95 = percentil(ok, 95)
        cumple = "sí" if ok and p95 <= UMBRALES_P95_MS[e] and err == 0 else "**no**"
        lineas.append(f"| {e} | {len(ok)} | {err} | {percentil(ok, 50):.0f} | {p95:.0f} | "
                      f"{max(ok) if ok else float('nan'):.0f} | {UMBRALES_P95_MS[e]} | {cumple} |")
    lineas.append("")
    (args.salida / "latencias.md").write_text("\n".join(lineas), encoding="utf-8")
    print("\n".join(lineas))


if __name__ == "__main__":
    main()
