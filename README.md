# ITSM Ticket System

IT Service Management (ticket) uygulaması: müşteriler talep açar, destek ekibi (agent/manager)
SLA takibiyle çözer. Backend Spring Boot + PostgreSQL, iş akışları jBPM (Apache KIE) ile.

> Durum: **Aşama 6** — ticket ekleri, uygulama içi bildirimler; web arayüzü (React + Keycloak girişi), ticket API'si, JWT kimlik doğrulama, SLA ve otomatik kapanış.

## Teknoloji
| Katman | Teknoloji |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| İş akışı | Apache KIE 10.2.0 jBPM (gömülü) |
| Veritabanı | PostgreSQL 18 |
| Web | React 19, TypeScript, Vite, React Router, TanStack Query, keycloak-js |
| Mobil | Expo (planlandı) |
| Kimlik | Keycloak 26.8 (OIDC, JWT) |
| Altyapı | Docker Compose |

## Hızlı başlangıç
Gereksinimler: Docker; geliştirme için Java 21 ve Node 22.22+.

```bash
docker compose -f infra/docker-compose.yml up -d --build
```

Ardından **http://localhost:3000** adresini açıp aşağıdaki yerel kullanıcılardan biriyle giriş yapın.

| Servis | Adres |
|---|---|
| Web uygulaması | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Keycloak (admin: `admin` / `admin`) | http://localhost:8081 |
| PostgreSQL (`itsm` / `itsm`) | localhost:5432 |

> Keycloak realm'i (`infra/keycloak/itsm-realm.json`) yalnızca Postgres volume'u **ilk oluşturulurken** içe aktarılır.
> Aşama 4'ten önce oluşturulmuş bir volume'unuz varsa bir kez `docker compose -f infra/docker-compose.yml down -v`
> çalıştırın (yerel veriyi siler), aksi hâlde `localhost:3000` girişte reddedilir.

Testler:

```bash
cd backend && ./mvnw test
```

```bash
cd web && npm ci && npm test
```

Web'i geliştirme modunda çalıştırmak (backend ve Keycloak Compose'ta açıkken; `/api` Vite tarafından backend'e yönlendirilir):

```bash
cd web && npm run dev
```

