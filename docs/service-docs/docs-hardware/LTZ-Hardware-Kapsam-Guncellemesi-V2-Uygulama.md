# LTZ — Hardware Katalog + Açık Kampanya Forumu / Kapsam Güncellemesi V2

> **Tarih:** 7 Kasım 2026  
> **Sorumlu:** Alper — Hardware Service + ilişkili frontend  
> **Durum:** Kullanıcının bu oturumda verdiği **yeni ürün kararı**; önceki katalog/kampanya kararlarından çelişen kapsamlar için güncelleme talimatı.  
> **Kullanım:** Bu belgeyi `docs-hardware/LTZ-Hardware-Kapsam-Guncellemesi-V2-Uygulama.md` yoluna koy. Codex, doğrudan kodlama görevi olarak tamamını okusun.  
> **Ana kural:** Kodlar, branch ve kullanıcıya ait yerel değişiklikler bu belgeden daha güncel olabilir. Önce `AGENTS.md` ve gerçek kodu oku. Başka ekiplerin servislerine müdahale etme. Commit, push, stage yapma.

## A. Kesinleştirilen ürün kararları

1. **Donanımlar katalog sayfasında başlangıçta tam 4 kategori:** İşlemci `CPU`, Anakart `MOTHERBOARD`, RAM `RAM`, Depolama `STORAGE`.
2. `GPU`, `PSU`, çevre birimleri gibi mevcut backend `ComponentCategory` enum değerlerini **silme**. Donanım profili (`Bilgisayarlarım`) ve ilerideki kullanım korunacak. Bu dört kategori kısıtı **katalog sayfasının V1 keşif/karşılaştırma kapsamıdır**, bütün sistemin fiziksel veri modeli değildir.
3. **Dış katalog kaynağı:** Open Icecat. Hedef yalnız model/marka, güvenilir teknik özellik, izinli ürün fotoğrafı ve kaynak bilgisidir. Katalogda platform kaynaklı fiyat, stok, satıcı, indirim ya da satın al butonu OLMAYACAK.
4. **Karşılaştırma:** Aynı kategorideki iki veya daha çok modelin kategoriye özgü özelliklerini yan yana göster. Bilinmeyen veriyi `—` olarak göster. Farklı mimariler arasında salt çekirdek/hız/GB sayısına bakarak 'daha iyi' hükmü üretme; benchmark yoksa performans kazananı ilan etme.
5. **Yorumlar:** Ürünlerin altında sadece LTZ kullanıcılarına ait gerçek yorumlar, puanlar ve mevcut Hardware Review API işlemleri. Icecat içeriğini LTZ kullanıcı yorumu gibi gösterme.
6. **PSU:** Katalog kategorisi değil, bağımsız bir **dış kaynak yardım kartı**. Rehberi kendi sitemize kopyalama, içe çekme, iframe içine gömme veya 'LTZ tier list' diye yeniden markalama. Kaynak linki ve doğru atıf sun.
7. **Kampanyalar:** Donanım katalogundan tamamen bağımsız, **her kategoride ürün/fırsat paylaşılabilen topluluk forumu**. Kullanıcı başlık, açıklama, serbest kategori, varsa URL ve isteğe bağlı fiyat bilgisi paylaşır; yorumlar ve oy verir. Bir LTZ `hardware_product_id` zorunlu değil. LTZ kendisi fiyat/stok takipçisi veya alışveriş platformu değildir.
8. Navbar, sidebar, footer, global CSS renkleri, ışık/karanlık temalar, TR/EN, mevcut AppShell korunacak. Sadece gerekli Hardware menüsü, aktif rota ve kaynak bağlantısı değişecek. Kullanıcının verdiği dört mockup stil hedefidir; fiyat/stok/mağaza bileşenleri artık katalog mockup'larından kaldırılacak.
9. Mock kullanıcı/ürün/yorum/fiyat production verisine karışmayacak. Tasarım için test-only fixture kullanılabilir.

