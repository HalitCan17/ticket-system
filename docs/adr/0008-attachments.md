# ADR 0008 — Ticket ekleri

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-11
- **Aşama:** 6

## Bağlam
İş kararı: ek dosyalar yerel Docker volume'da tutulur (MinIO yok). Kapsam kararları: dosya başına **10 MB**, **güvenli tür listesi**,
**yükleyen ve yönetici silebilir**.

## Karar

### Kurallar
- Görünürlük yorumlarla aynıdır (`INTERNAL` / `EXTERNAL`). Müşteri yalnızca müşteriye açık ekleri görür ve yükler. Personel seçer;
  web'de varsayılan Dahili.
- Erişilemeyen ticket'ın ya da görünmeyen bir ekin cevabı **404**'tür (varlık sızdırılmaz). Bir ek yalnızca kendi ticket'ı
  üzerinden açılabilir.
- Silme: yükleyen kendi ekini, MANAGER her eki silebilir, diğerleri 403 alır. Kapalı ticket'a ek eklenemez ve ek silinemez (409).
- Ek yüklenince bildirim gider (`ATTACHMENT_ADDED`, yorumlarla aynı alıcılar, ADR 0007).

### Tür ve boyut (`attachment/AttachmentTypePolicy`)
- İzin verilen uzantılar: png, jpg/jpeg, gif, webp, pdf, txt, log, csv, zip, docx, xlsx, pptx. Diğerleri 400 `UNSUPPORTED_FILE_TYPE`.
- Uzantıya ek olarak **ilk baytlar** kontrol edilir: resim/PDF/zip/Office için dosya imzası, metin türlerinde NUL bayt
  olmaması. Böylece adı `.png` yapılmış bir çalıştırılabilir dosya reddedilir.
- İndirmede kullanılan içerik türünü **sunucu belirler**, istemcinin gönderdiği tür kullanılmaz. Dosya adından yol kısmı atılır,
  kontrol karakterleri reddedilir ve ad en fazla 255 karakter olur.
- 10 MB sınırı üç katmanda uygulanır: nginx `client_max_body_size 11m` (varsayılan 1m yüklemeyi bozardı), Spring multipart
  `max-file-size`, servis kontrolü (`itsm.attachments.max-file-size`). Aşıldığında ProblemDetail **413 `FILE_TOO_LARGE`** döner.
  Tomcat `max-swallow-size`, istemcinin bağlantı kopması yerine 413 cevabını almasını sağlar.

### Depolama (`FileSystemAttachmentStorage`)
- İçerik `itsm.attachments.dir` altında (Compose'ta `attachments` volume'u, `/data/attachments`) **rastgele UUID** adıyla saklanır.
  Kullanıcının dosya adı yalnızca veritabanında durur, bu yüzden dizin dışına yol kaçışı mümkün değildir. Yalnızca UUID biçimindeki
  anahtarlar açılır.
- Dosya önce geçici dosyaya yazılır, SHA-256'sı hesaplanır, sonra atomik olarak yerine taşınır. Yarım kalan yükleme dosya bırakmaz.
- Veritabanı ile tutarlılık transaction senkronizasyonuyla sağlanır. Yükleme rollback olursa dosya silinir. Silmede önce kayıt
  silinir, dosya commit'ten sonra silinir. En kötü ihtimalle sahipsiz bir dosya kalır, içeriği olmayan bir kayıt kalmaz.
- Backend imajında dizin `app` kullanıcısına ait oluşturulur. Boş adlandırılmış volume ilk bağlamada bu sahipliği alır.

### İndirme
- Her zaman `Content-Disposition: attachment; filename="<ASCII yedek>"; filename*=UTF-8''<yüzde kodlu>` (RFC 6266/5987) ile
  sunulur. Spring'in builder'ı `filename` değerini RFC 2047 ile kodladığı için başlık elle üretilir.
- `X-Content-Type-Options: nosniff` (Spring Security) ve `Cache-Control: no-store, private` gönderilir. Ek hiçbir zaman satır içi
  gösterilmez, bu yüzden uygulamanın origin'inde çalışamaz.
- Web, bearer token gerektiği için dosyayı `fetch` ile alıp geçici bir blob bağlantısıyla, özgün adıyla kaydeder.

## Sonuçlar
- (+) Dahili ekler müşteriye ne listede ne indirmede görünür (testli). Tür, boyut ve imza kontrolleri testli.
- (+) Container yeniden oluşturulduğunda dosyalar volume'da kalır (elle doğrulandı).
- (−) **Virüs taraması yok.** Dosya imzası kontrolü zararlı içeriği tespit etmez (örneğin makrolu Office dosyası). Paylaşılan bir
  ortamda ClamAV gibi bir tarayıcı eklenmeli.
- (−) Yedekleme `pgdata` ve `attachments` volume'larının **birlikte** alınmasını gerektirir.
- (−) Ticket başına toplam kota, resim önizleme ve sahipsiz dosya temizliği yok.
