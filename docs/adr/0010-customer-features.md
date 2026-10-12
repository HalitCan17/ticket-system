# ADR 0010 — Müşteri özellikleri: kategori, arama, geri çekme, memnuniyet puanı

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 10 (rol özellikleri yol haritasının ilk adımı: 10 müşteri, 11 iş kuyruğu ve atama, 12 hazır yanıtlar,
  13 yönetici paneli)

## Bağlam
Müşteri talep açıp takip edebiliyordu ama taleplerini sınıflandıramıyor, aramıyor, vazgeçtiği bir talebi kapatamıyor ve
çözümden memnun olup olmadığını söyleyemiyordu. Yönetici panelinin (Aşama 13) memnuniyet ortalaması için de puan gerekiyor.

## Karar

### Kategori
- `TicketCategory`: `HARDWARE`, `SOFTWARE`, `NETWORK`, `ACCESS`, `OTHER`. Talep açarken zorunludur (API'de `@NotNull`,
  web'de varsayılan "Diğer"). V7 migration'ı mevcut talepleri `OTHER` yapar.
- Kategoriyi sonradan değiştirmek personelin işidir (Aşama 11).

### Arama ve sekmeler
- `GET /api/tickets` yeni parametreler alır: `q`, `scope=OPEN|CLOSED|ALL` ve `category`. `status` parametresi aynen kalır.
  Parametre verilmezse davranış değişmez (`scope` yoksa ALL).
- `q` başlıkta büyük/küçük harf duyarsız arar. Değer bir sayıysa (`12` ya da `#12`) id ile de eşleşir. Metnin iki tarafı da
  veritabanında `lower()` ile küçültülür, böylece Türkçe İ/I aynı kuralla eşleşir. `%` ve `_` karakterleri joker değil,
  düz metin olarak aranır.
- Sorgu JPA Specification ile kurulur (`TicketSpecifications`). Müşteri için `requester_id` koşulu her zaman eklenir, bu
  yüzden arama başkasının talebini göstermez. Aşama 11 aynı yapıya atama filtrelerini ekleyecek.
- Web'de **Açık / Kapanmış / Tümü** sekmeleri vardır (varsayılan Açık). Arama 300 ms bekledikten sonra çalışır ve bütün
  filtreler URL'de tutulur.

### Talebi geri çekme
- Yeni geçiş: `NEW → CLOSED`. Bu geçişi **yalnızca talep sahibi** yapabilir ve not isteğe bağlıdır. Agent ve manager
  yapamaz: onlar için bir talebi kapatmanın yolu çözmektir.
- Bu, ADR 0002'deki statü akışına eklenen tek kuraldır. `TicketWorkflow` ve `TicketWorkflowTest`'teki spesifikasyon
  tablosu birlikte güncellendi.
- Geri çekilen talebin çözüm notu olmadığından puanlanamaz.

### Memnuniyet puanı
- Talep sahibi, **çözülmüş** bir talebe **bir kez** 1–5 puan verir; isteğe bağlı yorum en fazla 1000 karakterdir.
  "Çözülmüş", statünün `RESOLVED` ya da `CLOSED` olması ve çözüm notunun bulunması demektir.
- Kural domain'dedir (`Ticket.rate`, `Ticket.canBeRatedBy`). Hatalar: başkası 403 `RATING_NOT_ALLOWED`, ikinci kez
  409 `ALREADY_RATED`, çözülmemiş talep 409 `TICKET_NOT_RESOLVED`. Başka bir müşteri 404 alır.
- Yanıtta `rating` ve `canRate` alanları döner. Web, formu `canRate` geldiğinde gösterir ve kuralı istemcide tekrarlamaz.
- Puan verilince atanan agent'a `TICKET_RATED` bildirimi gider. Talep sahibi kendi işlemi için bildirim almaz.
- Puan, talebin satırındaki sütunlarda tutulur (`rating`, `rating_comment`, `rated_at`) çünkü bir talebin tek puanı olur.

## Sonuçlar
- (+) Müşteri taleplerini sınıflandırıp bulabiliyor, vazgeçtiği talebi kapatabiliyor ve çözüm hakkında geri bildirim
  verebiliyor. Puanlar Aşama 13'teki panelin girdisi olacak.
- (+) Tüm kurallar birim testlerle, uç durumlar ise gerçek Postgres'le entegrasyon testleriyle doğrulandı. E2E testleri
  puanlama ve geri çekme akışlarını kapsıyor.
- (−) RESOLVED durumda verilen puan, müşteri sonradan itiraz etse de kalır. Puan ilk çözüm hakkındadır ve ikinci kez
  puanlama yoktur.
- (−) Arama yalnızca başlıkta yapılır (açıklama ve yorumlar kapsam dışı). Büyük veride tam metin arama (Postgres
  `tsvector` ya da OpenSearch) gerekir.
