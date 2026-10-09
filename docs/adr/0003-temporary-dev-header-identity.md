# ADR 0003 — Keycloak'a kadar geçici header tabanlı kimlik (yalnızca `dev`)

- **Durum:** Kabul edildi (geçici; Keycloak aşamasında yerine geçecek)
- **Tarih:** 2026-10-09
- **Aşama:** 1

## Bağlam
Ticket API'si rol ve sahiplik kurallarına dayanıyor (CUSTOMER yalnızca kendi ticket'ı, INTERNAL yorumlar yalnızca
AGENT/MANAGER). Keycloak 26.8.0 sonraki bir aşamada gelecek; bu aşamada kuralları uçtan uca geliştirip test edebilmek için
isteği yapan kullanıcıyı belirlemenin bir yolu gerekiyor.

## Karar
- Controller'lar bir `CurrentUser(id, role)` parametresi alır; değeri `CurrentUserProvider` arayüzü üretir
  (`backend/src/main/java/com/itsm/backend/security/`).
- `DevHeaderCurrentUserProvider` — **yalnızca `dev` profilinde**: `X-User-Id` ve `X-User-Role` header'larını okur.
  Eksik/geçersizse 401 (`UNAUTHENTICATED`).
- `UnauthenticatedCurrentUserProvider` — `dev` dışındaki tüm profillerde: her isteği 401 ile reddeder (varsayılan güvenli).
- Docker Compose şimdilik `SPRING_PROFILES_ACTIVE=docker,dev` ile çalışır (yorumla işaretli).

## Riskler
- `dev` profilinde **herkes istediği kimliği ve rolü iddia edebilir.** Bu yalnızca yerel geliştirme içindir;
  `dev` profili hiçbir paylaşılan/yayındaki ortamda açılmamalıdır.
- jBPM'in otomatik ürettiği REST uçlarındaki `?user=&group=` impersonation sorunu bu ADR'nin kapsamında değil
  (bkz. `docs/jbpm-spike.md`); Keycloak aşamasında birlikte kapatılacak.

## Keycloak'a geçiş
1. JWT doğrulayan (Spring Security resource server) bir `CurrentUserProvider` implementasyonu eklenir:
   `id` = token `sub`, `role` = realm rolünden.
2. `DevHeaderCurrentUserProvider` ve `UnauthenticatedCurrentUserProvider` silinir, Compose'tan `dev` profili kaldırılır.
3. Controller ve servis kodu değişmez; testlerde header yerine test JWT'leri kullanılır.
