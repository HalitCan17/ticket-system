# ADR 0004 — Keycloak ile JWT resource server

- **Durum:** Kabul edildi (ADR 0003'ün yerini alır)
- **Tarih:** 2026-10-09
- **Aşama:** 2

## Bağlam
Aşama 1'deki geçici kimlik (`dev` profilinde `X-User-Id`/`X-User-Role` header'ı) herkesin istediği rolü iddia etmesine
izin veriyordu. Ayrıca jBPM'in ürettiği REST uçları kimliği `?user=&group=`'tan alıyordu (`docs/jbpm-spike.md`).
Sabit karar: kimlik sağlayıcı Keycloak 26.8.0, roller `CUSTOMER`, `AGENT`, `MANAGER`.

## Karar
**Keycloak (`infra/keycloak/itsm-realm.json`, Compose ve testler aynı dosyayı kullanır)**
- Realm `itsm`, realm rolleri `CUSTOMER`/`AGENT`/`MANAGER`, kendi kendine kayıt kapalı.
- `itsm-web`: public client, Authorization Code + PKCE (S256) — web arayüzü aşamasında kullanılacak.
- `itsm-cli`: public client, yalnızca password grant — **sadece yerel geliştirme** (curl, otomatik testler).
- İki client da token'a `aud=itsm-backend` ekler (audience mapper).
- Yerel geliştirme kullanıcıları `customer1`, `customer2`, `agent1`, `manager1` (şifre = kullanıcı adı).
- Keycloak verisi aynı Postgres sunucusunda ayrı `keycloak` veritabanında (`infra/postgres/init/01-keycloak.sh`).

**Backend (Spring Security OAuth2 resource server)**
- Token doğrulama: imza (realm JWKS), `iss`, `aud=itsm-backend`, süre.
- Issuer ve anahtar adresi ayrı: tarayıcı/curl token'ı `http://localhost:8081` üzerinden alır, bu yüzden `iss` her zaman
  bu adrestir (`KC_HOSTNAME`). Docker içindeki backend imza anahtarlarını `http://keycloak:8080/.../certs` adresinden çeker
  (`KEYCLOAK_JWK_SET_URI`) ama `iss`'i public adresle (`KEYCLOAK_ISSUER_URI`) karşılaştırır.
- `CurrentUser`: `id` = token `sub` (Keycloak kullanıcı UUID'si), `role` = `realm_access.roles` içindeki en yetkili uygulama
  rolü (MANAGER > AGENT > CUSTOMER). Uygulama rolü yoksa 403 `NO_ROLE`. Controller ve servis kodu değişmedi; yalnızca
  `CurrentUserProvider` implementasyonu değişti (`security/JwtCurrentUserProvider`).
- URL kuralları: `/api/**` kimlik ister, `/actuator/health/**` ve `/actuator/info` açık, **diğer her şey `denyAll`**
  (Swagger UI, jBPM vb.). Stateless; bearer token tarayıcı tarafından otomatik gönderilmediği için CSRF kapalı.
- 401/403 cevapları da ProblemDetail (`UNAUTHENTICATED`/`FORBIDDEN`) ve RFC 6750 `WWW-Authenticate` header'ı taşır.
- jBPM REST controller'ları artık üretilmiyor (`kogito.generate.rest=false`, `application.properties` — kogito-maven-plugin
  `application.yml`'yi okumuyor). Süreçler gerekirse kendi servis katmanımızdan Java API ile sürülecek.

## Sonuçlar
- (+) Kimlik ve roller Keycloak'tan, imzalı token ile geliyor; header ile rol iddiası artık mümkün değil (testlerle doğrulandı).
- (+) Audience doğrulaması sayesinde aynı realm'in başka bir client'ı için verilen token (ör. `admin-cli`) kabul edilmiyor.
- (+) `KeycloakEndToEndTest` gerçek Keycloak 26.8.0 ile token → API akışını test ediyor; diğer testler `spring-security-test`
  `jwt()` ile hızlı çalışıyor.
- (−) Ticket'larda kullanıcılar `sub` UUID'siyle tutuluyor; görünen ad (ad soyad) için bir kullanıcı dizini/önbellek
  sonraki aşamalarda gerekecek.
- (−) Keycloak `start-dev` (HTTP) ile çalışıyor; yayına alınacak bir ortamda `start` + TLS/reverse proxy ayarı gerekir.
- (−) `itsm-cli` ve sabit şifreli kullanıcılar yalnızca yerel geliştirme içindir; paylaşılan bir ortamda realm dosyası
  bunlar olmadan import edilmelidir.
