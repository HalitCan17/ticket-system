# ADR 0016 — Bilgi bankası

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 16 (Faz 2: self-servis ve servis kataloğu)

## Bağlam
Aynı sorular tekrar tekrar talep olarak açılıyordu ve çözümler yalnızca yorumların içinde kalıyordu. Personelin
çözümleri makale olarak tutması, müşterinin talep açmadan önce cevabı bulması ve personelin yorumda makaleye
bağlantı vermesi hedeflendi.

## Karar

### Model (V13)
- `kb_articles`: başlık (≤200, büyük/küçük harf duyarsız benzersiz), gövde (düz metin, ≤20000), kategori
  (`TicketCategory`), görünürlük `DRAFT | INTERNAL | PUBLIC`, oluşturan/güncelleyen, `@Version`.
- `kb_article_votes`: makale + kullanıcı birincil anahtar; "işe yaradı / yaramadı", değiştirilebilir, makale
  silinince `ON DELETE CASCADE`.
- Başlangıç verisi: üç genel makale (VPN, parola, yazıcı) ve bir dahili not; `created_by = system`.

### Görünürlük ve yetki
- Okuma: kimliği doğrulanmış herkes. Müşteri yalnız `PUBLIC` görür; diğer makale için **404** (varlık sızdırılmaz).
  Personel hepsini görür (`visibility` filtresiyle daraltabilir).
- Yazma: AGENT ve MANAGER (`STAFF_ONLY`). Silme: yalnız MANAGER (`MANAGER_ONLY`). Oy: okuyabilen herkes.
- `SecurityConfig` değişmedi; kurallar serviste (hazır yanıtlardaki gibi).

### Arama
- `q` başlıkta ve gövdede `lower(...) LIKE` ile aranır (`%`, `_`, `\` kaçışlı); başlıkta eşleşenler önce, sonra son
  güncellenen. Kategori ve görünürlük süzgeçtir. `pg_trgm`/tam metin araması eklenmedi: bu ölçekte gereksiz ve
  eklenti/dil yapılandırması bağımlılığı getirir.

### Talep entegrasyonu
- Şemaya dokunmaz. Yeni talep sayfası, başlık ≥3 karakter olunca (debounce) `PUBLIC` makaleleri önerir (≤3, yeni
  sekmede açılır). Kategori kullanılmaz: varsayılan "Diğer" doğru makaleleri eler.
- Personel yorum formunda makale seçer; başlık ve tam bağlantı yoruma eklenir. Müşteriye görünür yorumda yalnız
  `PUBLIC` makaleler, dahili yorumda `INTERNAL` da sunulur; taslaklar hiç sunulmaz.

### İçerik güvenliği
Gövde düz metindir ve `white-space: pre-wrap` ile gösterilir; HTML/Markdown işlenmez, dolayısıyla ek bir
temizleme kütüphanesi ve XSS yüzeyi yoktur.

## Sonuçlar
- Yorumdaki bağlantı düz metindir (tıklanabilir değil); kopyalanabilir ve tarayıcıya yapıştırılabilir.
- Makale sürümü/onay akışı yoktur: son kaydeden kazanır (`@Version` çakışmada 409 verir).
- İstatistik (görüntülenme) tutulmuyor; yalnız oylar var.

## Kapsam dışı
Markdown/zengin metin, makale sürümleri ve onay akışı, ek dosya, etiketler, talep ile makale arasında kalıcı bağ
("çözüm makalesi"), tam metin araması, çeviri.
