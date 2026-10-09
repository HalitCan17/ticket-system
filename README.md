# ITSM Ticket System

IT Service Management (ticket) uygulaması: müşteriler talep açar, destek ekibi (agent/manager)
SLA takibiyle çözer. Backend Spring Boot + PostgreSQL, iş akışları jBPM (Apache KIE) ile.

> Durum: **Aşama 0** — proje iskeleti ve jBPM spike'ı. Ayrıntılar: [docs/jbpm-spike.md](docs/jbpm-spike.md)

## Teknoloji
| Katman | Teknoloji |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| İş akışı | Apache KIE 10.2.0 jBPM (gömülü) |
| Veritabanı | PostgreSQL 18 |
| Web / Mobil | React 19 + Vite / Expo (planlandı) |
| Kimlik | Keycloak 26.8 (planlandı) |
| Altyapı | Docker Compose |

## Hızlı başlangıç
Gereksinimler: Docker, (geliştirme için) Java 21.

```bash
docker compose -f infra/docker-compose.yml up -d --build
curl http://localhost:8080/actuator/health
```

Testler:

```bash
cd backend && ./mvnw test
```

## Dokümanlar
- [ADR 0001 — Spring Boot 3.5](docs/adr/0001-spring-boot-3.5.md)
- [jBPM spike raporu](docs/jbpm-spike.md)
