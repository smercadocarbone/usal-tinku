# ADR-M1-07 — Un Tutor puede tomar clases y tener menores a cargo, nunca con él mismo

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-25). Implementado en R1 de
`docs/spikes/DISENO-soluciones-revision-por-rol.md` (FR-ID-032, FR-RES-025, migración V37).
Enmienda Spec M1 §1 y §6 ("el Tutor es un camino de registro exclusivo" / "combinación descartada").

## Contexto
- El DNI es único y es el usuario de login: una persona tiene **una sola cuenta**.
- Un Tutor que además es madre o padre no podía dar de alta a sus hijos ni tomar clases.
- La revisión por rol encontró que el backend nunca aplicó la exclusividad del Spec: un Tutor
  podía activar capacidades, dar de alta menores y reservarse clases a sí mismo.
- No hay datos de producción que migrar.

## Decisión
1. Un Tutor **puede** activar las capacidades de Estudiante y/o Adulto Responsable desde "Mi cuenta".
   Para él son opcionales (FR-ID-001 exige al menos una solo al ADULTO).
2. **Nunca con él mismo:**
   - no puede ser pagador ni beneficiario de una Reserva suya (servicio → 422; CHECK
     `ck_reservas_tutor_no_es_parte` en la base);
   - no puede autorizarse como Tutor de un menor a su cargo (403);
   - no puede darle clase a un menor del que es Adulto Responsable, aunque exista una
     autorización previa (422).
3. **Conflicto de interés en el panel:** si además es Admin, nunca resuelve un caso en el que es parte.

## Alternativas descartadas
- **Mantener la exclusividad** (lo que decía el Spec): obliga a un Tutor con hijos a elegir.
  Con DNI único, no puede tener una segunda cuenta.
- **Segunda cuenta con otro identificador:** rompe la unicidad del DNI (FR-ID-018) y abre la puerta
  a evadir sanciones.

## Consecuencias
- La regla "Tutor ≠ Adulto Responsable del beneficiario" cruza tablas: la garantiza solo el servicio
  (`ReservaService.exigirTutorReservable`), no un CHECK.
- La navegación del Tutor suma "Buscar" y "Mis chicos" cuando tiene esas capacidades.
