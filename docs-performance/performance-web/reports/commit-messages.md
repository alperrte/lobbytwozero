# Performance Web — ayrı commit taslakları

Gerçek Git yazımı yapılmaz. TASK 1 kullanıcı tarafından commit edildi: `df308dfa`.
TASK 2 kullanıcı commit'i bekliyor; TASK 3 başlamadı.

## TASK 1 — kullanıcı tarafından commit edildi

```text
feat(performance-web): add PWEB-01–03 landing and linked device views

- PWEB-01: LTZ Performance tanıtım sayfasını Türkçe ve İngilizce rotalarla ekle; mevcut navigasyon ve sayfa kataloğuna bağla
- PWEB-02: masaüstü uygulamasının MVP sonrası durumunu açıkla; indirme alanını erişilebilir ve pasif sun
- PWEB-03: bağlı cihazları mevcut Performance API ve Auth oturumu üzerinden listele; durum, bağlanma ve son senkronizasyon zamanlarını göster
- Güvenlik: misafirlerde özel veri isteğini engelle; hesap değişiminde önceki cihaz verilerini gizle; isteklerde no-store, zaman aşımı ve iptal desteği kullan
- UI: mevcut tema tokenlarını kullan; TR/EN metinleri, yükleniyor, boş liste, HTTP hata ayrımları ve tekrar deneme akışını ekle
- Testler: cihaz durumları, hesap izolasyonu, güvenli metin gösterimi, API consumer ve navigasyon kontrollerini kapsayan hedefli koşuda 34 testi doğrula
- Dokümantasyon: TASK 1 checkpoint ve test raporunu, servis sözleşme matrisini ve açık bağımlılıkları kaydet
```

## TASK 2 — manuel commit bekleniyor

```text
feat(performance-web): implement PWEB-04–07 hardware and performance dashboard

- PWEB-04: çoklu bilgisayar, bileşen sayfalaması, kaynak ve gizlilik bilgilerini mevcut Hardware API üzerinden göster
- PWEB-05: owner oturum geçmişine sunucu sayfalaması, durum filtresi ve kullanıcı seçimiyle detay yükleme ekle
- PWEB-06: on kayıtlı metrik için zaman aralığı seçimi, sınırlı RAW/BUCKET grafik ve erişilebilir ölçüm tablosu sun
- PWEB-07: manuel oyun raporlarını çözünürlük, grafik ayarı, nullable ölçümler ve doğru donanım profiliyle görüntüle
- Entegrasyon: mevcut Auth oturumunu ve Gateway API istemcisini kullan; backend endpoint, DTO ve migration değiştirme
- Güvenlik: accountId ile özel ekranları yeniden kur, no-store/timeout/abort ve noindex uygula; eksik veriyi veya servis hatasını başarıya dönüştürme
- UI: TR/EN panel rotaları, tema uyumu, mobil grafik kaydırması ve bağımsız boş/hata/yükleniyor durumları ekle
- Testler: 1544 geçen frontend testi, 7 geçen Performance E2E ve özellik kapsamındaki yüzde 98,46 satır coverage sonucunu doğrula
- Regresyon: AppShell ve footer testlerini etkin Performans rotasıyla uyumlu hale getir; standart lint, typecheck ve production build kontrollerini geçir
- Dokümantasyon: TASK 2 checkpoint, test kanıtları, master checklist, sözleşme ve commit taslaklarını güncelle
```
