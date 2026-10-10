# ADR 0005 — Çözüm süresi SLA'sı ve otomatik kapanış

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-10
- **Aşama:** 3

## Bağlam
İş kuralları: SLA süresi `WAITING_FOR_CUSTOMER`'da durur; `RESOLVED`'da müşteri yanıt vermezse N gün sonra ticket
otomatik `CLOSED` olur. Kapsam kararları: yalnızca **çözüm süresi** SLA'sı (ilk yanıt yok), **takvim süresi 7/24**
(mesai takvimi yok), zamanlayıcı olarak **Spring `@Scheduled`**.

## Karar

### SLA saati
- Hedef önceliğe göre `itsm.sla.targets.<PRIORITY>` ayarından gelir. Varsayılanlar: CRITICAL 4h, HIGH 8h, MEDIUM 24h, LOW 72h.
- Hedef **ticket açılırken ticket'a kopyalanır** (`sla_target_seconds`). Ayar sonradan değişirse açık ticket'lar etkilenmez.
- Saat ticket satırında üç kolonla tutulur: `sla_target_seconds`, `sla_elapsed_seconds` (biten çalışma dönemlerinin toplamı),
  `sla_running_since` (çalışıyorsa başlangıç anı, değilse `NULL`). Böylece her okumada statü geçmişini taramadan
  `elapsed = elapsed_seconds + (now − running_since)` hesaplanır. Geçmiş tablosu yine her geçişi tutar (denetim için).
- Saat NEW/IN_PROGRESS'te çalışır, WAITING_FOR_CUSTOMER'da durur, RESOLVED/CLOSED'da durmuş kalır.
- **İtiraz** (RESOLVED → IN_PROGRESS) saati **kaldığı yerden** yeniden başlatır. İtiraz, sorunun aslında çözülmediğini
  gösterdiği için ilk çözüm için harcanan süre de SLA'ya sayılır.
- İhlal: harcanan süre hedefi **aştığında** (eşit olmak ihlal değil). İhlal saklanmaz, her okumada hesaplanır.
- Mantık saf Java'da: `ticket/domain/SlaClock` (değişmez, sabit `Instant`'larla birim testli). `Ticket.transitionTo` her geçişten
  sonra saati statüye göre durdurur/başlatır.
- API: her ticket cevabında `sla {state, targetSeconds, elapsedSeconds, remainingSeconds, dueAt, breached}`.
  `dueAt` yalnızca saat çalışırken dolu; `remainingSeconds` ihlalden sonra negatiftir.

### Otomatik kapanış
- `AutoCloseScheduler` (`itsm.auto-close.poll-interval`, varsayılan 60s) → `TicketAutoCloseService.closeStaleResolved(now)`.
- `resolved_at <= now − itsm.auto-close.after` (varsayılan 7d) olan RESOLVED ticket'lar id sırasıyla, 100'lük sayfalarla
  (keyset) taranır. Her ticket **kendi transaction'ında** yeniden yüklenip yeniden kontrol edilir ve `Ticket.autoClose` ile kapatılır.
  Geçmişe `changed_by = system`, not `Auto-closed: no customer response for 7d` yazılır.
- `TicketWorkflow.validateSystemClose`: sistemin yapabildiği tek geçiş `RESOLVED → CLOSED`. Kullanıcı kuralları
  (`validate`) değişmedi.
- Yarış durumu: müşteri aynı anda itiraz eder ya da kapatırsa `@Version` iyimser kilidi çakışmayı yakalar. Sistem o ticket'ı
  atlar ve hata vermez. Bir ticket'taki beklenmedik hata da yalnızca loglanır, diğerleri işlenir.
- Cevapta RESOLVED ticket'lar için `autoCloseAt = resolvedAt + after` döner.
- `itsm.auto-close.enabled=false` ile kapatılabilir. Compose'ta `ITSM_AUTOCLOSE_AFTER` / `ITSM_AUTOCLOSE_POLLINTERVAL`
  ile süre kısaltılıp elle denenebilir (Spring env eşlemesinde tire düşer).

### Neden jBPM timer değil
Ticket statüsü servis katmanında yönetiliyor (ADR 0002). jBPM timer'ı için her ticket'a bir süreç örneği, jBPM kalıcılığı ve
job servisi gerekirdi (spike'ta bunlar denenmedi). Tek bir periyodik sorgu aynı işi daha az bileşenle görüyor ve test edilmesi kolay.

## Sonuçlar
- (+) Kurallar birim testli (`SlaClockTest`, `TicketTest`, `TicketAutoCloseServiceTest`). Otomatik kapanış gerçek veritabanıyla
  "zamanı ileri alarak" test ediliyor (`closeStaleResolved(now)` parametresi sayesinde beklemeden).
- (+) SLA okuması tek satırdan, sorgusuz.
- (−) Biriken süre saniye hassasiyetinde saklanır (saniye altı kısım her duraklamada düşer); SLA saat mertebesinde olduğu için önemsiz.
- (−) **Tek backend örneği varsayılır.** Birden fazla örnekte her biri aynı ticket'ları tarar. İyimser kilit çift kapatmayı engeller
  ama iş tekrarlanır. Ölçeklenince ShedLock gibi bir zamanlayıcı kilidi eklenmeli.
- (−) Kapanış zamanı en fazla `poll-interval` kadar gecikebilir (varsayılan 60s).
- (−) V3 migration'ı mevcut satırlar için geçen süreyi zaman damgalarından **yaklaşık** doldurur (yalnızca geliştirme verisi vardı).
- Kapsam dışı, sonraki aşamalar: ilk yanıt SLA'sı, mesai takvimi, SLA ihlali bildirimi (bildirimler aşaması), SLA'ya göre filtre/rapor.
