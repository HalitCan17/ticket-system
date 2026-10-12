# ADR 0002 — Ticket statü akışı servis katmanında (BPMN değil)

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-09
- **Aşama:** 1

## Bağlam
Ticket statüleri: `NEW → IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER → RESOLVED → CLOSED`, `RESOLVED`'dan itirazla
`IN_PROGRESS`'e dönüş, çözümde zorunlu not. jBPM (Apache KIE 10.2.0) projede gömülü olarak çalışıyor
(`docs/jbpm-spike.md`), dolayısıyla bu akış BPMN ile de modellenebilirdi.

Spike bulguları: jBPM süreç durumu varsayılan olarak bellekte; kalıcılık ek bir add-on ve ayrı tablolar gerektiriyor.
Üretilen REST uçları kimliği query parametresinden alıyor. Ticket listesi, filtreleme ve "müşteri yalnızca kendi
ticket'ını görür" kuralları zaten `tickets` tablosu üzerinden çalışmak zorunda.

## Karar
- Statü kuralları saf Java sınıfında: `backend/src/main/java/com/itsm/backend/ticket/domain/TicketWorkflow.java`
  (izinli geçişler, kim yapabilir, hangi geçiş not ister). `Ticket.transitionTo(...)` bu kuralları uygular ve yan etkileri
  (atama, `resolved_at`, `closed_at`) yönetir.
- **Tek doğru kaynak `tickets` tablosu.** Her geçiş `ticket_status_history`'ye yazılır.
- jBPM ticket statüsünü yönetmez. İleride gerçekten süreç gerektiren işlerde (ör. `RESOLVED`'da N gün sonra otomatik
  `CLOSED` zamanlayıcısı, onay adımları) değerlendirilecek; o zaman da statüyü yine servis katmanı üzerinden değiştirecek.

## Sonuçlar
- (+) Kurallar Spring/DB olmadan birim testleniyor (`TicketWorkflowTest`: her `(kaynak, hedef, aktör)` kombinasyonu).
- (+) Tek veri modeli; süreç örneği ile ticket satırı arasında senkronizasyon sorunu yok.
- (+) SLA (WAITING'de duraklama) statü geçmişinden hesaplanabilir.
- (−) Akış görsel bir BPMN diyagramı olarak görünmüyor; dokümantasyon `TicketWorkflow` Javadoc'u ve bu ADR.
- (−) jBPM şimdilik yalnızca spike sürecini çalıştırıyor; kullanılmazsa bağımlılık ileride yeniden değerlendirilmeli.

## Ek (Aşama 10)
Talep sahibi, henüz işleme alınmamış talebini geri çekebilir: `NEW → CLOSED`, yalnızca `REQUESTER`, not isteğe bağlı.
Kural aynı tabloya eklendi; ayrıntılar [ADR 0010](0010-customer-features.md).
