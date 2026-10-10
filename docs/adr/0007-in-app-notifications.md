# ADR 0007 — Uygulama içi bildirimler

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-11
- **Aşama:** 5

## Bağlam
İş kararı: bildirimler yalnızca uygulama içi (`notifications` tablosu + arayüzde bir alan), e-posta yok. Kullanıcılar
ticket'larındaki değişiklikleri ve kendilerini bekleyen işleri sayfayı tek tek açmadan görebilmeli. Onaylanan kararlar:
yeni bildirimler için **30 saniyede bir sorgu** (SSE/push yok), SLA ihlali **atanan personele ve tüm yöneticilere**.

## Karar

### Kim, ne zaman (`notification/NotificationRules`, saf Java, birim testli)
İşlemi yapan kişi kendi işlemi için bildirim almaz.

| Olay | Tür | Alıcı |
|---|---|---|
| Statü değişikliği | `STATUS_CHANGED` (+ yeni statü) | talep eden + atanan |
| Sistemin otomatik kapatması | `AUTO_CLOSED` | yalnızca talep eden |
| Müşteriye açık yorum | `COMMENT_ADDED` | talep eden + atanan |
| Dahili yorum | `INTERNAL_COMMENT_ADDED` | yalnızca atanan (**müşteriye asla**) |
| SLA ihlali | `SLA_BREACHED` | atanan + tüm yöneticiler, ticket başına **bir kez** |

### Nasıl
- `TicketService` / `TicketCommentService` / `TicketAutoCloseService`, `TicketStatusChangedEvent` ve `TicketCommentAddedEvent`
  yayınlar. `NotificationService` bunları senkron `@EventListener` ile **aynı transaction içinde** kaydeder. Değişiklik geri
  alınırsa bildirim de geri alınır; kaydedilmiş bir değişikliğin bildirimi kaybolmaz.
- Bildirim cümle değil olgu saklar (`type`, `ticket_id`, `actor_id`, `ticket_status`). Cümleyi arayüz kurar, böylece metin ve
  dil istemcide kalır.
- **SLA ihlali:** `SlaBreachScheduler` (`itsm.sla.breach-check-interval`, varsayılan 60s) açık ticket'lardan SLA'sı aşılmış ve
  henüz bildirilmemiş olanları SQL'de bulur. Her birini kendi transaction'ında `SlaClock` ile yeniden kontrol eder, ardından
  `tickets.sla_breach_notified_at` alanını işaretleyip bildirimleri yazar. İtirazla saat yeniden başlasa da ikinci bildirim gitmez.
- **Yöneticiler:** Kullanıcı rehberi (`app_users`, ADR 0006) artık token'daki rolü de saklıyor. Yöneticiler oradan bulunur.
- **API** (`/api/notifications`): kendi bildirimlerin (yeniden eskiye, isteğe bağlı yalnızca okunmamışlar; ticket başlığı ve
  işlemi yapanın adı sayfa başına tek sorguyla), `/unread-count`, `/{id}/read` (tekrar çağrılabilir; başkasının bildirimi 404),
  `/read-all`.
- **Web:** Üst çubukta zil ve okunmamış sayısı (30 sn'de bir yenilenir). Açılan panelde son 10 bildirim; tıklayınca okundu
  işaretlenip ticket açılır. "Tümünü okundu işaretle" ve Escape / dışarı tıklamayla kapanma var.

## Sonuçlar
- (+) Dahili yorumların müşteriye sızmaması kuralla ve testle güvence altında.
- (+) Bildirimler değişiklikle atomik: hata alan işlem bildirim üretmez (testli).
- (−) Bildirim en fazla 30 sn gecikmeyle görünür. Anlık gerekirse SSE eklenebilir.
- (−) **Yönetici yalnızca en az bir kez giriş yaptıysa** bilinir. Hiç giriş yapmamış bir yönetici SLA ihlali bildirimi almaz.
  Kesin liste gerekirse Keycloak Admin API'den rol üyeleri okunmalı (servis hesabı gerekir).
- (−) SLA ihlali, kontrol aralığı kadar (60 sn) gecikebilir. Bu arada çözülen bir ticket için bildirim gitmez (bilinçli).
- (−) Eski bildirimler silinmiyor. Saklama süresi ve temizlik ileride eklenmeli.
- Tek backend örneği varsayımı otomatik kapanıştaki gibi geçerli (ADR 0005). Çift örnekte iyimser kilit çift bildirimi önler.
