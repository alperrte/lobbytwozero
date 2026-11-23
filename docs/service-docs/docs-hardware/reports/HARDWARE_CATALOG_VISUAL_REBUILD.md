# Donanımlar kataloğu — görsel yenileme raporu

Tarih: 7 Kasım 2026. Sorumlu: Alper. Kapsam: yalnız katalog ve bu sayfaya ait görsel bileşenler.

Kullanıcının ikinci görselindeki katalog ana referans olarak kullanıldı. İlk görseldeki ürün detayı yalnız yüzey/marka yaklaşımı için incelendi; detay ekranı yeniden tasarlanmadı. Bu teslim, önceki genel Hardware frontend tesliminden ayrı bir görsel iyileştirmedir.

## Referans ve uygulama

| Alan | Referanstan çıkarılan oran / davranış | Uygulama |
| --- | --- | --- |
| Banner | Yaklaşık 100 px; büyük küp, solda başlık, sağda çapraz çizgiler | 100 px; mobilde 112 px; mevcut semantik renklerle LTZ çizgileri |
| Kategoriler | Yaklaşık 50 px, altı ana kategori, ikonlu aktif vurgu | 50 px; altı ana enum ve kalan altı kategori için Diğer seçimi; mobil yatay kaydırma |
| Filtre | Yaklaşık 288 px, temizleme başlıkta | 288 px; dar masaüstünde 246 px; küçük ekranda odak yönetimi olan diyalog |
| Sonuç alanı | Kompakt toolbar ve üç sütun | Arama, gerçek toplam, ızgara/liste; 1440/1672/1920'de üç sütun |
| Kart | Yaklaşık 294–300 px ve 12 px aralık | 1672'de 300 px; 132 px görsel; geniş ekranda 156 px görsel; 12 px aralık |
| Aksiyonlar | Aynı taban çizgisinde kırmızı detay / karşılaştırma | Sabit satırlar, seçili çerçeve ve checkbox; mobilde daha büyük kontroller |
| Karşılaştırma | Alt çubukta ürün adları ve küçük görseller | Yapışkan çubuk; gerçek seçimler, kategori çizimleri, tek tek kaldırma, temizleme, karşılaştırmayı açma |

Önceki küçük CPU simgesi ve büyük boş medya kutusu kaldırıldı. On iki kategoriye özel, markasız SVG çizimleri eklendi. Bunlar ürün fotoğrafı veya modele özgü render olarak sunulmaz; kartta açıkça “Kategori çizimi · ürün fotoğrafı değil” etiketi vardır. Teknik değerler yalnız API cevabından alınır. Üretici ilişkisi gelmediğinde “Üretici bilgisi belirtilmemiş” gösterilir.

Filtrelerde kategori, üretici adı/kimliği ve kategoriye uygun gerçek teknik alanlar bulunur. Minimum/maksimum doğrulaması korunur. API'de desteklenmeyen fiyat, stok, puan veya sıralama seçenekleri eklenmedi. Favori/kaydetme servisi olmadığı için çalışmayan bir buton eklenmedi.

## Değiştirilen dosyalar

Yollar depo köküne göredir. Bu liste yalnız bu görsel görevdeki değişiklikleri kapsar; önceden mevcut çalışma alanı değişiklikleri korunmuştur.

