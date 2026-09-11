# =============================================================================
# Tinku — Makefile de desarrollo local
#
# Todo el stack está dockerizado (docker-compose.yml en la raiz):
#   Postgres 16 (pgvector)  -> db       (:5432)
#   matching-service Python -> matching (:8000)
#   backend Spring Boot     -> backend  (:8080)
#   frontend Next.js        -> frontend (:3000)
#
# Los targets `backend`/`matching`/`frontend` de host se conservan para
# correr las piezas sueltas con hot reload real (dev de un módulo), pero el
# flujo completo de un día de trabajo es `make up` (todo dockerizado).
#
# Convention: si existe `.env` en la raiz (copiado de `.env.example`), se
# cargan sus valores; si no, se usan los defaults de desarrollo de abajo
# (coinciden con docker-compose.yml y application.yml). Nunca commitear `.env`.
# =============================================================================

# JDK 21 es obligatorio (pom.xml, enforcer + Lombok). Sobrescribible vía
# `make JAVA_HOME=...` o variable de entorno.
JAVA_HOME ?= /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

BACKEND_DIR      := backend
MATCHING_DIR     := matching-service
FRONTEND_DIR     := frontend
LOG_DIR          := .logs

# Ports por defecto (matching 8000, backend 8080, frontend 3000).
MATCHING_PORT    ?= 8000
FRONTEND_PORT    ?= 3000

# Defaults de BD de desarrollo (docker-compose.yml + application.yml).
DB_USER        ?= tinku_dev
DB_PASSWORD    ?= changeme
TINKU_PG_HOST  ?= localhost
TINKU_PG_PORT  ?= 5432
TINKU_PG_DBNAME?= tinku

# Flags extra (ej. `make backend-test TEST_FLAGS="-Dtest=X"`).
TEST_FLAGS      ?=

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
.PHONY: help
help: ## Lista de comandos disponibles
	@echo "Tinku — desarrollo local (ver comentarios del header y backend/README.md)"; \
	echo ""; \
	grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  %-18s %s\n", $$1, $$2}'

# -----------------------------------------------------------------------------
# Stack completo (dockerizado)
# -----------------------------------------------------------------------------
.PHONY: up down logs ps build up-fast
up: ## Levanta TODO el stack dockerizado (build + up) y muestra los logs
	@docker compose up -d --build; \
	echo ""; \
	docker compose ps

down: ## Baja todo el stack (conserva el volumen de datos)
	@docker compose down

logs: ## Logs de todos los servicios
	@docker compose logs -f

ps: ## Estado del stack
	@docker compose ps

build: ## (Re)construye las imágenes del stack
	@docker compose build

up-fast: ## Levanta el stack sin reconstruir imágenes (ya built)
	@docker compose up -d; \
	echo ""; \
	docker compose ps

# -----------------------------------------------------------------------------
# Todo junto (flujo dev completo: stack dockerizado)
# -----------------------------------------------------------------------------
.PHONY: dev
dev: up ## Aliass de `make up` — todo el stack dockerizado

# -----------------------------------------------------------------------------
# Setup inicial (una sola vez)
# -----------------------------------------------------------------------------
.PHONY: setup
setup: ## Copia .env.example -> .env y prepara dependencias una sola vez (host, para tests)
	@if [ ! -f .env ]; then cp .env.example .env && echo "Creado .env (revisalo y completá los valores)."; fi
	@[ -d "$(JAVA_HOME)" ] || echo "Aviso: JAVA_HOME=$(JAVA_HOME) no existe (solo hace falta para targets de host)."
	@pip install $(PIP_FLAGS) -r $(MATCHING_DIR)/requirements.txt 2>/dev/null || true
	@echo "Setup listo."

# -----------------------------------------------------------------------------
# Base de datos (Postgres 16 / pgvector, docker-compose de la raiz)
# -----------------------------------------------------------------------------
.PHONY: db-up db-down db-logs db-reset
db-up: ## Levanta Postgres y espera a que esté healthy
	@docker compose up -d db --wait

