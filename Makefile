# =============================================================================
# Tinku — Makefile de desarrollo local
#
# Levanta las 4 piezas del monorepo contra la infra local:
#   Postgres 16 (pgvector)  -> docker-compose.yml (raiz)
#   backend Spring Boot     -> backend/ (JDK 21, perfil dev por defecto)
#   matching-service Python -> matching-service/ (uvicorn, puerto 8000)
#   frontend Next.js        -> frontend/ (puerto 3000)
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
# Setup inicial (una sola vez)
# -----------------------------------------------------------------------------
.PHONY: setup
setup: ## Copia .env.example -> .env y prepara dependencias una sola vez
	@if [ ! -f .env ]; then cp .env.example .env && echo "Creado .env (revisalo y completá los valores)."; fi
	@docker compose up -d db --wait 2>/dev/null || docker compose up -d db
	@cd $(BACKEND_DIR) && ./mvnw -q -DskipTests compile
	@pip install $(PIP_FLAGS) -r $(MATCHING_DIR)/requirements.txt
	@cd $(FRONTEND_DIR) && if [ ! -d node_modules ]; then npm install; fi
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

# -----------------------------------------------------------------------------
# Todo junto (flujo dev completo)
# -----------------------------------------------------------------------------
.PHONY: dev
dev: db-up setup ## Levanta db + matching + frontend (background) y el backend (foreground)
	@mkdir -p $(LOG_DIR); \
	nohup sh -c "$(MAKE) -s matching" > $(LOG_DIR)/matching.log 2>&1 & echo $$! > $(LOG_DIR)/matching.pid; \
	nohup sh -c "$(MAKE) -s frontend" > $(LOG_DIR)/frontend.log 2>&1 & echo $$! > $(LOG_DIR)/frontend.pid; \
	echo "matching  up  -> tail -f $(LOG_DIR)/matching.log"; \
	echo "frontend  up  -> tail -f $(LOG_DIR)/frontend.log"; \
	echo "backend   corre acá (Ctrl+C lo detiene). Para detener todo: make stop"
	@$(MAKE) backend

.PHONY: ps
ps: ## Estado de db y de los procesos de dev en background
	@docker compose ps; \
	echo "--- procesos dev (pid files en $(LOG_DIR)/) ---"; \
	for f in $(LOG_DIR)/*.pid; do [ -f "$$f" ] && ps -p "$$(cat "$$f")" -o pid=,command= 2>/dev/null || echo "$$f: no corriendo"; done

.PHONY: stop
stop: ## Detiene background (matching/frontend) y baja la db (sin borrar datos)
	@for f in $(LOG_DIR)/*.pid; do \
		[ -f "$$f" ] && kill "$$(cat "$$f")" 2>/dev/null || true; rm -f "$$f"; \
	done; \
	docker compose down; \
	echo "Todo detenido."

# -----------------------------------------------------------------------------
# Test de TODO el monorepo
# -----------------------------------------------------------------------------
.PHONY: test
test: backend-test matching-test ## Suite de tests de backend + matching