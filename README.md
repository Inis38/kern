# Kern

Локальный веб-менеджер сервера: мониторинг состояния и утилизации ресурсов (CPU, память, диски, uptime, load average).

## Стек

- **Kotlin** + **Spring Boot 3**
- **Thymeleaf** — UI
- **OSHI** — сбор системных метрик
- Порт по умолчанию: **8090**

## Архитектура

```
domain/          — модели и порты (MetricsCollector, HealthEvaluator)
application/     — MonitoringService (фасад для UI и будущих модулей)
infrastructure/  — реализации (OSHI, оценка здоровья)
web/             — контроллеры, DTO, REST API
```

Новые возможности (алерты, процессы, логи, SSH и т.д.) добавляются отдельными реализациями портов или новыми сервисами в `application/`, без переписывания веб-слоя.

## Требования

- **Локально:** JDK 21+
- **Docker:** Docker Engine + Docker Compose

## Запуск

### Локально (разработка)

```bash
./gradlew bootRun
```

Приложение стартует на порту **8090**. Откройте: http://localhost:8090

### JAR на сервере

```bash
./gradlew bootJar
java -jar build/libs/kern.jar
```

### Docker (Ubuntu, для тестирования)

Рекомендуется для проверки в Linux-окружении — load average, `/proc` и диски работают как на целевом сервере.

```bash
docker compose up --build
```

Дашборд: http://localhost:8090

Остановка:

```bash
docker compose down
```

Сборка и запуск образа вручную:

```bash
docker build -t kern .
docker run --rm -p 8090:8090 kern
```

Runtime-образ: **Ubuntu 24.04** + JRE 21. Метрики отражают окружение контейнера.

## API

`GET /api/v1/monitoring` — JSON-снимок метрик и состояния (используется автообновлением дашборда).

## Конфигурация

`src/main/resources/application.yml`:

- `server.port` — порт (8090)
- `kern.monitoring.refresh-interval-ms` — интервал обновления UI
- `kern.monitoring.cpu-sample-delay-ms` — задержка замера CPU