| Dosya | Değişiklik |
| --- | --- |
| `frontend/src/app/[locale]/(dashboard)/hardware/page.tsx` | Kataloğa özel genişlik; shell içinde fazladan PageContainer dolgusu kaldırıldı |
| `frontend/src/features/hardware/components/catalog-screen.tsx` | Banner, kategoriler, toolbar, grid/liste, yükleniyor/boş/hata kompozisyonu |
| `frontend/src/features/hardware/components/catalog-filters.tsx` | Kompakt filtre paneli, kategori seçimi, temizleme ve küçük üretici hata bildirimi |
| `frontend/src/features/hardware/components/catalog-product-card.tsx` | Yalnız katalogda kullanılan yoğun ürün kartı |
| `frontend/src/features/hardware/components/catalog-product-art.tsx` | On iki kategoriye özel dürüst görsel fallback |
| `frontend/src/features/hardware/components/catalog-icons.tsx` | Tutarlı ikon seti |
| `frontend/src/features/hardware/components/catalog.css` | `ltzCatalog_` önekli, katalogla sınırlı responsive stiller |
| `frontend/src/features/hardware/components/catalog-styles.ts` | Yerel sınıf kayıtları ve CSS yükleme |
| `frontend/src/features/hardware/components/catalog-comparison-bar.tsx` | Ürün bazlı alt seçim çubuğu |
| `frontend/src/features/hardware/components/comparison-provider.tsx` | Yalnız katalog rotasında yeni çubuğun gösterilmesi; mevcut karşılaştırma mantığı korunur |
| `frontend/src/shared/lib/i18n/messages/tr.json` | `Hardware.visual` Türkçe metinleri |
| `frontend/src/shared/lib/i18n/messages/en.json` | `Hardware.visual` İngilizce metinleri |
| `frontend/e2e/catalog-visual.spec.ts` | 29 yeni görsel/etkileşim/hata testi ve test fixture'ları |
| `frontend/e2e/hardware.spec.ts` | Yeni karşılaştırma düğmesi adı; fixture CORS origin'inin kullanılan sunucuya uyarlanması |
| `docs-hardware/reports/HARDWARE_CATALOG_VISUAL_REBUILD.md` | Bu rapor |

Projede yapılandırılmış Tailwind CSS yükleyicisiyle ilk CSS modülü denemesinde sınıf adları tarayıcıya uygulanmadı. Görsel kontrolde yakalanıp yalnız kataloğa özgü önekli düz CSS'e geçildi. Global CSS, tema tokenları veya Next yapılandırması değiştirilmedi. Adı geçen frontend-design/image-to-code/ui-ux-pro-max/taste-skill/impeccable/frontend-design-review becerileri oturumda kullanılabilir değildi; gerçek Playwright ve ekran görüntüsü incelemesi kullanıldı.

## API ve kapsamın korunması

- API client, Hardware servis adaptörleri, hook'lar ve DTO sözleşmeleri değiştirilmedi. Endpoint eklenmedi/değiştirilmedi; `docs/api.md` değişikliği gerekmiyor.
- Mevcut katalog araması, URL filtreleri, geri/ileri gezinme, sayfalama ve kategoriye göre teknik filtreler korunur.
- Karşılaştırma aynı kategoriden iki ürünle sınırlı mevcut davranışı sürdürür. POST `/comparisons/catalog-products` isteğindeki `leftId` / `rightId` test edildi.
- Ürün detayındaki kartlar ve diğer Hardware ekranları yeni katalog kartına geçirilmedi. Navbar/sidebar/footer/AppShell bu görevde değiştirilmedi.
- Katalog başarısız olduğunda tek ana hata alanı vardır. Üretici hatası filtre panelinde büyük ikinci kutu oluşturmaz. 401, 503, ağ kesintisi ve boş katalog ayrı davranışlardır. Yeniden deneme test edildi.
- Backend, ENV dosyaları, diğer servisler ve bağımlılıklar değiştirilmedi. Git add/commit/push yapılmadı.

## Görsel QA ve sonuçlar

Katalog ekran görüntüleri **test fixture cevabı** kullanır; canlı backend başarısı değildir. Fixture'lar yalnız `frontend/e2e/` içindedir. Uygulama kaynaklarına sahte ürün, fiyat, stok, kullanıcı veya puan eklenmedi; DB'ye kayıt atılmadı.