## B. Zorunlu okuma/uyumluluk

1. `AGENTS.md` → `CLAUDE.md` veya `frontend/CLAUDE.md` (varsa) → `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Alper.md` → mevcut görev/karar belgeleri.
2. `docs/SECURITY.md`, `docs/architecture.md`, `docs/folder-structure.md`, `docs/web-rules.md`, `docs/database.md`, `docs/er-diagram.md`, `docs/decisions/migrations.md`, `docs/api.md`, `docs-hardware/README.md`, `docs-hardware/decisions/HW02-catalog-campaign-proposal.md`.
3. Hardware backend controller/DTO/entity/service, migration ve testleri; mevcut frontend hardware feature, API client, i18n routing, nav config, theme ve layout'u keşfet.
4. `git status` / `git diff` ile yalnızca **salt okunur** başlangıç kontrolü yap. Yerel branch'in main'den ileride olabileceğini varsay. Yapılmış işleri silme/yeniden yazma. Kayıtlı dokümanlardaki eski kampanya kararlarının bu kullanıcı talimatıyla **neden değiştiğini** ayrı bir karar notunda belirt; eski belgeleri gizlice silme.
5. Herhangi bir erişim/permission/yeni servis sınırı çelişkisinde güvenliği gevşetmeden bağımsız alt görevleri tamamla. İzin gerektiren konuları raporla.

## C. Katalog backend: Open Icecat ingestion

> 2026-11-07 kullanıcı talimatıyla bu bölümün Icecat kaynak/import işi iptal edildi.
> Güncel aktarım kararı: [PC Part Dataset](decisions/pc-part-dataset-import.md).
> Mevcut görev frontend/forum tasarımı veya Icecat entegrasyonu içermez.

### C1. Veri sözleşmesi

Mevcut `hardware_products` modeli ve dış sistemlere açık `/api/v1/hardware/catalog/products` sözleşmesi mümkün olduğunca **geriye uyumlu** kalacak.

- `productId`, `category`, `model`, `brands`, `specifications` gibi mevcut alanları koru.
- Kaynağı belirtmek için kayıt altında en az: `sourceProvider=OPEN_ICECAT`, `externalProductId` (varsa), `brand`, `manufacturerPartNumber`/MPN (varsa), GTIN/EAN (varsa), orijinal dil, son kaynak güncelleme zamanı, son başarılı sync, görünürlük/kalite durumu.
- Çoklu görseller için en az bir doğrulanmış `primaryImage` ve varsa galeri metadata'sı; `alt` metni. Kaynağı ve izin durumunu izlenebilir tut.
- Kategoriye özgü teknik özellikler için tipli karşılaştırma alanları + genişletilebilir kaynak feature grupları. Yalnız tüm kaynağı JSON olarak atıp frontend'e yorumsuz dökme.
- `product_sources` gibi bir ilişki tablosu ile `UNIQUE(sourceProvider, externalProductId)`; ayrıca brand+MPN/GTIN çakışmalarıyla karşılaşınca otomatik overwrite yerine doğrulama kuyruğu. Aynı modelin farklı marka SKU'larını yanlış birleştirme.
- Icecat kaynak metninin kullanıcı yorumu, benchmark, fiyat veya stok olarak kullanılmasını önle.
- İçe aktarım yanlış/eksik ürün bilgisini mevcut doğrulanmış LTZ kaydı üzerine körlemesine yazmamalı. Audit ve import sonucunda eklenen/güncellenen/atlanılan/çakışan sayıları sakla.

### C2. Sadece dört kategoriye kaynak eşlemesi

