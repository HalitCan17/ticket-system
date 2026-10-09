# ITSM Ticket System

IT Service Management (ticket) uygulaması: müşteriler talep açar, destek ekibi (agent/manager)
SLA takibiyle çözer. Backend Spring Boot + PostgreSQL, iş akışları jBPM (Apache KIE) ile.

> Durum: **Aşama 1** — ticket çekirdeği (ticket, statü akışı, yorumlar) backend API'si.
> Kimlik şimdilik geçici (yalnızca `dev` profili, bkz. [ADR 0003](docs/adr/0003-temporary-dev-header-identity.md)).

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

## Ticket akışı
```
NEW ──► IN_PROGRESS ◄──► WAITING_FOR_CUSTOMER
            │  ▲
   (not ile)▼  │ (itiraz, gerekçe ile)
          RESOLVED ──► CLOSED
```

| Geçiş | Kim |
|---|---|
| NEW → IN_PROGRESS (atanmamışsa yapana atanır) | AGENT, MANAGER |
| IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER | AGENT, MANAGER |
| IN_PROGRESS → RESOLVED (**çözüm notu zorunlu**) | AGENT, MANAGER |
| RESOLVED → IN_PROGRESS (**itiraz gerekçesi zorunlu**) | ticket sahibi, MANAGER |
| RESOLVED → CLOSED | ticket sahibi, MANAGER |

Müşteri yalnızca kendi ticket'larını görür (başkasınınki için 404). `INTERNAL` yorumlar yalnızca AGENT/MANAGER'a görünür.

## API
Tüm uçlar `dev` profilinde `X-User-Id` ve `X-User-Role` (`CUSTOMER` / `AGENT` / `MANAGER`) header'ları ister.

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/api/tickets` | ticket aç: `{title, description, priority: LOW\|MEDIUM\|HIGH\|CRITICAL}` |
| GET | `/api/tickets?status=&page=0&size=20` | sayfalı liste (`size` ≤ 100) |
| GET | `/api/tickets/{id}` | detay (+ çağıranın yapabileceği `allowedTransitions`) |
| POST | `/api/tickets/{id}/transitions` | `{targetStatus, note}` |
| GET | `/api/tickets/{id}/history` | statü geçmişi |
| GET | `/api/tickets/{id}/comments` | yorumlar |
| POST | `/api/tickets/{id}/comments` | `{body, visibility: INTERNAL\|EXTERNAL}` |

Hatalar [RFC 9457 ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457) olarak döner, makine okunur bir `code` alanıyla:

```json
{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"A note is required to move a ticket from IN_PROGRESS to RESOLVED.",
 "instance":"/api/tickets/2/transitions","code":"RESOLUTION_NOTE_REQUIRED"}
```

Örnek:

```bash
curl -X POST localhost:8080/api/tickets -H "X-User-Id: ayse" -H "X-User-Role: CUSTOMER" -H "Content-Type: application/json" -d '{"title":"VPN down","description":"Cannot connect","priority":"HIGH"}'
```

> Windows'ta Git Bash/PowerShell'den Türkçe karakterli body gönderirken komut satırı UTF-8 olmayabilir;
> body'yi UTF-8 bir dosyaya yazıp `--data-binary @body.json` ile gönderin.

## Dokümanlar
- [ADR 0001 — Spring Boot 3.5](docs/adr/0001-spring-boot-3.5.md)
- [ADR 0002 — Statü akışı servis katmanında](docs/adr/0002-ticket-status-flow-in-service-layer.md)
- [ADR 0003 — Geçici dev header kimliği](docs/adr/0003-temporary-dev-header-identity.md)
- [jBPM spike raporu](docs/jbpm-spike.md)