db-down: ## Apaga Postgres (conserva el volumen de datos)
	@docker compose down

db-logs: ## Logs de Postgres
	@docker compose logs -f db

db-reset: ## BORRA el volumen de datos y recrea la base desde cero (Flyway la re-sembra)
	@echo "ATENCIÓN: esto borra todos los datos locales (volumen tinku_pgdata)."; \
	read -p "¿Continuar? [y/N] " r; [ "$$r" = "y" ] || { echo "Abortado"; exit 1; }; \
	docker compose down -v && docker compose up -d db --wait

# -----------------------------------------------------------------------------
# Backend (Spring Boot, monolito modular — com.tinku.*)
# -----------------------------------------------------------------------------
.PHONY: backend backend-test backend-build
backend: db-up ## Levanta el backend (perfil dev por defecto, puerto 8080)
	@if [ ! -d "$(JAVA_HOME)" ]; then \
		echo "JAVA_HOME=$(JAVA_HOME) no existe. Instalá TempleJDK/temurin-21 o pasá 'make JAVA_HOME=/ruta/al/jdk'."; exit 2; \
	fi
	@set -a; [ -f .env ] && . ./.env || true; set +a; \
	cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw spring-boot:run

backend-test: db-up ## Corre la suite de tests del backend (Testcontainers requiere Docker)
	@set -a; [ -f .env ] && . ./.env || true; set +a; \
	cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw test $(TEST_FLAGS)

backend-build: ## Compila el backend (sin tests)
	@set -a; [ -f .env ] && . ./.env || true; set +a; \
	cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" ./mvnw -q -DskipTests package

# -----------------------------------------------------------------------------
# Matching service (proceso Python separado — única excepción al monolito, M2)
# -----------------------------------------------------------------------------
.PHONY: matching matching-test
matching: db-up ## Levanta el servicio de matching (uvicorn, puerto 8000)
	@set -a; [ -f .env ] && . ./.env || true; set +a; \
	export TINKU_PG_HOST="$${TINKU_PG_HOST:-$(TINKU_PG_HOST)}" \
	       TINKU_PG_PORT="$${TINKU_PG_PORT:-$(TINKU_PG_PORT)}" \
	       TINKU_PG_DBNAME="$${TINKU_PG_DBNAME:-$(TINKU_PG_DBNAME)}" \
	       TINKU_PG_USER="$${TINKU_PG_USER:-$${DB_USER:-$(DB_USER)}}" \
	       TINKU_PG_PASSWORD="$${TINKU_PG_PASSWORD:-$${DB_PASSWORD:-$(DB_PASSWORD)}}"; \
	pip install $(PIP_FLAGS) -r $(MATCHING_DIR)/requirements.txt; \
	cd $(MATCHING_DIR) && uvicorn main:app --reload --port $(MATCHING_PORT)

matching-test: ## Tests del matching service (pytest, inyecta embedder/repo falsos)
	@pip install $(PIP_FLAGS) -r $(MATCHING_DIR)/requirements.txt; \
	cd $(MATCHING_DIR) && python -m pytest test_main.py -q

# -----------------------------------------------------------------------------
# Frontend (Next.js + React PWA, puerto 3000)
# -----------------------------------------------------------------------------
.PHONY: frontend
frontend: ## Levanta el frontend (Next.js dev server)
	@cd $(FRONTEND_DIR) && if [ ! -d node_modules ]; then npm install; fi; \
	npm run dev -- --port $(FRONTEND_PORT)

.PHONY: stop
stop: ## Baja todo el stack dockerizado (conserva el volumen de datos)
	@docker compose down

# -----------------------------------------------------------------------------
# Test de TODO el monorepo
# -----------------------------------------------------------------------------
.PHONY: test
test: backend-test matching-test ## Suite de tests de backend + matching