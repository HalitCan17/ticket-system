# ADR 0006 — Web uygulaması mimarisi

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-11
- **Aşama:** 4

## Bağlam
Backend iş kuralları (ticket akışı, yorumlar, SLA, otomatik kapanış) ve Keycloak ile JWT doğrulaması hazır. Kullanıcıların
tarayıcıdan giriş yapıp ticket açması, takip etmesi ve yönetmesi için bir web arayüzü gerekiyor. Sabit karar: React 19 + Vite,
React Router. Kapsam kararları: **TypeScript**, **sade CSS Modules** (UI kütüphanesi yok), ticket'larda kullanıcıların
**adıyla** görünmesi.

## Karar

### Yapı
- `web/`: React 19.3, Vite 8.3, TypeScript 7.0 (strict), React Router 8.4, TanStack Query 5 (önbellek, mutasyon sonrası yenileme),
  keycloak-js 26.2. Bağımlılıklar tam sürümle sabit.
- `src/api/`: backend DTO'larıyla birebir tipler, `createApiClient` (bearer token, ProblemDetail → `ApiError`, 401'de bir kez
  token yenileyip tekrar), `ticketsApi`.
- `src/auth/`: Keycloak girişi, rol eşleme (backend'le aynı öncelik), `AuthContext`, `AuthGate`.
- `src/pages/`, `src/components/`: bileşen başına `*.module.css`, ortak tasarım değişkenleri `styles/global.css`'te.

### Giriş (OIDC)
- `itsm-web` public client, **Authorization Code + PKCE (S256)**, `onLoad: 'login-required'`. Token'lar yalnızca bellekte
  (keycloak-js), localStorage'a yazılmaz.
- Her API çağrısından önce `updateToken(30)`; 401 gelirse token zorla yenilenip istek bir kez tekrarlanır.
- Uygulama rolü olmayan hesap için açıklayıcı mesaj + çıkış; Keycloak'a ulaşılamazsa "tekrar dene".
- Çıkışta Keycloak oturumu da sonlanır, `http://localhost:<port>/`'a dönülür (client'ın post-logout kalıbıyla eşleşir).

### Aynı origin
Tarayıcı yalnızca web sunucusuyla konuşur, `/api` istekleri proxy'lenir. Bu yüzden **backend'e CORS eklenmedi**.
- Geliştirme: `npm run dev` (5173), Vite proxy `/api` → `localhost:8080`.
- Compose: nginx (3000), statik dosyalar + SPA fallback + `/api` → `backend:8080`. `index.html` önbelleğe alınmaz,
  parmak izli `/assets` 1 yıl önbellekte kalır. Temel güvenlik başlıkları eklenir (`nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy`).
- Keycloak'un `itsm-web` client'ı iki origin'e izin verir: `localhost:5173` ve `localhost:3000`.

### Arayüz kuralları
- **Aksiyonlar yalnızca backend'in `allowedTransitions` alanından** gelir. İş kuralları istemcide tekrarlanmaz,
  arayüz yalnızca etiket ve not gereksinimini bilir.
- Çözüm notu ve itiraz gerekçesi boşken gönderilemez. Backend'in kendi doğrulaması da zaten geçerli.
- Personel yorumlarında görünürlük varsayılanı **Dahili**. Ekip notunun yanlışlıkla müşteriye gitmesi, müşteriye
  yazılacak bir yorumda bir tık fazlasından daha pahalı bir hata. Müşteri için seçim yoktur, her zaman müşteriye açık gönderilir.
- Kullanıcılar, backend'in kullanıcı rehberinden gelen adla gösterilir. Giriş yapan kullanıcı için "Siz", bilinmeyen için kısa id.
- Arayüz dili Türkçe; tarih ve saatler `tr-TR` biçiminde.

### Kullanıcı rehberi (backend, V4)
- `app_users`: her kimlikli istekte token'daki `preferred_username`, `name`, `email` bilgisi yazılır (yalnızca değiştiyse).
  Cevaplara `requesterName`, `assigneeName`, `changedByName`, `authorName` eklenir. Bir sayfa için tek sorgu atılır.
- Doğruluk kaynağı Keycloak'tır. Rehber yalnızca adı gösterme amaçlı bir kopyadır. Kullanıcı en az bir kez giriş yapmadıysa
  adı bilinmez ve arayüz kısa id gösterir.

## Sonuçlar
- (+) CORS yok, token tarayıcı deposunda değil, istemcide iş kuralı tekrarı yok.
- (+) 42 Vitest testi (API istemcisi, rozetler, aksiyonlar, yorum formu, sayfalar). Uçtan uca akış gerçek Keycloak ile
  tarayıcıda elle denendi: giriş/çıkış, ticket açma, atama, dahili/açık yorum, çözüm notu zorunluluğu, itiraz,
  başka müşterinin erişememesi.
- (−) Vite env değişkenleri build anında gömülür. Keycloak adresi değişirse imaj yeniden build edilmeli.
- (−) Tarayıcı E2E testleri otomatik değil (Playwright sonraki aşama). ESLint/Prettier henüz yok.
- (−) Realm dosyası yalnızca boş veritabanında içe aktarılır. Client ayarı değiştiği için yerelde bir kez `down -v` gerekti.
