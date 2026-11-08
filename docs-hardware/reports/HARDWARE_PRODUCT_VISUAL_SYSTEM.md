# Hardware MVP ürün görsel sistemi

Tarih: 2026-11-08. Kapsam yalnız Hardware frontend görselleri ve doğrulamasıdır.
Backend, migration, katalog görünürlüğü/temizliği, teknik kayıtlar ve ENV dosyaları değişmedi.
Başlangıçta mevcut katalog ekranı/CSS, query hook'u ve katalog Playwright testinde
çalışma ağacı değişiklikleri vardı; korundu. Filtre/arama/sıralama akışı değiştirilmedi.

## Uygulama

`HardwareProductVisual` kart, detay, kompakt karşılaştırma ve kategori sekmesi
boyutlarını paylaşır. `productVisual` yalnız mevcut `Product` teknik alanlarını okur.
Marka adı normal LTZ yazı tipiyle gösterilir; logo wordmark'ı taklit edilmez.
GPU kart üreticisi (MSI/Sapphire/ASRock gibi) ile çip üreticisi (NVIDIA/AMD/Intel)
ayrı alanlardır. Model adı değiştirilmez, Founders Edition gibi model türetilmez.

M.2 biçimi, 2.5 inç SATA SSD, HDD ve bilinmeyen depolama ayrı çizim seçer.
PCIe tek başına NVMe/M.2 etiketi ürettirmez; `storage-nvme.svg` M.2 silüetidir,
ekranda yalnız kayıttaki arayüz etiketi gösterilir. RAM nesli, VRAM, soket ve benzeri
etiketler `readSpec` ile gerçek API verisinden gelir; eksik değer uydurulmaz.

Özgün SVG dosyaları, `frontend/public/assets/hardware/illustrations/`:

| Dosya | Temsil |
| --- | --- |
| cpu.svg | Metal kapak, taşıyıcı ve kontaklı işlemci |
| gpu.svg | Genel ekran kartı; herhangi bir üreticinin referans tasarımı değildir |
| motherboard.svg | Soket, yuva, bağlantı ve devre detaylı anakart |
| ram.svg | Isı dağıtıcı ve kontaklı bellek modülü |
| storage-nvme.svg | M.2 biçimindeki SSD |
| storage-sata.svg | 2.5 inç SATA SSD |
| storage-hdd.svg | Plaka/okuyucu temsilli HDD |
| storage.svg | Tür/biçim kanıtı bulunmayan depolama için genel çizim |

Çizimler 640×360 viewBox, transparan zemin, nötr metal tonları ve LTZ kırmızısıyla
oluşturuldu. Fotoğraf veya üçüncü taraf çiziminden türetilmedi. Çizimdeki pin/fan/çip
sayısı seçili ürünün gerçek tasarımını ifade etmez. Kart/detayda TR/EN temsilî görsel
etiketi, karşılaştırmada açıklama, küçük görsellerde erişilebilir metin/title vardır.

Güncellenen kullanım noktaları: `catalog-screen.tsx` beş kategori sekmesi,
`catalog-product-card.tsx`, eski `product-card.tsx`, `product-detail-screen.tsx`,
`catalog-comparison-bar.tsx` ve `comparison-view.tsx`. Ortak kabuk yeniden tasarlanmadı.
`ProductSource.brand` mevcut backend yanıtına uygun opsiyonel frontend tipi olarak eklendi.
Gerçek API servisi/hook'u, ürün değerleri ve endpoint sözleşmesi değişmedi.

## Logo araştırması ve kullanım durumu

**İndirilen/kullanılan üçüncü taraf logo: 0.** Bu kullanım için açık izin
doğrulanamayan logolar yerine kullanıcının istediği tipografik badge seçeneği kullanıldı.
İzin istenmesi için kimseye mesaj/e-posta gönderilmedi. Kullanım kararı hukuki izin
verildiği iddiası değildir; aşağıdaki kaynaklar ve kapsam ayrımı esas alındı.

