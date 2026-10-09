# jBPM (Apache KIE 10.2.0) spike — Aşama 0

## Sonuç: ÇALIŞTI ✅

Apache KIE 10.2.0 jBPM, Spring Boot 3.5.16 / Java 21 backend'ine **gömülü** olarak eklendi.
Tek bir user task içeren BPMN süreci hem Java API'si (otomatik test) hem de otomatik üretilen
REST uçları (Docker Compose ile ayağa kalkan konteynerde, elle) üzerinden başlatılıp tamamlandı.
**Ayrı bir KIE Server gerekmiyor** (aşağıya bakın).

| Doğrulama | Sonuç |
|---|---|
| `./mvnw test` (smoke 2 + spike 1) | 3/3 geçti |
| `JbpmSpikeTest`: başlat → görev AGENT'a görünür, CUSTOMER'a görünmez → claim → complete(`approved=true`) → süreç COMPLETED, `approved=true` | geçti |
| Konteynerde REST: `POST /spikeApproval` → `GET /usertasks/instance` → `claim` → `complete` → instance `404` (tamamlandı) | çalıştı |
| `dependency:tree`: Spring Framework 6.2.19, Security 6.5.11, Boot 3.5.16 | hizalı (düzeltme sonrası) |
| Uygulama açılış süresi (konteyner) | ~8 sn |

## Kullanılan bağımlılıklar (Maven Central'dan doğrulandı)

```xml
<properties>
  <kie.version>10.2.0</kie.version>
</properties>

<dependencyManagement>
  <dependencies>
    <!-- Önce Spring Boot BOM: KIE BOM'u 3.5.10'u import ediyor, ilk import kazanır -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${project.parent.version}</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.kie.kogito</groupId>
      <artifactId>kogito-spring-boot-bom</artifactId>
      <version>${kie.version}</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>org.jbpm</groupId>
  <artifactId>jbpm-spring-boot-starter</artifactId>
</dependency>

<plugin>
  <groupId>org.kie.kogito</groupId>
  <artifactId>kogito-maven-plugin</artifactId>
  <version>${kie.version}</version>
  <executions><execution><goals><goal>generateModel</goal></goals></execution></executions>
</plugin>
```

Kaynaklar: `org.jbpm:jbpm-spring-boot-starter:10.2.0` POM'u, `org.kie.kogito:kogito-dependencies-bom:10.2.0`
(Spring Boot 3.5.10 / Framework 6.2.15 baseline), resmi örnek
`apache/incubator-kie-examples` @ `10.2.x` → `kogito-springboot-examples/process-springboot-example`.

Spike dosyaları:
- `backend/src/main/resources/processes/spike/spike-approval.bpmn` — Start → User Task "Review" (GroupId=`AGENT`) → End; değişkenler `ticketId: String`, `approved: Boolean`
- `backend/src/test/java/com/itsm/backend/spike/JbpmSpikeTest.java`

## Ayrı KIE Server gerekir mi? — Hayır

KIE 10.x'teki jBPM, eski 7.x'ten farklı olarak **Kogito mimarisiyle** çalışır:
- `kogito-maven-plugin`, derleme sırasında (`process-classes` fazı) BPMN'den Java kodu üretir
  (`target/generated-sources/kogito`): süreç sınıfı, model/girdi/çıktı DTO'ları, user task sınıfları,
  Spring `@Component`/`@Configuration`'lar ve REST controller'lar.
- Motor uygulamanın içinde, aynı JVM'de ve Spring context'inde çalışır. 7.x'teki gibi ayrı bir
  KIE Server / Business Central kurulumu bu hatta yoktur ve gerekmez.
- Opsiyonel ayrı servisler (Data Index, Jobs Service, Management Console) vardır; bu proje için
  şu an gerekmiyor. Zamanlayıcı (timer) kullanılırsa Jobs Service veya gömülü job add-on'u ayrıca
  değerlendirilmeli (bkz. "Sonraki adımlar").

## Karşılaşılan sorunlar ve çözümler

