# ADR 0001 — Spring Boot 3.5.x (4.x değil)

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-09
- **Aşama:** 0

## Bağlam
Backend'de iş akışı motoru olarak Apache KIE (jBPM) 10.2.0 kullanılması planlanıyor.
Maven Central'da Spring Boot'un güncel hattı 4.x (4.1.1, 4.2.0-M2) olsa da jBPM'in Spring Boot
entegrasyonu belirli bir Spring Boot sürümüne karşı derlenip test ediliyor.

Kanıt (Maven Central, `org.kie.kogito:kogito-dependencies-bom:10.2.0`):

```xml
<version.org.springframework>6.2.15</version.org.springframework>
<version.org.springframework.boot>3.5.10</version.org.springframework.boot>
```

`org.jbpm:jbpm-spring-boot-starter:10.2.0` ayrıca `resilience4j-spring-boot3` gibi
Spring Boot 3'e özgü bağımlılıklar çekiyor. Spring Boot 4 / Spring Framework 7, Jakarta EE 11 ve
çok sayıda kırıcı değişiklik getiriyor; KIE 10.2.0 bunlarla test edilmemiştir.

## Karar
- Spring Boot **3.5.x** hattında kalınır; şu an **3.5.16** (3.5 hattının en güncel yaması).
- KIE BOM'u (`kogito-spring-boot-bom`) kendi içinde Spring Boot 3.5.10 BOM'unu import ettiği için
  `backend/pom.xml`'de `spring-boot-dependencies` BOM'u, KIE BOM'undan **önce** import edilir.
  Böylece Spring Framework/Security sürümleri parent'ın yönettiği sürümlerde kalır
  (aksi hâlde 6.2.19 → 6.2.15 ve 6.5.11 → 6.5.7'ye düşüyordu; bkz. `docs/jbpm-spike.md`).

## Sonuçlar
- (+) jBPM 10.2.0 desteklenen bir kombinasyonla çalışır (Aşama 0 spike'ı ile doğrulandı).
- (−) Spring Boot 4'ün yeniliklerinden şimdilik yararlanılmaz.
- (−) 3.5 hattının açık kaynak destek süresi takip edilmeli; destek bittiğinde ya KIE'nin
  Spring Boot 4 desteği beklenir ya da iş akışı motoru kararı yeniden değerlendirilir.
- Yama yükseltmeleri (3.5.x) serbesttir; minor/major yükseltme yeni bir ADR gerektirir.
- Bilinen not: Spring Boot 3.5.16'nın yönettiği Flyway (11.7.2) PostgreSQL 18'i "test edilmemiş"
  olarak uyarı veriyor; migration'lar çalışıyor. İlgili takip `docs/jbpm-spike.md` → "Diğer bulgular".