- `CPU`: socket, core/thread count (varsa), base/boost frekansı, güç/TDP, cache; belirsiz birimler doğru çevrilmeli.
- `MOTHERBOARD`: socket, chipset, form factor, memory support, RAM slotları ve arayüzler (varsa).
- `RAM`: kit kapasitesi, modül sayısı (varsa), DDR tipi, MT/s veya MHz ayrımı, CL (varsa).
- `STORAGE`: SSD/HDD/NVMe tipi, kapasite, PCIe/SATA, form factor, sıralı okuma/yazma (varsa).
- Gerekli veri alanları kaynakta yoksa `null` olarak kaydet; **AI tarafından tahmin etme**.
- Aynı isimde farklı SKU/kapasite varyantlarını ayrı tut; kanıtlanmış eşdeğer ürünleri tek kimliğe bağla.

### C3. Icecat API adaptörü ve job

- Resmî API dokümanını esas al: `https://iceclog.com/manual-for-icecat-json-product-requests/`.
- API JSON: `live.icecat.biz/api`; ürün tekil sorgulanıyor, toplu keşif için INDEX gerekiyor. Kaynak indeksleri ve `daily.index.xml.gz` delta dosyasını resmî güncel dokümandan araştır, yanlış URL uydurma.
- Kaynak kategorisini Icecat taxonomy ID'leri üzerinden doğrulayarak filtrele; dört kategori dışındaki ürünleri indirme/yayınlama.
- Gece çalışan job: ilk bootstrap kontrollü ve sayfalı; sonraki günler incremental index → ilgili ürün fetch → validate → dedupe → upsert/quarantine. Manuel tetikleme yalnız yetkili bakım/geliştirme aracı üzerinden.
- Dış isteklerde timeout, retry/backoff, hız limiti, bounded batch, idempotency, log redaction, metrik, hata raporu ve durdurulabilir checkpoint kullan.
- **Icecat tokenlarını yalnız server-side kullan.** Repo içine secret, test fixture içine gerçek token veya tarayıcı bundle'ına `api-token` / `content-token` koyma.
- Kullanıcının hesabı/tokenı ve hakları doğrulanmadan canlı import tamamlandı deme. Yapılandırma yoksa import **disabled/fail-safe** kalsın; deterministik contract testleriyle adaptörü doğrula. Yeni ENV dosyası/secret oluşturma veya değiştirme için AGENTS onayı gerekiyorsa raporla.
- Open Icecat'in V1 marka-kategori kapsamını **gerçek hesapla** test et; kapsamadığı modeller için 'var' iddiasında bulunma.
- Dokümanın gerekli atfını ve AS-IS beyanını ürün detayına ekle. Kaynak: `https://iceclog.com/manual-how-to-import-product-content-into-your-webshop-via-icecat/`.
- Icecat verilerini **AI modeli eğitimi için kullanma**. Veri map/validation için AI zorunlu değil; deterministik parser öncelikli.
- Görselleri Icecat'in sürekli hotlink URL'lerinden servis etme: yetkili olduğumuz medyayı **kalıcı kendi depolamamızda** yönetmek gerekiyor. Sağlam storage entegrasyonu yoksa salt yerel development adapter + üretim için açık blokaj raporu; yeni production altyapısı/ücretli servis kararını kendiliğinden verme. Yetkisiz yüksek çözünürlüklü medya kullanma.

### C4. Gösterim API'si

- Mevcut ürün listele/detay/karşılaştırma API'lerini geliştir; **başka tüketicileri kırma**.
- `GET /api/v1/hardware/catalog/products?category=CPU&page=0&size=20` gibi mevcut filtreler korunsun.
- Gerekirse ürün görsel URL'si/metadata/typed spec alanlarını geriye uyumlu ekle.
- Liste/detay/karşılaştırma performansı, sayfalama ve N+1 kontrolü test edilsin.
- Kullanıcı katalog ekranında fiyat/stok göstermesin; backend'de mevcut eski kampanya modelleri korunabilir, kataloğa karıştırılmayacak.
- Katalog GET'in Auth şartını `AGENTS.md`, onaylı auth/gateway kuralları ve mevcut kodla teyit et. **Auth yok diye public/permitAll yapma.** Public browse isteniyorsa bu ayrı güvenlik kararıdır.

## D. Katalog frontend: yalnız 4 kategori

