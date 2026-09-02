# tinku-matching-service

Único proceso separado del monolito (Constitución, Artículo VIII). Ver
`Plan_M2_Motor_Matching.md` antes de tocar este servicio — en particular
**ADR-M2-01**, todavía sin resolver: dónde vive el índice de embeddings
(en memoria de este proceso vs. `pgvector` en PostgreSQL).

## Regla de oro de este servicio

**Nunca debe conocer reglas de negocio de otros módulos** (autorización de
Tutores, suspensiones de M9, reputación de M7). El backend Java resuelve
todo eso antes de llamar acá — este servicio solo recibe un texto de
búsqueda y una lista ya acotada de `tutor_ids` candidatos, y devuelve un
ranking de similitud semántica. Si en algún momento este servicio necesita
saber "¿está este tutor suspendido?", es una señal de que algo se diseñó
mal — esa pregunta se responde en el backend Java, antes de la llamada.

## Cómo correr localmente

```bash
pip install -r requirements.txt --break-system-packages
uvicorn main:app --reload --port 8000
```

## Verificar que está vivo

```bash
curl http://localhost:8000/health
```