| Marka / kaynak | İnceleme sonucu ve uygulama |
| --- | --- |
| [AMD Brand Assets Terms](https://www.amd.com/en/legal/terms-and-conditions/media-library.html) | Lisans AMD işlemcisi içeren lisans alan ürünlerinin pazarlama/satışı gibi belirli kapsamlar tanımlar. LTZ katalog kullanımı için uygunluk kanıtlanmadı; ad badge'i. |
| [Intel logo kullanımı](https://www.intel.com/content/www/us/en/support/articles/000015080/programs.html) | Üçüncü taraf logo kullanımı için lisans/yazılı izin ister; ad badge'i. |
| [NVIDIA logo kuralları](https://www.nvidia.com/en-gb/about-nvidia/legal-info/logo-brand-usage/) | Açık yazılı yetkilendirme, marka biçimi ve ortaklık algısı kuralları vardır; logo yerine normal metinle GPU üreticisi. |
| [ASUS site şartları](https://www.asus.com/terms_of_use_notice_privacy_policy/official-site/) | Yazılı anlaşma olmadan logo hakkı verilmez; Asus adı gerçek katalog yazımıyla korunur. |
| [MSI şartları](https://us.msi.com/page/website-terms-of-use) | İçeriği yayımlama/kopyalama için yazılı onay koşulları; ad badge'i. |
| [GIGABYTE brand guidelines](https://www.gigabyte.com/About/logo-brand-usage) | Yazılı yetkilendirme ister; ad badge'i. |
| ASRock | Resmî copyright sayfasına erişim doğrulanamadı; açık logo lisansı saptanmadı, ad badge'i. |
| [Corsair şartları](https://www.corsair.com/us/en/s/terms-of-use) | Logo kullanımını sınırlar; normal metin adı kullanılır. |
| Kingston | Resmî kullanım sayfasına erişim doğrulanamadı; açık logo izni saptanmadı, ad badge'i. |
| [G.SKILL legal](https://www.gskill.com/legal) | Marka/logo için önceden yazılı rıza ister; ad badge'i. |
| [Micron/Crucial trademark guidelines](https://www.micron.com/about/press/trademark-usage-guidelines) | Marka kılavuzu incelendi; LTZ için grafik logo izni doğrulanmadı, ad badge'i. |
| [Samsung legal](https://sra.samsung.com/legal/) | Samsung isim/logo haklarını ve yazılı izin koşullarını belirtir; LTZ grafik logo izni yok, normal metin. |
| [Western Digital kullanım şartları](https://www.westerndigital.com/legal/terms-of-use), [marka kuralları](https://www.westerndigital.com/legal/trademarks) | Yetkili kullanım kapsamları/ön onay şartları vardır; LTZ için doğrulanmadı, ad badge'i. |
| Diğer katalog markaları (Sapphire, Seagate vb.) | Açık grafik logo izni envanterde yok; aynı tipografik fallback sorunsuz çalışır. |
| [Simple Icons disclaimer](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md) | CC0 paket beyanı tek başına her markanın kullanım hakkı değildir; paket/logolar indirilmedi. |

Projede mevcut LTZ marka varlıkları korunur; uygun üretici logo varlığı bulunmadı.
Kaynak/yerel varlık notu: `frontend/public/assets/hardware/README.md`.

## Gelecekte gerçek fotoğraf

Opsiyonel `approvedImage` prop'u önce incelenip yerel olarak eklenmiş
`/assets/hardware/products/<dosya>.png|jpg|jpeg|webp|avif` varlığını önceler.
Dış URL, path traversal ve SVG fotoğraf yolu kabul edilmez. Yükleme hatasında
kategori çizimine dönülür. Mevcut çağrıların tamamı yalnız SVG fallback kullanır.
Yeni fotoğraf API'si, scraping, gerçek fotoğraf indirme, servis veya DB alanı yoktur.

## Test ve görsel kanıt

Unit testler gerçek local katalogdan salt okunur alınmış 10 kaydı kullanır:
`frontend/e2e/fixtures/hardware-visual-products.json` ve kaynak notu `.md`.
CPU AMD/Intel, GPU NVIDIA/AMD/Intel, anakart, RAM, M.2/SATA SSD ve HDD örnekleri
gerçek UUID/model/marka/teknik değerlerini taşır. Fixture production koduna import edilmez.
Test-only API replay, canlı Auth/Gateway uçtan uca kabulü olarak sunulmaz.

Ortamda özel frontend-design/taste/impeccable skill'i bulunmadı; katalogda bitmap
üretimine yönelik ImageGen bu SVG işi için uygun değildi ve kullanılmadı. Uygulama
repo tasarım kuralları, yerel Next.js 16.4.3 Image rehberi ve proje Playwright'ıyla yapıldı.

Tarayıcı testleri gerçek ENV içermeyen `.cache/hardware-product-visual-qa` kopyasında,
projenin mevcut 5101 Playwright yapılandırmasıyla Chromium'da çalıştırılır.
node_modules junction hedefi için yalnız test kopyasının Turbopack root'u workspace'e
ayarlanır; uygulama config'i/portu/çalışan frontend değiştirilmez.
İlk browser koşusunda junction çözümleme hatası giderildi. Screenshot incelemesinde
karşılaştırma başlığına taşan kompakt görsel düzeltildi ve boyut regresyon testi eklendi.

Son doğrulama:

| Kontrol | Sonuç |
| --- | --- |
| `npm run lint` | PASS |
| `npm run typecheck` | PASS |
| `npm run test` | 81 dosya, 778 PASS / mevcut 1 SKIP |
| Son bileşen regresyonu | 10/10 PASS |
| `npm run build` (ENV içermeyen kopyada) | PASS; TypeScript ve 11 statik sayfa üretimi dahil |
| Playwright yeni görsel matrisi | 16/16 PASS; 4 viewport × 2 tema × 2 dil |
| Mevcut katalog seçim/karşılaştırma regresyonu | 6/6 PASS |
| Son birleşik Playwright koşusu | 22/22 PASS |
| `git diff --check` | PASS |

22 senaryoda viewport genişliği 1920/1440/768/375 olarak doğrulandı. Yeni matriste
pageerror, hydration/eksik çeviri hatası ve Hardware asset HTTP hatası 0; yatay
sayfa taşması 0. Karşılaştırma min-width tablosu kendi alanında kaydırılabilir kalır.
Eski CPU kayıtlarındaki soket etiketi, kategori çevirisi yoksa mevcut ortak `specs`
çevirisine döner. Teknik değer veya görünürlük kararı değiştirilmedi.
Next dev bazı lazy görseller için LCP öncelik önerisi verir; CWV/LCP performans ölçümü
bu koşuda yapılmadı ve PASS iddiası yoktur. Detay görseli eager, kartlar lazy yüklenir.

48 PNG `test-results/hardware-product-visual/` içine kalıcı yerel QA kanıtı olarak
kopyalandı (Git dışı). Her kombinasyonda catalog/compare/detail dosyası vardır:
`<ekran>-<1920|1440|768|375>-<dark|light>-<tr|en>.png`.
Masaüstü katalog, küçük ekran detayı ve tablet karşılaştırması görsel olarak incelendi;
CPU AMD/Intel, GPU NVIDIA/AMD/Intel, anakart, RAM, M.2/SATA SSD ve HDD çizimleri görüldü.

Örnek kanıtlar (repo köküne göre):

- [1920 dark TR katalog](../../test-results/hardware-product-visual/catalog-1920-dark-tr.png)
- [1440 light EN katalog](../../test-results/hardware-product-visual/catalog-1440-light-en.png)
- [375 light TR detay](../../test-results/hardware-product-visual/detail-375-light-tr.png)
- [768 dark EN karşılaştırma](../../test-results/hardware-product-visual/compare-768-dark-en.png)

Çalıştırılan komutlar Windows'ta `npm.cmd run ...` biçimindedir. Test logları
`test-results/hardware-visual-{lint,typecheck,tests,unit-final,browser-final,build-final}.log`
içindedir. Tüm backend/full pre-push koşusu bu frontend görevinde tekrar çalıştırılmadı;
`SAFE TO COMMIT` veya canlı Auth kabulü iddiası yoktur. Backend'e/ENV'ye dokunulmadı.
Git add/commit/push yapılmadı.