- Sadece `CPU`, `MOTHERBOARD`, `RAM`, `STORAGE` kategori menüsü/filter chip'i/selector'u.
- Türkçe: **İşlemci, Anakart, RAM, Depolama**; İngilizce: **Processor, Motherboard, Memory, Storage**.
- Ürün listesi: doğru marka/model, izinli birincil görsel veya kaliteli kategori fallback, gerçek teknik özet, LTZ kullanıcı puanı/yorum sayısı varsa göster. Yoksa 'Henüz değerlendirme yok' göster; 0 yıldızdan rastgele yıldız üretme.
- Ürün detayı: kategoriye göre tutarlı bölümlenmiş özellikler, kaynağı ve son güncellemesi, Icecat atfı, yorumlar.
- Katalog içi karşılaştırma: 2–4 ürünü aynı kategori içinde yan yana seç; uyuşmayan kategori karşılaştırmasına açıklayıcı uyarı.
- **Satın al, fiyat, stok, mağaza, alışveriş sepeti, indirim etiketi, kargo, satış CTA'sı yok.** Mevcut mockup fiyat alanları teknik özet / karşılaştırma / kullanıcı değerlendirmesiyle yer değiştirir.
- Katalog sayfası toolbar: arama, kategori, marka, gerçekten desteklenen specs filtreleri, grid/list ve karşılaştırma çubuğu. İstenmeyen kategorileri backend enum'dan kaldırma.
- Referans tasarımların LTZ kimliği (navbar/sidebar/footer, kompozisyon, boşluklar, kırmızı vurgu) korunmalı. Yeni tasarımı genel SaaS template'ine dönüştürme; her komponent gerçek API'ye bağlı kalmalı.
- `tr`/`en`, light/dark, 375/768/1440/1920px, keyboard, focus, skeleton/empty/error, gerçek auth 401/403 senaryolarını test et.

## E. PSU yardım kaynağı — telif ve ürün kuralı

- Bağlantı: `https://zttbuilds.com/pages/psu-tier-list`.
- Kaynak, **SPL / Sir ProvidenceLaw ve katkıda bulunan PSU uzmanlarının** Tier List'ini referans veriyor; ZTT sayfası açıklama ve yönlendirme sunuyor.
- Önerilen UI metni:
  - Başlık: `Güç kaynağı seçimi için bağımsız rehber`
  - Açıklama: `Bir güç kaynağını değerlendirirken watt ihtiyacı, bağlantılar ve bağımsız PSU tier değerlendirmeleri yararlı olabilir. SPL PSU Tier List'i dış kaynaktan inceleyebilirsin.`
  - Kaynak: `SPL PSU Tier List (Sir ProvidenceLaw ve katkıda bulunanlar) · Zach's Tech Turf üzerinden`
  - CTA: `PSU rehberini aç`.
- En uygun yer: `Bilgisayarlarım` → bileşen ekle/düzenle → **PSU/Güç Kaynağı** alanı, veya katalogdan ayrı küçük `Yardımcı Kaynaklar` öğesi; kategori chip'i olarak PSU ekleme.
- Dış sayfaya güvenli `target="_blank" rel="noopener noreferrer"` linki; üründen daha yüksek erişilebilir CTA ağırlığı verme.
- **PSU tier listesinin içeriğini kopyalama, verilerini kazıyıp modeline alma, resimlerini yeniden barındırma, iframe embed yapma, kendi karşılaştırma algoritmana izinsiz entegre etme.** Yazılı izin olmadığından bu iş sadece bağlantı/atıf.
- 'Tüm cevaplar burada', 'LTZ onaylı' veya üreticinin tavsiyesiymiş gibi kesin ifadeler kullanma. Bağımsız üçüncü taraf kaynağı olduğunu belirt. Kaynak değişirse/beklenmedik 404 dönerse link güncellenebilir.
- ZTT kullanım koşulları `https://zttbuilds.com/policies/terms-of-service` — yeniden yayımlama için önceden yazılı izin koşulu var.

