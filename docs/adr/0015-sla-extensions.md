# ADR 0015 — SLA genişletmeleri: ilk yanıt, erken uyarı, eskalasyon ve mesai takvimi

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 15a (ilk yanıt, uyarı, eskalasyon) ve 15b (mesai takvimi)

## Bağlam
SLA yalnızca çözüm süresini, takvim saatiyle ölçüyordu ve ihlalde tek bir bildirim gidiyordu. Destek masalarında ayrıca
ilk yanıt süresi ölçülür, ihlalden önce uyarı verilir, ihlalde iş yükseltilir ve süre mesai saatinde işler.

## Karar

### İlk yanıt (15a)
- Personelin müşteriye görünen ilk tepkisi sayılır: ilk `EXTERNAL` personel yorumu **ya da** çözüme alma (`RESOLVED`).
  Dahili yorum, atama ve `WAITING_FOR_CUSTOMER` geçişi saymaz.
- Hedefler önceliğe göredir (`itsm.sla.first-response.<PRIORITY>`: 30 dk / 1 sa / 4 sa / 8 sa). Müşteri beklemesi bu saati
  durdurmaz. Öncelik değişince yanıtlanmamış talebin hedefi yeniden hesaplanır.
- Yanıtlanmadan kapanan talep (ör. geri çekme) ihlal sayılmaz.
- Saklanan alanlar: `first_response_due_at`, `first_responded_at`, `first_response_breach_notified_at` (V11).

### Erken uyarı ve eskalasyon (15a)
- Çözüm hedefinin %80'i dolunca `SLA_WARNING` (atanan; yoksa grup üyeleri; yoksa yöneticiler), talep başına bir kez.
  Uyarı anı `sla_warn_at` olarak saklanır ve `sla_due_at` gibi tek yerde (`Ticket.setSlaClock`) güncellenir.
- Çözüm SLA'sı aşılınca `SLA_BREACHED` atanana, yöneticilere ve grup üyelerine gider; talep `escalated_at` ile işaretlenir.
  `itsm.sla.escalation.raise-priority` açıksa öncelik bir kademe yükselir (varsayılan kapalı; aktör `system`).
- `SlaBreachService.run(now)` üç taramayı (uyarı, ilk yanıt ihlali, çözüm ihlali + eskalasyon) tek zamanlayıcıda yapar;
  her talep kendi transaction'ında işlenir.

### Mesai takvimi (15b)
- `BusinessCalendar` (saf Java): çalışma günleri, başlangıç–bitiş saati, saat dilimi (varsayılan `Europe/Istanbul`) ve
  tatiller. `plus(from, duration)` bitiş anını, `between(from, to)` geçen mesai süresini verir. `ALWAYS` 7/24'tür ve
  takvim kapalıyken kullanılır (varsayılan; önceki davranış).
- `SlaClock` ve `Ticket` işlemleri takvimi parametre olarak alır; eski imzalar `ALWAYS`'e devreder, domain saf kalır.
  Servisler takvimi `BusinessCalendarProvider`'dan alır (30 sn önbellek, kayıttan sonra commit'te yenilenir).
- Ayar tek satırlık `business_hours` ve `holidays` tablolarındadır (V12). Sabit tarihli resmî tatiller 2026–2027 için
  hazır gelir; dini bayramları yönetici ekler.
- `GET /api/sla/calendar` personele açık, `PUT` yalnızca yöneticiye. Doğrulama hatası `INVALID_CALENDAR`.
- **Takvim değişince** açık talepler yeniden hesaplanır (`Ticket.rebaseSlaClocks`): geçen süre eski takvimle kapatılır,
  yeni takvimle devam eder; yanıtlanmamış talebin ilk yanıt bitişi açılış anından yeni takvimle yeniden kurulur.
- **SQL tarafı:** ihlal adayları ve raporlar ham saat aritmetiği yerine saklanan anlara bakar: saat akıyorsa
  `sla_due_at < now`, duruyorsa `sla_elapsed_seconds > sla_target_seconds`. Takvim mantığı yalnızca Java'da kalır.

## Sonuçlar
- Mesai takvimi küreseldir: testler ve E2E ayarı değiştirip geri yüklemek zorundadır.
- Hedefler sunucu ayarından gelir; web sayfası onları salt okunur gösterir.
- Yeniden hesaplama açık talepleri tek transaction'da yükler; bu ölçekte yeterlidir.

## Kapsam dışı
Gruba/kategoriye özel SLA hedefi, birden çok takvim, dini bayramların otomatik hesaplanması, e-posta ile eskalasyon.
