# ADR 0011 — İş kuyruğu, atama, öncelik ve kategori değiştirme

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 11 (rol özellikleri yol haritasının ikinci adımı; bkz. ADR 0010)

## Bağlam
Bu aşamadan önce bir talep yalnızca "İşleme al" ile, işlemi yapan kişiye atanabiliyordu. Kimse talebi başkasına veremiyor,
yönetici iş dağıtamıyordu. Öncelik ve kategori talep açıldıktan sonra değişmiyordu. Agent'ın "bana ne düştü?" sorusunu
cevaplayan bir görünüm de yoktu.

## Karar

### Atama kuralları (`TicketAssignment`, saf Java, birim testli)
| Rol | İzin |
|---|---|
| AGENT | Atanmamış talebi **üstlenir** (yalnız kendine). Kendi talebini başka bir personele **devreder** ya da **bırakır**. Başkasının talebine dokunamaz. |
| MANAGER | Her talebi her personele atar ya da atamayı kaldırır. |
| CUSTOMER | Hiçbiri (403 `ASSIGNMENT_NOT_ALLOWED`). |

- Talep yalnızca bilinen bir personele (`app_users.role` AGENT/MANAGER) atanabilir, aksi hâlde 400 `ASSIGNEE_NOT_STAFF`.
  Kapalı talebin ataması değişmez (409). Talep zaten o kişideyse istek bir şey değiştirmez ve kayıt üretmez.
- "İşleme al" ile otomatik atama aynen kalır.
- Yanıttaki `canClaim` ve `canReassign` alanları hangi düğmelerin görüneceğini söyler. Web kuralı tekrar uygulamaz.

### Öncelik ve kategori (`PATCH /api/tickets/{id}`)
- Yalnızca personel değiştirebilir ve kapalı talep değişmez.
- Öncelik değişince **gerekçe zorunludur** (`PRIORITY_REASON_REQUIRED`). SLA hedefi yeni önceliğin hedefi olur ve harcanan
  süre korunur (`SlaClock.withTarget`). Yeni hedefle ihlal ortadan kalkarsa "ihlal bildirildi" işareti sıfırlanır, böylece
  sonraki ihlal yine bildirilir.
- Çözülmüş talebin önceliği değişmez, çünkü SLA durmuştur (409 `PRIORITY_LOCKED`). Kategori ise değişebilir.

### Etkinlik geçmişi
- Atama, öncelik ve kategori değişiklikleri `ticket_activities` tablosuna yazılır: tür, eski ve yeni değer, not, kim, ne
  zaman. Statü geçmişi ayrı tabloda kalır. Web ikisini tek "Geçmiş" listesinde zaman sırasıyla birleştirir.
- Etkinlikleri **yalnız personel görür**, çünkü öncelik gerekçesi dahili olabilir. Müşteri `GET .../activities` için 404
  alır. Atanan kişiyi zaten talebin üzerinde görür.

### Bildirimler
- Yeni atanana `TICKET_ASSIGNED`, atamadan çıkarılana `TICKET_UNASSIGNED` gider. Öncelik değişince atanana
  `PRIORITY_CHANGED` gider.
- Kimse kendi işlemi için bildirim almaz. Örneğin talebi üstlenen kişiye bildirim gitmez.

### İş kuyruğu
- `GET /api/tickets` iki yeni parametre alır: `assignee=me|unassigned|<id>` ve `sort=SLA`. Bunlar yalnızca personel için
  geçerlidir; müşteri gönderirse yok sayılır.
- SLA sıralaması en acil talebi üste koyar:
  1. Üzerinde çalışılan talepler, saati akanlar bitiş zamanına göre.
  2. Duraklatılanlar, kalan sürelerine göre.
  3. Çözülmüş ya da kapalı talepler.
- Sıralama için `tickets.sla_due_at` sütunu eklendi. Bu sütun SLA saati her değiştiğinde `Ticket` tarafından tek yerden
  güncellenir; saat duraklamış ya da durmuşsa NULL olur. V8 migration'ı mevcut satırları doldurur. "Şimdi"ye bağlı
  olmadığı için indekslenebilir.
- Atama seçim listesi için `GET /api/users/staff` eklendi (yalnız personel).

## Sonuçlar
- (+) Agent kendi kuyruğunu ve atanmamış işleri görür. Yönetici iş dağıtır. Öncelik hatası SLA'yı doğru hedefe taşır.
  Her değişiklik kim/ne zaman bilgisiyle kayıtlıdır.
- (+) Kurallar saf Java'dadır. Bütün rol ve durum kombinasyonları birim testlerle, uçlar ise gerçek Postgres'le test edildi.
- (−) Atama listesi `app_users`'tan gelir. En az bir kez giriş yapmamış bir agent listede görünmez (ADR 0007 ile aynı
  sınırlama). Keycloak admin API'siyle kullanıcı çekmek kapsam dışıdır.
- (−) Toplu atama, otomatik dağıtım (round-robin) ve agent kapasite sınırı yoktur.
