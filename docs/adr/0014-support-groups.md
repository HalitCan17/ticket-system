# ADR 0014 — Destek grupları, kategori yönlendirmesi ve grup içi otomatik atama

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 14 (Faz 1: iş akışını güçlendirme; Aşama 15 SLA genişletme ve eskalasyon)

## Bağlam
Bütün talepler tek bir ortak kuyruğa düşüyordu ve atama tamamen elle yapılıyordu. Gerçek bir destek masasında talep,
konusuna göre ilgili ekibe gider ve çoğu zaman ekip içinde otomatik dağıtılır.

## Karar

### Model
- `support_groups`: ad (benzersiz, büyük/küçük harf duyarsız; Postgres `lower()` Türkçe İ'yi de i yapar), açıklama,
  atama modu ve round-robin imleci (`last_assigned_id`).
- `support_group_members`: grup ile kullanıcı eşlemesi. Yalnızca bilinen personel eklenebilir (`MEMBER_NOT_STAFF`).
- `category_routes`: her kategori en fazla bir gruba bağlanır.
- `tickets.group_id`: grup silinince `SET NULL` olur.
- V10, kategori başına bir başlangıç grubu oluşturur ve mevcut talepleri gruplara bağlar. Gruplar **üyesiz** başlar:
  realm kullanıcılarının sabit id'si yok, üyeler yönetici ekranından eklenir.

### Yönlendirme
- Talep açılınca kategorisinin grubuna girer. Bu bir etkinlik olarak kaydedilmez, başlangıç durumudur.
- Personel kategoriyi değiştirirse talep yeni kategorinin grubuna geçer (rota varsa).
- Personel grubu elle de değiştirebilir ya da kaldırabilir (`PATCH /api/tickets/{id}` + `groupId` / `removeGroup`).
  Aynı istekte hem kategori hem grup verilirse elle verilen grup kazanır.
- Grup değişiklikleri `GROUP_CHANGED` etkinliğine yazılır. Mevcut atama korunur.

### Otomatik atama (`AssignmentPicker`, saf Java)
- Modlar:
  - `NONE` (varsayılan): talep grup kuyruğunda bekler.
  - `ROUND_ROBIN`: üyelere id sırasıyla, imleçten sonraki kişiden başlayarak dağıtılır. İmleçteki kişi gruptan çıktıysa
    sıra ondan sonra gelen id ile devam eder.
  - `LEAST_LOADED`: en az açık işi olan üyeye verilir. Eşitlik olursa round-robin sırası belirler.
- Otomatik atama yalnızca **atanmamış** ve açık bir talep gruba girdiğinde çalışır. Atanmış bir talep hiçbir zaman
  başkasına geçmez.
- Aday listesi, gruptaki hâlâ personel olan üyelerdir; sonradan rolü değişen kişi atlanır.
- İmleç, grup satırı `SELECT … FOR UPDATE` ile kilitlenerek ilerletilir. Aynı anda açılan iki talep aynı sırayı almaz.
- Atamayı "system" yapar: `ASSIGNEE_CHANGED` etkinliği yazılır ve atanan kişiye `TICKET_ASSIGNED` bildirimi gider.
  Mevcut olay ve kural kullanılır, yeni bir bildirim türü eklenmedi.

### Görünürlük ve API
- Grup yönetimi yalnız MANAGER'dadır: `POST/PUT/DELETE /api/groups`, `PUT /api/groups/{id}/members`,
  `PUT /api/groups/routes`. Personel listeyi okur. Müşteri talebinde yalnızca grubun adını görür.
- İş kuyruğu: `GET /api/tickets?group=mine|<id>`. Panel: `GET /api/reports/groups` grup başına açık, atanmamış ve SLA'sı
  aşılmış iş sayılarını verir.

## Sonuçlar
- (+) Talepler doğru ekibin kuyruğuna gider. Ekip isterse dağıtım otomatik olur ve kimin aldığı kayıtlıdır.
- (+) Seçim mantığı birim testli. Yönlendirme, iki mod, yeniden yönlendirme, yetkiler ve kilitli imleç gerçek Postgres'le
  test edildi. E2E testi gerçek tarayıcıda round-robin atamayı ve bildirimi doğrular, ayarları sonunda geri alır.
- (−) Rotalar sistem geneli tek bir tablodur. Testler bu yüzden rotaları kaydedip geri yükler.
- (−) Bir üye gruptan çıkınca açık işleri başkasına dağıtılmaz. Kapasite sınırı ve mesaiye göre atama yoktur (Aşama 15
  ve sonrası).
- (−) Üyeler `app_users`'a dayanır. Hiç giriş yapmamış bir agent gruba eklenemez.
