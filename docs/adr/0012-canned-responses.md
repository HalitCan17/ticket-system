# ADR 0012 — Hazır yanıtlar

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 12 (rol özellikleri yol haritasının üçüncü adımı; bkz. ADR 0010, 0011)

## Bağlam
Agent'lar sık sorulan bilgileri ("ekran görüntüsü paylaşır mısınız?", "bilgisayarı yeniden başlatın…") her talepte elle
yazıyordu. Yönetici, ekibin ortak cevap şablonlarını tanımlayabilmeli; agent da yorum yazarken bunları seçip
kullanabilmeli.

## Karar
- `canned_responses` tablosu (V9) şu alanları tutar: **başlık** (en fazla 100 karakter, benzersiz, büyük/küçük harf
  duyarsız, `lower(title)` üzerinde unique index), **metin** (en fazla 5000 karakter) ve **önerilen görünürlük**
  (`INTERNAL`/`EXTERNAL`). Migration dört Türkçe başlangıç şablonu ekler.
- **Yetki:** Listeyi agent ve manager okur. Ekleme, düzenleme ve silmeyi yalnız manager yapar (agent 403 `MANAGER_ONLY`,
  müşteri 403). Aynı başlık 409 `TITLE_TAKEN` döner. Önce bir sorgu kontrol eder; aynı anda iki kayıt gelirse unique
  index ihlali de 409'a çevrilir.
- **Yer tutucular** istemcide doldurulur, sunucu metni yazıldığı gibi saklar:
  - `{müşteri}` → talep eden
  - `{talep}` → `#id`
  - `{ben}` → yorumu yazan personel

  Bilinmeyen yer tutucular olduğu gibi kalır. Bu sayede sunucunun bir şablon dili yorumlaması gerekmez, agent da
  gönderilecek metni olduğu gibi görür.
- **Kullanım:** Personel yorum formunda "Hazır yanıt" seçer. Doldurulmuş metin yorum kutusuna eklenir (kutu doluysa alt
  satıra) ve görünürlük önerilen değere geçer. Metin **gönderilmez**: agent düzenleyip kendisi gönderir. Görünürlük,
  gönderilmeden önce gözle kontrol edilebilir.
- Silme kalıcıdır. Şablondan yazılmış yorumlar metnin kopyasını taşır, bu yüzden silmeden etkilenmez.
- Web'de `/canned-responses` yönetim sayfası vardır ve üst menüde yalnız manager'a gösterilir. Başka bir rol bu adrese
  giderse açıklama görür, istek atılmaz.

## Sonuçlar
- (+) Tutarlı ve hızlı cevaplar. Müşteriye gidecek metni agent her zaman gönderilmeden önce görür.
- (+) Yetki ve benzersizlik kuralları gerçek Postgres'le entegrasyon testlerinde, ekleme davranışı web testlerinde ve uçtan
  uca akış E2E'de doğrulandı.
- (−) Şablonlar kategoriye göre süzülmez, kişisel (agent'a özel) şablon yoktur ve kullanım istatistiği tutulmaz.
- (−) Yer tutucu listesi sabittir. Yenisi istemcide (`placeholders.ts`) ve yönetim sayfasındaki açıklamada birlikte
  eklenmelidir.
