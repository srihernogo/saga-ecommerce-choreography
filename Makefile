# Makefile
.PHONY: build up down logs test clean

## Build semua service
build:
	mvn clean install -DskipTests
	docker compose build

## Jalankan seluruh stack
up:
	docker compose up -d
	@echo "Menunggu service siap..."
	@sleep 10
	@make health

## Cek health semua service
health:
	@curl -s http://localhost:8081/actuator/health | jq .status
	@curl -s http://localhost:8082/actuator/health | jq .status
	@curl -s http://localhost:8083/actuator/health | jq .status
	@curl -s http://localhost:8084/actuator/health | jq .status

## Stop semua
down:
	docker compose down -v

## Lihat log service tertentu (make logs svc=order-service)
logs:
	docker compose logs -f $(svc)

## Run semua test
test:
	mvn test

## Smoke test (kirim 1 order)
smoke:
	@./scripts/smoke-test.sh

## Bersihkan semua artifact
clean:
	mvn clean
	docker compose down -v --rmi local
