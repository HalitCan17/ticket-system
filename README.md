# ITSM Ticket System

[![CI](https://github.com/HalitCan17/ticket-system/actions/workflows/ci.yml/badge.svg)](https://github.com/HalitCan17/ticket-system/actions/workflows/ci.yml)

IT Service Management (ticket) uygulaması: müşteriler talep açar, destek ekibi (agent/manager)
SLA takibiyle çözer. Backend Spring Boot + PostgreSQL, iş akışları jBPM (Apache KIE) ile.

> Durum: **Aşama 13** — yönetici paneli ve ekip görünümü, hazır yanıtlar, personel iş kuyruğu, üstlenme/devretme/atama, öncelik ve kategori değiştirme; müşteri özellikleri (kategori, arama, geri çekme, memnuniyet puanı), GitHub Actions CI, log4j2 + OpenTelemetry + OpenSearch ile loglama ve izleme, Playwright uçtan uca testleri, ticket ekleri, uygulama içi bildirimler; web arayüzü (React + Keycloak girişi), ticket API'si, JWT kimlik doğrulama, SLA ve otomatik kapanış.

## Teknoloji
| Katman | Teknoloji |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| İş akışı | Apache KIE 10.2.0 jBPM (gömülü) |
| Veritabanı | PostgreSQL 18 |
| Web | React 19, TypeScript, Vite, React Router, TanStack Query, keycloak-js |
| Mobil | Expo (planlandı) |
| Kimlik | Keycloak 26.8 (OIDC, JWT) |
| Loglama / izleme | log4j2 (JSON), OpenTelemetry Java agent, Data Prepper, OpenSearch 3.6 + Dashboards |
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
| OpenSearch Dashboards (loglar, trace'ler) | http://localhost:5601 |
| OpenSearch | http://localhost:9200 |

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

Her push'ta GitHub Actions ([`ci.yml`](.github/workflows/ci.yml)) backend testlerini (`./mvnw verify`, Testcontainers ile)
ve web testlerini/build'ini çalıştırır. `main`'e push'larda bu ikisi geçerse uçtan uca testler de Compose yığınına karşı çalışır
(gözlemleme servisleri olmadan). Hata olursa test raporları Actions sayfasında artifact olarak indirilebilir.

### Uçtan uca testler (Playwright)
`web/e2e/` altındaki testler gerçek bir tarayıcıyla çalışan Compose yığınını (`http://localhost:3000`) kullanır. Kapsadıkları:
Keycloak girişi, müşterinin başkasının talebini görememesi, talebin tüm yaşam döngüsü (itiraz dahil), dahili yorum gizliliği,
bildirim, ek yükleme/indirme/silme. Yığını kendileri başlatmazlar.

```bash
docker compose -f infra/docker-compose.yml up -d --build
cd web && npx playwright install chromium && npm run e2e
```

- Kurulu Chrome ile (Chromium indirmeden): `E2E_BROWSER_CHANNEL=chrome npm run e2e`
- Başka adres: `E2E_BASE_URL=http://localhost:3000`. Görsel mod: `npm run e2e -- --ui`. Hata raporu: `npx playwright show-report`.
- Testler `customer1`, `customer2`, `agent1` kullanıcılarıyla benzersiz başlıklı talepler açar; veritabanında `E2E …` talepleri birikir.

Web'i geliştirme modunda çalıştırmak (backend ve Keycloak Compose'ta açıkken; `/api` Vite tarafından backend'e yönlendirilir):

```bash
cd web && npm run dev
```

## Loglama ve izleme
Backend log4j2 ile loglar. Compose'ta her satır JSON'dur (`docker logs itsm-backend-1`). OpenTelemetry Java agent her API
isteğini bir **trace** olarak (HTTP → servis → SQL span'ları) ve logları aynı `trace_id` ile Data Prepper üzerinden OpenSearch'e
gönderir.

- **Trace'ler:** http://localhost:5601 → Observability → Traces (veri kaynağı "Data Prepper"). Bir trace'e tıklayınca span zaman
  çizelgesi ve "View associated logs" açılır.
- **Loglar:** Discover → `otel-logs-*` (index pattern'i `dashboards-init` servisi otomatik oluşturur).
- İzleme servisleri kapalı olsa da uygulama çalışır, yalnızca izleme verisi kaybolur.
- **Yerel geliştirme içindir:** OpenSearch güvenlik eklentisi kapalıdır ve portlar yalnızca `127.0.0.1`'e açıktır.
- Yığın bu servislerle yaklaşık 2 GB daha fazla bellek kullanır. İlk `up` yaklaşık 2,2 GB imaj indirir (bir kez).

Ayrıntılar: [ADR 0009](docs/adr/0009-observability.md).

## Web arayüzü
- Keycloak ile giriş (Authorization Code + PKCE); üst çubukta kullanıcı, rol ve çıkış.
- **Talepler:** Açık / Kapanmış / Tümü sekmeleri, başlıkta ya da `#numara` ile arama, kategori ve durum filtreleri
  (hepsi URL'de), sayfalama, öncelik/durum/SLA rozetleri, talep eden ve atanan kişinin adı.
- **Yeni talep:** başlık, açıklama, kategori, öncelik; backend doğrulama hataları alanların altında.
- **Müşteri:** henüz işleme alınmamış talebini geri çekebilir; çözülen talebe bir kez 1–5 yıldız ve yorum verir.
- **Personel:** liste üstünde iş kuyruğu (Herkesin / Bana atananlar / Atanmamışlar) ve "SLA'sı en yakın" sıralaması.
  Agent atanmamış talebi üstlenir, kendi talebini devreder ya da bırakır; yönetici her talebi atar. Öncelik (gerekçeyle,
  SLA hedefi yeniden hesaplanır) ve kategori değiştirilebilir. Bu değişiklikler geçmişte yalnızca personele görünür.
- **Hazır yanıtlar:** personel yorum yazarken şablon seçer; `{müşteri}`, `{talep}`, `{ben}` doldurulmuş olarak kutuya
  eklenir (gönderilmez). Yönetici şablonları "Hazır yanıtlar" sayfasından ekler, düzenler, siler.
- **Panel (yönetici):** açık / atanmamış / SLA'sı aşılmış talepler, son 7-30-90 günde açılan ve çözülen, SLA uyum oranı,
  ortalama çözüm süresi, memnuniyet; statü, kategori ve puan dağılımları; ekip tablosu (kişi başına açık iş, SLA aşımı,
  çözülen, memnuniyet) ve oradan kişinin kuyruğuna geçiş.
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
| Memnuniyet puanı verildi | atanan |
| Talep atandı / atamadan çıkarıldı | yeni atanan / önceki atanan |
| Öncelik değişti | atanan |
| Otomatik kapanış | talep eden |
| SLA ihlali (ticket başına bir kez) | atanan ve tüm yöneticiler* |

\* Yöneticiler, uygulamaya en az bir kez giriş yapmış olanlardır. Ayrıntılar: [ADR 0007](docs/adr/0007-in-app-notifications.md).

API: `GET /api/notifications?unreadOnly=&page=&size=`, `GET /api/notifications/unread-count`,
`POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.

## Ticket akışı
```
NEW ──► IN_PROGRESS ◄──► WAITING_FOR_CUSTOMER
 │          │  ▲
 │ (not ile)▼  │ (itiraz, gerekçe ile)
 │        RESOLVED ──► CLOSED
 └──────(geri çekme)────► CLOSED
```

| Geçiş | Kim |
|---|---|
| NEW → IN_PROGRESS (atanmamışsa yapana atanır) | AGENT, MANAGER |
| IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER | AGENT, MANAGER |
| IN_PROGRESS → RESOLVED (**çözüm notu zorunlu**) | AGENT, MANAGER |
| RESOLVED → IN_PROGRESS (**itiraz gerekçesi zorunlu**) | ticket sahibi, MANAGER |
| RESOLVED → CLOSED | ticket sahibi, MANAGER |
| NEW → CLOSED (geri çekme, not isteğe bağlı) | yalnızca ticket sahibi |

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
| POST | `/api/tickets` | ticket aç: `{title, description, priority: LOW\|MEDIUM\|HIGH\|CRITICAL, category: HARDWARE\|SOFTWARE\|NETWORK\|ACCESS\|OTHER}` |
| GET | `/api/tickets?q=&scope=OPEN\|CLOSED\|ALL&category=&status=&page=0&size=20` | sayfalı liste (`q`: başlıkta ya da `#id`; personel için `assignee=me\|unassigned\|<id>`, `sort=SLA`; `size` ≤ 100) |
| GET | `/api/tickets/{id}` | detay (+ çağıranın yapabileceği `allowedTransitions`) |
| POST | `/api/tickets/{id}/transitions` | `{targetStatus, note}` |
| PUT | `/api/tickets/{id}/assignee` | personel: `{assigneeId}` (null = atamayı kaldır); agent yalnız üstlenir/kendi talebini devreder |
| PATCH | `/api/tickets/{id}` | personel: `{priority, category, reason}` (öncelik değişince gerekçe zorunlu) |
| GET | `/api/tickets/{id}/activities` | personel: atama/öncelik/kategori geçmişi |
| GET | `/api/users/staff` | personel: atanabilir agent ve yöneticiler |
| GET | `/api/canned-responses` | personel: hazır yanıtlar |
| GET | `/api/reports/summary?days=30` | yönetici: panel göstergeleri |
| GET | `/api/reports/team?days=30` | yönetici: kişi başına iş yükü |
| POST / PUT / DELETE | `/api/canned-responses[/{id}]` | yönetici: `{title, body, visibility}` (başlık benzersiz) |
| POST | `/api/tickets/{id}/rating` | talep sahibi, çözülmüş talebe bir kez: `{score: 1..5, comment}` |
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
curl -X POST localhost:8080/api/tickets -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"title":"VPN down","description":"Cannot connect","priority":"HIGH","category":"NETWORK"}'
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
- [ADR 0009 — Loglama ve izleme](docs/adr/0009-observability.md)
- [ADR 0010 — Müşteri özellikleri](docs/adr/0010-customer-features.md)
- [ADR 0011 — İş kuyruğu ve atama](docs/adr/0011-work-queue-and-assignment.md)
- [ADR 0012 — Hazır yanıtlar](docs/adr/0012-canned-responses.md)
- [ADR 0013 — Yönetici paneli](docs/adr/0013-manager-dashboard.md)
- [jBPM spike raporu](docs/jbpm-spike.md)
