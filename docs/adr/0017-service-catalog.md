# ADR 0017 — Servis kataloğu ve jBPM ile onay akışı

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-13
- **Aşama:** 17 (Faz 2: self-servis ve servis kataloğu)

## Bağlam
Kullanıcılar arıza bildirmenin yanında yeni bir şey de ister (dizüstü, lisans, erişim). Bu isteklerin bir kısmı
yönetici onayından geçmeli; onaylanan istek destek ekibine normal bir ticket olarak düşmeli. jBPM, Aşama 0'dan beri
yalnızca spike olarak duruyordu (`docs/jbpm-spike.md`); bu aşama onu ilk kez gerçek bir iş için kullanır.

## Karar

### Sınırlar
- Ticket statüsü yine servis katmanındadır (ADR 0002). jBPM yalnızca **istek → onay → karşılama** sürecini yönetir.
- `service_requests` satırı arayüzün okuduğu tek kaynaktır (durum, karar, notlar, ticket bağlantısı). Süreç
  değişkenleri yalnızca `requestId`, `requiresApproval`, `approved`.

### Süreç (`processes/service-request.bpmn`, id `serviceRequest`)
Başlangıç → "Onay gerekir mi?" → evet: **Approve** user task (grup `MANAGER`) → "Onaylandı mı?" → evet: **Fulfil**,
hayır: **Reject**. Onay gerekmiyorsa doğrudan **Fulfil**. Fulfil ve Reject, Spring bean'i
`ServiceRequestSteps`'i çağıran Java service task'lardır.
- Motor adımları çağıranın iş parçacığında ve transaction'ında çalıştırır (istek oluşturma ya da yöneticinin kararı).
  Bir adım hata verirse kararla birlikte geri alınır: talep "onay bekliyor"da, görev açık kalır, karar tekrar
  verilebilir (`ServiceRequestFailureIntegrationTest`).
- Kimlik motora bizim tarafımızdan (`IdentityProviders.of(user.id(), [rol])`) verilir; üretilen REST uçları kapalı
  kalır (`kogito.generate.rest=false`). Erişim `ApprovalFlow` dışında yoktur.

### Kalıcılık
- `org.kie:kie-addons-springboot-persistence-jdbc` (süreç örnekleri) ve
  `org.jbpm:jbpm-addons-springboot-usertask-storage-jpa` (görevler), 10.2.0. `kogito.persistence.type=jdbc`.
- Eklentilerin kendi Flyway çalıştırıcısı (kie-flyway) bizim Flyway'imizden önce çalışıp `public` şemasını
  doldurduğu için kapalıdır; tabloları **V14** migration'ına değiştirilmeden kopyalandı. KIE yükseltilirse
  eklentilerin yeni betikleri yeni bir migration olarak eklenmelidir.
- Görev deposu yapılandırması `org.jbpm.usertask.jpa.springboot` paketinde ve kendi `@EntityScan`'ini taşır;
  uygulama bu paketi tarar ve `@EntityScan("com.itsm.backend")` ekler (paketler birleşir).
- Backend yeniden başlatıldıktan sonra bekleyen onay listelenip tamamlanabildiği Compose'ta doğrulandı.

### Codegen ile yaşamak
- Model sınıfı (`ServiceRequestModel`) derlemeden **sonra** üretilir; ana kod onu doğrudan kullanamaz.
  `ApprovalFlow` genel `Process<? extends Model>` ve `Model.fromMap` ile çalışır.
- Üretilen service task handler'ları ayrıca argümansız kurucu çağırır; `ServiceRequestSteps`'te bu kurucu yalnızca
  derlensin diye vardır ve çağrılırsa hata fırlatır. Spring `@Autowired` kurucuyu kullanır.

### Kurallar
- Katalog öğesi: ad (büyük/küçük harf duyarsız benzersiz), açıklama, kategori ve öncelik (ticket'a geçer, grup
  yönlendirmesi mevcut kurallarla), onay gerekir mi, aktif/pasif ve en fazla 15 form alanı (`TEXT`, `TEXTAREA`,
  `NUMBER`, `SELECT`; `jsonb`). Tanım sunucuda doğrulanır (`INVALID_FORM`). Yalnızca yönetici değiştirir; müşteri
  pasif öğeyi görmez (404).
- Cevaplar öğenin alanlarına göre doğrulanır (`INVALID_ANSWERS`, alan bazlı `errors`: `answers.<key>`); bilinmeyen
  anahtarlar atılır.
- Durumlar `PENDING_APPROVAL → FULFILLING | REJECTED`, sahibi bekleyeni geri çekebilir (`CANCELLED`, süreç `abort`).
  Onay gerekmeyen istek doğrudan `FULFILLING`. Yönetici kendi isteğine karar veremez (`SELF_APPROVAL`); red için
  gerekçe zorunlu (`REASON_REQUIRED`).
- Karşılama: talep sahibi adına ticket açılır; açıklamada cevaplar, onay notu ve "Hizmet talebi #N" bulunur.
- Bildirimler artık bir ticket'a **ya da** bir hizmet talebine bağlıdır (`notifications.service_request_id`,
  CHECK ile tam biri): `APPROVAL_REQUESTED` (yöneticiler), `REQUEST_APPROVED` / `REQUEST_REJECTED` (sahip).

## Sonuçlar
- jBPM gerçek bir işte, kalıcı ve yeniden başlatmaya dayanıklı çalışıyor; spike BPMN'i ve testi kaldırıldı.
- Bir isteğin ilerleyişi (ticket'a geçtikten sonra) ticket'ta izlenir; istek sayfası bağlantı verir.
- Onaylayıcı her zaman `MANAGER` rolüdür.

## Kapsam dışı
Çok adımlı/çok onaylayıcılı zincir, öğe başına onaylayıcı grubu, maliyet/bütçe, onay zaman aşımı (jBPM timer → Jobs
Service gerekir), dosya alanı, istek üzerinde yorumlaşma, e-posta.