Viewportlar: **1672×941, 1440×940, 1920×1080, 768×1024, 375×900**. Her boyutta TR/EN ve dark/light olmak üzere 20 katalog görüntüsü alındı. Beş boyutun görüntüleri görsel olarak incelendi; masaüstü yoğunluğu, kart görselleri, buton hizası ve küçük ekran kaydırması kontrol edildi. Alt çubuğun masaüstü/mobil ve iki tema görüntüleri ayrıca incelendi. Hata görünümü de incelendi. İlk CSS yükleme ve checkbox tıklama sorunları bu döngüde düzeltilip tekrar doğrulandı. Bu süreç otomatik piksel benzerliği puanı değildir.

Ekran görüntüleri: `frontend/test-results/catalog-visual/`.

- `before-1672-dark-tr.png`: görev öncesi görünüm.
- `catalog-{width}-{dark|light}-{tr|en}.png`: 20 yeni görünüm.
- `comparison-{1672|375}-{dark|light}.png`: dört seçim çubuğu görünümü.
- `error-{401|503|network}.png`: hata görünümleri.
- `list-375.png`: mobil liste görünümü.

| Kontrol | Sonuç |
| --- | --- |
| `npm run lint` | PASS |
| `npm run typecheck` | PASS |
| `npm run test` | 365 PASS, önceden mevcut 1 SKIP |
| Tüm Playwright paketi | **126 PASS**; 29'u yeni katalog testleri |
| `npm run build` | PASS; ENV yükleme geçici Node preload ile kapatıldı |
| Kaynak kontrolü | Yeni fixture kimlikleri/model kayıtları uygulama kaynaklarında bulunmadı |

E2E mevcut `http://localhost:3001` geliştirme sunucusunu kullandı; kullanıcının sunucusu durdurulmadı. Geçici runner: `frontend/test-results/catalog-visual/playwright.config.ts`. Komut: `npm run test:e2e -- --config=test-results/catalog-visual/playwright.config.ts --workers=2`. Standart runner'ın ikinci Next dev oturumu başlatma denemesi mevcut sunucuyla çakıştığından bu yöntem kullanıldı. İlk test denemelerinde bulunan seçici/CORS/checkbox sorunları düzeltildi; son tam koşuda başarısız test yoktur.

Build, Next'in `@next/env.loadEnvConfig` çağrısını dosya yüklemeyen geçici preload ile değiştiren alt süreçte çalıştırıldı. ENV dosyaları okunmadı. Ağır root pre-push/backend kontrolü bu yalnız frontend görevinin doğrulaması olarak çalıştırılmadı; genel `SAFE TO COMMIT` veya backend PASS iddiası yoktur. Kurulu Next runtime 16.4.2'dir; bağımlılık sürümü güncellenmedi.

## Eksik alanlar ve kalan görsel farklar

Katalog Product DTO'sunda görsel URL'si, fiyat, stok, değerlendirme özeti ve favori durumu yoktur. Bu yüzden referanstaki gerçek fotoğraflar, yıldızlar, fiyat/stok satırı ve kaydetme aksiyonu bulunmaz. Bunlar geldiğinde sözleşmeye dayalı ayrı entegrasyon gerekir; mevcut çizimler bu verilere sahipmiş izlenimi vermez.

Korunan mevcut shell nedeniyle 1672 px görünümde katalog soldan yaklaşık 296 px'de başlar; referansta yaklaşık 271 px'dir. Navbar, sidebar, fontlar ve onaylı renk tokenları mevcut projeye aittir. Çapraz çizgiler CSS ile yeniden oluşturulmuştur; referansın doku/fotoğraf varlıkları birebir kopyalanmamıştır. Filtre paneli gerçek alanlara göre uzar/kısalır; sahte stok/puan ve popüler arama kutuları eklenmemiştir. On iki enumu erişilebilir tutmak için altı ana kategoriye Diğer seçimi eklenmiştir. Karşılaştırma mevcut iki ürün kuralını korur.

Bu teslim referansın temel kompozisyonunu ve yoğunluğunu uygular; fotoğraf ve veri eksikleri nedeniyle piksel düzeyinde aynı görünüm iddiası taşımaz. Canlı backend ile oturum/CRUD kabulü bu görsel görevin test sonucu olarak sunulmaz.