## F. Kampanyalar — genel kullanıcı gönderisi forumu

### F1. Mevcut modelden ayrım

Şu anki `HardwareCampaignController` + `SaveHardwareCampaignRequest` sistemi, **yetkili katalog yöneticisi**, zorunlu `productId`, eski/yeni fiyat, geçerlilik tarihleri için yazılmıştır. Kullanıcının istediği forum **bu model değildir**.

**Eski uçları kırma veya eski migration'ı değiştirme.** Yeni kullanıcı gönderileri için ayrı bounded-context/bölüm oluştur. Önerilen servis içi path: `/api/v1/hardware/deal-posts` (nihai path'i repo API belgeleriyle uyumlu seç). Eski `/api/v1/hardware/campaigns` uçları teknik borç/deprecation olarak ayrıca belgelensin; frontend onları forum için kullanmasın.

### F2. Gönderi kuralları

- Giriş yapmış LTZ kullanıcıları paylaşım oluşturabilir.
- Her türlü **meşru ürün/hizmet fırsatı** paylaşılabilir: donanım, yazılım, oyun, telefon, giyim, market, ev, genel/diğer vb. **Hardware catalog ile bağ zorunlu değildir**.
- Minimum: başlık, açıklama ve kategori. Opsiyonel: mağaza/kaynak adı, https URL, eski fiyat, yeni fiyat, para birimi, bitiş tarihi. Fiyat yoksa da gönderi açılabilir.
- Kullanıcı verdiği fiyatları açıkça `Kullanıcı tarafından bildirildi / doğrulanmadı` olarak görür. LTZ fiyatı doğrulamadığı için otomatik yüzde/gerçek indirim iddiası yapma (gerekirse aritmetik yüzde, `kullanıcı verisine göre` ibaresiyle göster).
- Gönderinin `authorId` alanı request body'den değil **doğrulanmış principal'dan** gelir. UGC gösterim adı başka servis yoksa sahte profil üretmeden nötr şekilde gösterilir.
- Oluşturan kendi gönderisini düzenleyebilir, silebilir; yetkili moderasyon ayrı haklara tabi.
- Yorum oluşturma/listeleme/silme; en az bir seviye cevaplaşma (mevcut moderasyon altyapısıyla çakışmazsa), sayfalama.
- Kullanıcı başına gönderi başına en fazla bir oy; oy değiştirilebilir/geri alınabilir; upvote/downvote veya sadece upvote repo UX kararına göre tutarlı olsun. Bunu sessizce sayaç manipülasyonuna çevirmeyin.
- Listeleme: yeni, popüler (açık formüllü, belirli zaman pencereli), çok yorumlanan; kategori/arama filtreleri. Pagination ve stabil tie-breaker uygulanmalı.
- Kullanıcılar 'süresi doldu', 'hatalı', 'spam', 'şüpheli link' gibi gönderi raporu sunabilmeli; rapor sonrası moderasyon/inceleme, otomatik silme değil.
- XSS önleme: açıklamalar plain text veya güvenli markdown, sunucuda doğrulama; URL SSRF/open redirect/sahte şema kontrolleri, `https` kısıtı, takip bağlantılarında `rel="nofollow ugc noopener noreferrer"`; backend kullanıcı URL'sini otomatik fetch etmez.
- Limit: rate limiting, duplicate/spam, hakaret/kişisel veri/moderasyon gereklilikleri SECURITY.md ile uyumlu; içeriğin sahipliği, 401/403/404, 409 optimistic locking, audit.
- Bu sürümde platformun otomatik kampanya bulması, fiyat kontrolü, scraping, affiliate, kupon doğrulama, bildirim veya ücretli servis yok.

### F3. Önerilen veri modeli (onaylı mevcut DB yapısına göre adapte et)

