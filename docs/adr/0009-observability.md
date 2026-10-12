# ADR 0009 — Loglama ve izleme

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-11
- **Aşama:** 8

## Bağlam
Sabit kararlar: log altyapısı **log4j2** (Spring Boot'un varsayılan Logback'i çıkarılır), loglama/izleme **OpenSearch 3.6.x +
OpenTelemetry**. Önceden loglar yalnızca `docker logs`'ta düz metindi. Bir isteğin nerede yavaşladığı ya da bir hatanın hangi
isteğe ait olduğu görülemiyordu.

## Karar

### log4j2
- Her starter `spring-boot-starter` üzerinden `spring-boot-starter-logging`'i (Logback) getirir. `spring-boot-starter`
  `pom.xml`'de ilk bağımlılık olarak bu hariç tutmayla tanımlanır, `spring-boot-starter-log4j2` eklenir. Kod SLF4J
  kullandığı için değişmedi. `LoggingBackendTest`, Logback'in geri gelmediğini doğrular.
- `docker` profilinde Spring Boot'un yapılandırılmış loglaması açıktır (`logging.structured.format.console: ecs`): her satır
  bir JSON nesnesidir. Yerelde okunur düz metin kalır. Ayrı bir `log4j2-spring.xml` gerekmez.
- Ticket oluşturma, statü geçişi, ek ekleme ve silme `info` seviyesinde loglanır (ticket id, kullanıcı id, statüler).
  Başlık, açıklama, yorum ve dosya adı loglanmaz, çünkü bunlar kullanıcı içeriğidir.

### OpenTelemetry
- **Java agent** (`opentelemetry-javaagent` 2.32.0) kod değişikliği olmadan HTTP isteklerini, JDBC sorgularını ve dış
  çağrıları (Keycloak JWKS) span olarak üretir. log4j2 loglarını da yakalar ve `trace_id`/`span_id` ile gönderir.
- Agent imaja build sırasında Maven Central'dan eklenir. Yalnızca Compose'ta `JAVA_TOOL_OPTIONS` ile açılır, bu yüzden testler
  ve `mvnw spring-boot:run` etkilenmez. Metrikler kapalıdır (`OTEL_METRICS_EXPORTER=none`).
- Ayrı bir OTel Collector yoktur. Agent OTLP/gRPC ile doğrudan Data Prepper'a gönderir (traces `:21890`, logs `:21892`).
  Bir servis ve bellek tasarrufu sağlanır. Gerekirse Collector araya sonradan eklenebilir.

### OpenSearch
- **Data Prepper 2.16.0**, OpenSearch Dashboards'ın Trace Analytics görünümünün beklediği biçimde yazar
  (`trace-analytics-raw` → `otel-v1-apm-span-*`, `trace-analytics-service-map`). Loglar `otel-logs-<gün>` indeksine gider.
  Pipeline'lar `infra/data-prepper/pipelines.yaml` dosyasındadır.
- **OpenSearch 3.6.0** ve **Dashboards 3.6.0** tek düğümle çalışır. Heap 512 MB, veri `osdata` volume'undadır.
- **Güvenlik eklentisi kapalıdır** (TLS ve kullanıcı yok). Bu yalnızca yerel geliştirme içindir. Portlar yalnızca
  `127.0.0.1`'e bağlanır. Paylaşılan bir ortamda güvenlik eklentisi, TLS ve Data Prepper kimlik bilgileri gerekir.
- İzleme uygulamanın çalışmasının ön koşulu değildir. Backend bu servislere `depends_on` ile bağlı değildir. Data Prepper
  kapalıyken agent veriyi düşürür ve bir hata satırı loglar, API çalışmaya devam eder (elle doğrulandı).

## Sonuçlar
- (+) Her API isteği bir trace'tir. Dashboards'ta uç nokta gecikmesi, hata oranı ve span ağacı (HTTP → SQL) görülür.
  Loglar aynı `traceId` ile bağlanır.
- (+) `docker logs` çıktısı makinece okunabilir JSON'dur.
- (−) Yığın yaklaşık 2 GB daha fazla bellek ister. İlk imaj indirmesi büyüktür.
- (−) Healthcheck istekleri (`/actuator/health/readiness`, 10 sn'de bir) de trace olarak görünür. Dashboards'ta filtrelenir.
- (−) Saklama politikası (ISM) yoktur, indeksler birikir. `down -v` ya da indeks silme ile temizlenir.
- (−) Metrikler, alarmlar ve tarayıcı (web) tarafı izleme kapsam dışıdır.