## Web arayüzü
- Keycloak ile giriş (Authorization Code + PKCE); üst çubukta kullanıcı, rol ve çıkış.
- **Talepler:** durum filtresi, sayfalama, öncelik/durum/SLA rozetleri, talep eden ve atanan kişinin adı.
- **Yeni talep:** başlık, açıklama, öncelik; backend doğrulama hataları alanların altında.
- **Talep detayı:** yalnızca kullanıcının yapabileceği aksiyonlar (backend'in `allowedTransitions` alanı), çözüm notu ve
  itiraz gerekçesi zorunlu, çözüm ve otomatik kapanış bildirimi, SLA bilgileri, statü geçmişi, yorumlar
  (personel için dahili / müşteriye açık seçimi, dahili yorumlar müşteriye hiç gelmez).
- **Ekler:** talep detayında dosya ekleme, indirme ve silme (personel için dahili / müşteriye açık seçimi).
- **Bildirimler:** üst çubukta zil ve okunmamış sayısı; tıklayınca ilgili talep açılır.
- Mimari: [ADR 0006](docs/adr/0006-web-app-architecture.md).

## Ekler
Dosyalar Docker volume'unda (`attachments`) saklanır, veritabanında yalnızca bilgileri durur.

- Dosya başına en fazla **10 MB**. İzin verilen türler: png, jpg/jpeg, gif, webp, pdf, txt, log, csv, zip, docx, xlsx, pptx.
  Uzantının yanında dosyanın ilk baytları da kontrol edilir; adı değiştirilmiş bir dosya reddedilir.
- Görünürlük yorumlardaki gibidir: müşteri dahili ekleri göremez. Yükleyen kendi ekini, yönetici her eki silebilir.
  Kapalı taleplerde ek değişmez.
- İndirmeler her zaman dosya olarak iner (tarayıcıda açılmaz).
- Yedek alırken `pgdata` ve `attachments` volume'larını birlikte alın.

API: `GET /api/tickets/{id}/attachments`, `POST /api/tickets/{id}/attachments` (multipart: `file`, `visibility`),
`GET /api/tickets/{id}/attachments/{attachmentId}/content`, `DELETE /api/tickets/{id}/attachments/{attachmentId}`.
Ayrıntılar: [ADR 0008](docs/adr/0008-attachments.md).

## Bildirimler
Uygulama içi bildirimlerdir (e-posta yok), arayüz 30 saniyede bir kontrol eder. İşlemi yapan kişi kendi işlemi için bildirim almaz.

| Olay | Alıcı |
|---|---|
| Statü değişikliği | talep eden ve atanan |
| Müşteriye açık yorum | talep eden ve atanan |
| Dahili yorum | yalnızca atanan (müşteriye asla) |
| Ek yüklendi | yorumlardaki gibi (dahili ek yalnızca atanana) |
| Otomatik kapanış | talep eden |
| SLA ihlali (ticket başına bir kez) | atanan ve tüm yöneticiler* |

\* Yöneticiler, uygulamaya en az bir kez giriş yapmış olanlardır. Ayrıntılar: [ADR 0007](docs/adr/0007-in-app-notifications.md).

API: `GET /api/notifications?unreadOnly=&page=&size=`, `GET /api/notifications/unread-count`,
`POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.

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

## SLA ve otomatik kapanış
Çözüm süresi SLA'sı takvim süresiyle (7/24) sayılır; hedef ticket açılırken öncelikten alınır:

| Öncelik | Hedef |
|---|---|
| CRITICAL | 4 saat |
| HIGH | 8 saat |
| MEDIUM | 24 saat |
| LOW | 72 saat |

Saat `NEW`/`IN_PROGRESS`'te çalışır, `WAITING_FOR_CUSTOMER`'da **durur**, çözümde durur; itirazda kaldığı yerden devam eder.
Her ticket cevabında `sla` alanı döner:

```json
"sla": {"state":"RUNNING","targetSeconds":14400,"elapsedSeconds":5,"remainingSeconds":14395,
        "dueAt":"2026-10-10T17:09:04.209911Z","breached":false}
```

`RESOLVED` ticket müşteri yanıt vermezse **7 gün** sonra sistem tarafından kapatılır (geçmişte `changedBy: "system"`);
cevaptaki `autoCloseAt` bu anı gösterir. Ayarlar: [ADR 0005](docs/adr/0005-sla-and-auto-close.md).

| Ayar | Varsayılan | Compose env |
|---|---|---|
| `itsm.sla.targets.<PRIORITY>` | yukarıdaki tablo | — |
| `itsm.auto-close.after` | `7d` | `ITSM_AUTOCLOSE_AFTER` |
| `itsm.auto-close.poll-interval` | `60s` | `ITSM_AUTOCLOSE_POLLINTERVAL` |
| `itsm.auto-close.enabled` | `true` | — |

Otomatik kapanışı bir dakika içinde görmek için:

```bash
ITSM_AUTOCLOSE_AFTER=30s ITSM_AUTOCLOSE_POLLINTERVAL=5s docker compose -f infra/docker-compose.yml up -d backend
```

## Kimlik doğrulama
`/api/**` uçları Keycloak'ın `itsm` realm'inden alınmış bir access token ister (`Authorization: Bearer ...`).
Ayrıntılar: [ADR 0004](docs/adr/0004-keycloak-jwt-resource-server.md).

Yerel geliştirme kullanıcıları (şifre = kullanıcı adı, **yalnızca yerel geliştirme için**):

| Kullanıcı | Rol |
|---|---|
| `customer1`, `customer2` | CUSTOMER |
| `agent1` | AGENT |
| `manager1` | MANAGER |

Token almak (yerel `itsm-cli` client'ı, password grant):

```bash
curl -s -d client_id=itsm-cli -d grant_type=password -d username=customer1 -d password=customer1 http://localhost:8081/realms/itsm/protocol/openid-connect/token
```

## API

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

Örnek (`TOKEN` yukarıdaki cevaptaki `access_token`):

```bash
curl -X POST localhost:8080/api/tickets -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"title":"VPN down","description":"Cannot connect","priority":"HIGH"}'
```

> Windows'ta Git Bash/PowerShell'den Türkçe karakterli body gönderirken komut satırı UTF-8 olmayabilir;
> body'yi UTF-8 bir dosyaya yazıp `--data-binary @body.json` ile gönderin.

## Dokümanlar
- [ADR 0001 — Spring Boot 3.5](docs/adr/0001-spring-boot-3.5.md)
- [ADR 0002 — Statü akışı servis katmanında](docs/adr/0002-ticket-status-flow-in-service-layer.md)
- [ADR 0003 — Geçici dev header kimliği](docs/adr/0003-temporary-dev-header-identity.md) (yerini 0004 aldı)
- [ADR 0004 — Keycloak ile JWT resource server](docs/adr/0004-keycloak-jwt-resource-server.md)
- [ADR 0005 — SLA ve otomatik kapanış](docs/adr/0005-sla-and-auto-close.md)
- [ADR 0006 — Web uygulaması mimarisi](docs/adr/0006-web-app-architecture.md)
- [ADR 0007 — Uygulama içi bildirimler](docs/adr/0007-in-app-notifications.md)
- [ADR 0008 — Ticket ekleri](docs/adr/0008-attachments.md)
- [jBPM spike raporu](docs/jbpm-spike.md)