- `hardware_deal_posts`: UUID PK, verified author REF, category, title, body, optional sourceName/sourceUrl, optional price data, status, created/updated/deleted timestamps, version.
- `hardware_deal_comments`: UUID PK, post FK, author REF, optional parentComment FK, text, status, timestamps, version.
- `hardware_deal_votes`: post FK, user REF, vote (ör. -1/+1), unique(post,user), created/updated.
- `hardware_deal_reports`: reporter REF, post or comment target, reason, moderation state, unique spam guard, timestamps.
- DB'de başka servisin user tablosuna FK YOK. Mevcut Hardware `@Version`, audit, `ProblemDetail` kuralları korunur.
- Kategori adları serbest kullanıcı metniyle DB schema olarak tanımlanmaz; kontrollü sınıflandırma + `OTHER` ile esnek tasarım.

### F4. Kampanyalar frontend

- Sidebar'da yalnız **Kampanyalar** bir ana bölüm/bağlantı olarak görülebilir; ürün karşılaştırmasıyla karışmaz.
- Tasarım hedefi forum/feed: üst kategori filtreleri, yeni/popüler sekmesi, gerçek gönderi listesi, yorum sayısı, oy, yeni gönderi oluştur, gönderi detay ve yorum akışı.
- 'Donanım ürünü seç' zorunlu alanı YOK. Teknoloji dışı kategori ve kaynak bağlantısı destekli.
- Gerçek oturum yoksa oluştur/oyla/yorumla eylemlerini güvenli giriş gereksinimine bağla. Boş liste halinde yaratılmış kampanya kartları değil kaliteli empty state.
- Navbar/sidebar/footer/AppShell/globals.css/TR-EN/light-dark korunur. Mobil dikey akış ve masaüstü forum kolonları referans kompozisyona uyarlanır.

## G. Migration, dokümantasyon, test ve tasarım kabulü

### G1. Sıralama

1. Repo kurallarını, **yerel çalışma ağacını** ve mevcut Hardware kodunu denetle.
2. Açık kapsam değişikliklerini `docs-hardware/decisions/` içinde **yeni** karar kaydıyla açıklığa kavuştur (eski HW02 kararı superseded, niçin).
3. Sadece frontend kategori/PSU kartı ve mevcut API ile çalışabilen işleri uygula; bağımsız testleri yaz.
4. Icecat adaptörü, typed spec/source/media modelini, importer ve job'u ekle; canlı hesabın yoksa güvenle disabled kalacak ama test edilebilir olsun.
5. UGC kampanya forumunun backend'ini ayrı entity/service/controller ile ekle, mevcut katalog kampanyası API'sini bozma.
6. Yeni forum FE'sini ve katalog karşılaştırma/detay akışını gerçek API'ye bağla.
7. Unit, MockMvc/security, repository/Testcontainers ve Playwright testlerini çalıştır; görsel karşılaştırmayı gerçek tarayıcı ile yap.

### G2. Migration bütçesi

Repo `main` kontrolünde Hardware V1–V8 mevcuttu; **yerel branch'i esas al**. Hardware için `AGENTS.md` örnekleri V1–V10 diyor; migration numarasını `docs/decisions/migrations.md` ile teyit et. Eğer V9/V10 boşsa mantıklı iki migration kapsamında:

- `V9__...`: katalog kaynak, görsel ve typed-spec genişlemeleri.
- `V10__...`: bağımsız topluluk kampanya gönderisi/yorum/oy/rapor tabloları.

Numara doluysa, yeni tahsis yoksa veya kullanıcı onayı gerekiyorsa **kendiliğinden V11 vb. uydurma**, mevcut migration'ı değiştirme. Kod/FE'nin bağımsız uygulanabilir kısmını bitir, kalan migrate bağımlılığını raporla. Flyway `clean`/DB reset YASAK.

### G3. Test kabul kriterleri