1. **Codegen ilk `compile`'da çalışmadı.** `generateModel` goal'unun varsayılan fazı `process-classes`
   (plugin descriptor'dan doğrulandı). `./mvnw compile` yetmez; `process-classes`, `test`, `package` yeterli.
   Sorun değil, bilgi notu.
2. **Spring sürümleri sessizce düşürüldü.** `kogito-spring-boot-bom` kendi içinde
   `spring-boot-dependencies:3.5.10`'u import ettiği için Spring Framework 6.2.19 → **6.2.15**,
   Spring Security 6.5.11 → **6.5.7** oldu (Boot 3.5.16 jar'larıyla karışık sürüm).
   **Çözüm:** `spring-boot-dependencies:${project.parent.version}` BOM'unu KIE BOM'undan önce import etmek.
3. **`No qualifying bean of type 'org.kie.kogito.app.Application'`.** Kogito, çalışma zamanı bağlantısını
   `org.kie.kogito.app` paketine üretir; `@SpringBootApplication` sadece `com.itsm.backend`'i tarıyordu.
   **Çözüm (resmi örnekteki gibi):** `@SpringBootApplication(scanBasePackages = {"com.itsm.backend", "org.kie.kogito.**"})`.
4. **Test dinleyicisi `ClassCastException`.** Motor, Spring'deki `ProcessEventListener` bean'lerini
   `KogitoProcessEventListener`'a cast ediyor. `org.kie.api...DefaultProcessEventListener` yerine
   `org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener` kullanılmalı.
5. **User task API'si 7.x'ten farklı.** 10.x'te `UserTaskService` + yaşam döngüsü geçişleri kullanılıyor
   (`DefaultUserTaskLifeCycle`, jar'dan doğrulandı): durumlar `Ready → Reserved → Completed`
   (+ `Error`, `Obsolete`), geçişler `activate`, `claim`, `release`, `complete`, `skip`, `fail`, `reassign`.
   Tamamlamak için önce `claim`, sonra `complete` + çıktı verisi.

## Diğer bulgular / riskler (sonraki aşamaları etkiler)

- ✅ ~~🔴 **Güvenlik — kimlik query parametresinden alınıyor.**~~ Üretilen REST uçları
  (`/usertasks/instance/...`, `/spikeApproval/...`) kullanıcıyı `?user=...&group=...`'tan alıyordu
  (`IdentityProviderFactory.getOrImpersonateIdentity`); herkes `group=AGENT` diyerek görev alabiliyordu.
  **Aşama 2'de çözüldü:** `kogito.generate.rest=false` (`application.properties`; kogito-maven-plugin
  `application.yml`'yi okumuyor) ile bu controller'lar artık üretilmiyor, ayrıca güvenlik zinciri `/api/**` dışındaki
  her yolu reddediyor. Süreçler kendi servis katmanımızdan Java API ile sürülecek (bkz. ADR 0004).
  Bu yüzden yukarıdaki "Konteynerde REST" doğrulaması artık tekrarlanamaz; spike testi Java API ile çalışmaya devam ediyor.
- 🟡 **Global Jackson ayarı değişiyor.** Üretilen `GlobalObjectMapper`, Spring'in ObjectMapper'ına
  `WRITE_DATES_AS_TIMESTAMPS=false`, `FAIL_ON_EMPTY_BEANS=false` ve JVM saat dilimine göre tarih formatı uyguluyor.
  Standart response formatımız tasarlanırken bu dikkate alınmalı.
- 🟡 **Starter ek bağımlılık çekiyor:** springdoc-openapi (Swagger UI `/swagger-ui/index.html` açık),
  spring-security-core, resilience4j, kogito-rest-workitem. (Aşama 2: Swagger UI güvenlik zincirinde `denyAll` ile kapalı.)
- 🟡 **Kalıcılık yok (bu spike'ta).** Süreç durumu bellekte; uygulama yeniden başlarsa açık süreçler kaybolur.
  Postgres kalıcılığı için `org.kie:kie-addons-springboot-persistence-jdbc` /
  `kie-addons-springboot-persistence-postgresql` (10.2.0, Maven Central'da mevcut) denenmeli.
- ⚪ Flyway 11.7.2 (Boot 3.5.16 yönetimli) "PostgreSQL 18.6 is newer than this version of Flyway" uyarısı veriyor;
  migration başarıyla uygulanıyor. Gerekirse PG18'i resmi destekleyen bir Flyway 11.x'e property ile yükseltilebilir.
- ⚪ Build sırasında `kogito-maven-plugin` "Log4j API could not find a logging provider" yazıyor — yalnızca
  build JVM'inde, çalışma zamanını etkilemiyor. log4j2 geçişinde tekrar bakılacak.
- ⚪ `ProcessDefinitionEventRegistry: Not source found for process id spikeApproval` uyarısı — Data Index'e
  süreç tanımı yayınlama ile ilgili, kullanmadığımız için etkisiz.
- ⚪ Host locale'i `tr_TR`. Önlem olarak Maven/test/konteyner JVM'leri `en_US` ile çalıştırılıyor; bu
  ayarlarla locale kaynaklı sorun görülmedi (ayarsız hâli ayrıca denenmedi).

## Ticket iş akışı için öneri

jBPM kullanılabilir durumda. Ancak ticket statü makinesi (NEW → IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER →
RESOLVED → CLOSED, itirazla geri dönüş, SLA duraklatma, N gün sonra otomatik kapanış) için:
- **Kaynak gerçeklik (source of truth) `tickets` tablosu** olmalı; statü kuralları servis katmanında
  test edilebilir şekilde durmalı.
- jBPM, insan görevi atama/onay gibi gerçekten süreç gerektiren yerlerde ve zamanlayıcılarda
  (otomatik kapanış) değer katar. Hangi kısmın BPMN'e gideceği Aşama 1 tasarımında kararlaştırılmalı.

## Sonraki adımlar (önerilen)
1. jBPM kalıcılığını Postgres'e bağlamak (`kie-addons-springboot-persistence-jdbc`) ve yeniden başlatma testi.
2. Üretilen REST uçlarını kapatmak, süreci kendi servisimizden sürmek.
3. Timer (otomatik CLOSED) için Jobs Service gereksinimini spike etmek.
4. Spike BPMN'i ve testi, jBPM'in gerçek bir kullanımı (ör. otomatik kapanış) gelince kaldırmak/yerine koymak.
   Not (Aşama 1): ticket statü akışı jBPM'e değil servis katmanına konuldu → `docs/adr/0002-ticket-status-flow-in-service-layer.md`.
