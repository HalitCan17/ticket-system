# ADR 0013 — Yönetici paneli ve ekip görünümü

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-12
- **Aşama:** 13 (rol özellikleri yol haritasının son adımı; bkz. ADR 0010–0012)

## Bağlam
Yönetici ekibin durumunu yalnızca talep listesini gezerek görebiliyordu. Önceki aşamalar gerekli veriyi zaten üretiyor:
statü, SLA saati, atama, memnuniyet puanı ve kategori. Bu aşama bu veriyi tek ekranda özetler.

## Karar

### API (yalnız MANAGER; diğer roller 403 `MANAGER_ONLY`)
- `GET /api/reports/summary?days=30` ve `GET /api/reports/team?days=30`. `days` 1 ile 365 arasında olmalıdır; web 7, 30
  ve 90 gün sunar.
- **Anlık** göstergeler dönemden bağımsızdır:
  - Açık talep (NEW, IN_PROGRESS veya WAITING_FOR_CUSTOMER)
  - Atanmamış açık talep
  - SLA'sı aşılmış açık talep (SLA ihlali zamanlayıcısıyla aynı formül)
  - Statü dağılımı
- **Dönem** göstergeleri dönem içindeki olaylara bakar:
  - Açılan talepler (`created_at`) ve kategori dağılımı.
  - Çözülen talepler (`resolved_at`; itiraz bu değeri sıfırladığı için yeniden açılan talep sayılmaz).
  - SLA uyum oranı: çözülenler içinde harcanan süre ≤ hedef olanların payı.
  - Ortalama çözüm süresi: SLA saatinin saydığı süre, yani müşteri beklemesi hariç.
  - Memnuniyet: `rated_at` dönem içinde olan puanların ortalaması, sayısı ve 1–5 dağılımı.
- **Ekip:** Bilinen agent ve yöneticilerin yanı sıra açık işi olan her atanan listelenir. Her kişi için açık iş, SLA'sı
  aşılmış açık iş, dönemde çözülen ve dönemdeki puan ortalaması gösterilir. Çözümler ve puanlar talebin (şu anki) atanan
  kişisine yazılır. Liste açık işe göre çoktan aza sıralanır.
- Hesap veritabanında yapılır (`ReportQueries`: salt-okunur SQL, `count(*) filter (...)`), talepler belleğe çekilmez.
  Bütün sorgular tek bir salt-okunur transaction'da çalışır ve aynı "şimdi"yi kullanır. Testler başka bir anı verebilir:
  `ReportService.summary(user, days, now)`.

### Web (`/dashboard`, menüde yalnız yöneticiye "Panel")
- Dönem seçici URL'de tutulur. Gösterge kutucukları vardır; açık, atanmamış ve aşılmış kutucukları talep listesinin ilgili
  görünümüne bağlantıdır.
- Dağılımlar tek renkli yatay çubuklardır. Her çubuğun yanında sayı metin olarak yazar, üzerine gelince payı (%)
  gösterilir. Seri tek olduğu için kategorik palet ve lejant gerekmez; renk, yüzeye karşı kontrast kontrolünden geçti
  (dataviz doğrulayıcısı). Ek grafik kütüphanesi yoktur.
- Ekip tablosundaki isim, o kişinin kuyruğunu açar: `/tickets?assignee=<id>&sort=sla`. Liste sayfasının kuyruk filtresi
  bu yüzden belirli bir kişiyi de kabul eder ("Kuyruk: X").

## Sonuçlar
- (+) Yönetici iş yükünü, SLA durumunu ve memnuniyeti tek ekranda görür, oradan doğrudan ilgili listeye geçer.
- (+) Rakamlar gerçek Postgres'le test edildi. Paylaşılan test veritabanında dönem rakamları "önce/sonra farkı"
  yöntemiyle, ekip satırları testin kendi agent'larıyla birebir doğrulandı. SLA aşımı gelecekteki bir anla test edildi.
- (−) Zaman serisi (günlük trend), CSV dışa aktarma ve iş günü/mesai takvimi yoktur. Süreler takvim süresidir.
- (−) Çözüm ve puan, talebin şu anki atananına yazılır. Çözümden sonra talep başkasına devredilirse rakam o kişiye geçer.
- (−) Ekip listesi `app_users`'a dayanır (bkz. ADR 0007, 0011). Hiç giriş yapmamış ve açık işi olmayan bir agent görünmez.