- Katalog kategorileri kullanıcıya yalnız CPU, MOTHERBOARD, RAM, STORAGE gösterir; backend enum ve Bilgisayarlarım diğer kategorileri korur.
- Katalog testi: doğru gerçek görsel/metadata, null spec fallback, stable paging/filter, aynı kategori karşılaştırması, kullanıcı yorumu, Icecat atfı, fiyat/stok yok.
- Icecat adapter testi: token gizliliği, ağ erişim hatası, rate limit/backoff, günlük delta, aynı ürün tekrar fetch edildiğinde idempotent kayıt, brand-MPN çakışması quarantine, hatalı görsel lisansı fallback.
- PSU kartı: doğru atıf, doğru URL, harici siteye bağlantı, kopyalanmış/tarayıcıdan embed edilmiş tablo bulunmaması.
- Kampanyalar testi: donanım dışı kategori kabul, `productId` ve fiyat olmadan gönderi, geçerli opsiyonel fiyat/link, yorum/oy, sahiplik kontrolleri, XSS/SSRF güvenliği, auth olmayan 401, yetkisiz 403, silinmiş içerik görünürlüğü, pagination.
- FE: `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` ve uygun Playwright; BE uygun modülde Maven test/verify. Tarayıcı kontrolü 375/768/1440/1920, TR/EN, dark/light.
- Üretimde fixture/mock veri, statik sahte kampanya, sahte user, dummy Icecat fotoğrafı yok. Test fixture'ı test klasöründedir.
- Yeni/değişen endpoint'ler `docs/api.md` Hardware bölümüne; şema değişimi hardware ER/database belgelerine; karar değişimi `docs-hardware/decisions/` dosyasına işlenir.
- `git add/commit/push/merge/reset/stash` YOK; AI co-author YOK; finale yalnız `AGENTS.md` formatında önerilen commit mesajı.

### G4. Final rapor formatı

**Tamamlandı / Kısmen / Bloke** olarak aşağıdaki durumu ayır:

1. Kodlanan backend dosyaları ve migration'lar; mevcut endpoint'lerin uyumluluğu.
2. Kodlanan frontend routes/components ve mockup ile görsel farklar.
3. Open Icecat gerçek erişim durumu, hesap/izin/asset barındırma eksikleri, kaç doğrulanmış gerçek ürün geldiği (**0 ise 0**).
4. PSU dış bağlantısı ve kaynağın atıf/yeniden yayımlama sınırı.
5. Kampanyalar tüm kategori/yorum/oy/form/URL testleri.
6. Test sonuçları ve gerçek browser ekran görüntüleri; çalıştırılmayanları PASS sayma.
7. Auth/permission/migration/ENV/başka servis entegrasyon blokajları.
8. Önceki çalışmaların korunması, herhangi bir Git yazma işlemi yapılmadığının teyidi.

## H. Doğrulanmış dış kaynaklar

- [Open Icecat ücretsiz içerik planı](https://icecat.com/content-subscription/)
- [Icecat JSON API kullanım kılavuzu (Mayıs 2026)](https://iceclog.com/manual-for-icecat-json-product-requests/)
- [Icecat indexing / daily delta / attribution kılavuzu](https://iceclog.com/manual-how-to-import-product-content-into-your-webshop-via-icecat/)
- [Open Icecat Fair Use Policy](https://iceclog.com/open-icecat-fair-use-policy/)
- [ZTT PSU sayfası — asıl SPL listesini referans gösterir](https://zttbuilds.com/pages/psu-tier-list)
- [SPL'nin kamuya açık bağlantı verilen orijinal Google Sheet'i](https://docs.google.com/spreadsheets/d/1akCHL7Vhzk_EhrpIGkz8zTEvYfLDcaSpZRB6Xt6JWkc/edit?usp=sharing)
- [ZTT Terms of Service — izinsiz yeniden yayımlama kısıtları](https://zttbuilds.com/policies/terms-of-service)

> **Dürüst durum notu:** Bu dosya **uygulama görev tanımıdır**; tek başına frontend/backend kodunun değiştirildiği veya Icecat'ten canlı veri çekildiği anlamına gelmez. Yerel Git çalışma ağacı mevcut dosyalarla Codex tarafından incelenerek uygulanmalıdır.
