# Hardware Frontend — Teslim ve doğrulama raporu

Tarih: 2026-11-07 · Sorumlu: Alper · Genel durum: **PARTIAL**.
Bu rapor frontend uygulamasını, izole tarayıcı testlerini ve canlı API kabulünü ayrı değerlendirir.

## 1. Repo, branch ve başlangıç

- Branch: `general-features`; branch değiştirilmedi.
- Başlangıçta yalnız `docs-hardware/LTZ-Hardware-Frontend-Codex-Otonom-Gorev.md` untracked idi.
  Kullanıcının bu dosyası değiştirilmedi.
- Kök AGENTS/CLAUDE, frontend CLAUDE, Alper'in HW-01–17 görevleri, genel gereksinim DOCX'i,
  SECURITY, architecture, database, ER, folder-structure, web-rules, migration kararları,
  engineering, Hardware API kataloğu ve servis güvenlik notları incelendi.
- Java controller/DTO/service/security ve ilgili repository/migration yapıları sözleşme kaynağı alındı.
  Görevdeki eski snapshot'ın aksine güncel backend gerçek JWT ve dış politika adapter'ları içeriyor.
  Frontend `use-shell-user` ise hâlâ misafir döndürüyor.
- Dört mockup ve `ltz-hardware-service-frontend-karar.md` repoda/erişilebilir ekte bulunamadı.
  Erişilebilir ek yalnız görev metniydi. Yazılı tasarım kararları uygulandı; mockup görülmüş sayılmadı.

## 2. Sayfa bazlı sonuç

| Sayfa | Durum | Uygulanan ve doğrulanan | Canlı sınır |
| --- | --- | --- | --- |
| Bilgisayarlarım | PARTIAL | Liste/istatistik; create/edit/delete; manuel/katalog bileşen CRUD; opsiyonel marka; profil/alan/kategori gizliliği; profil ve bileşen karşılaştırma | Gerçek owner oturumuyla CRUD NOT VERIFIED |
| Donanımlar | PARTIAL | 12 kategori; arama; üretici/teknik filtreler; URL; pagination; grid/list; gerçek ürün ID'leriyle karşılaştırma | Canlı anonim GET 401; oturumlu katalog NOT VERIFIED |
| Donanım detayı | PARTIAL | UUID doğrulama; teknik tablo; kayıtlı puan özeti; yorum create/edit/delete; own liste; yabancı yorumu raporlama; ilişkili modeller | Oturumlu/audience filtreli gerçek veri NOT VERIFIED |
| Kampanyalar | PARTIAL | Mevcut editör kayıtlarının listesi/detail; para birimi/kaynak/gözlem/geçerlilik; ürün ve güvenli HTTPS kaynak bağlantıları | Forum ve admin CRUD arayüzü uygulanmadı |

P0/P1 frontend akışları izole testlerle uygulanmıştır; tüm ürünün production-ready olduğu iddia edilmez.
Otomatik tarama/snapshot gönderimi veya gerçek masaüstü istemcisi geliştirilmedi.

## 3. Gerçek endpoint bağlantıları

Tüm yollar `/api/v1/hardware` önekiyle Gateway'e gider. Ayrıntılı tüketici eşlemesi
[frontend entegrasyon rehberindedir](../../frontend/README.md#hardware-frontend-entegrasyon-rehberi).

| Yetenek | Endpoint'ler | Kanıt |
| --- | --- | --- |
| Katalog | GET `/catalog/products`, `/catalog/products/{id}`, `/catalog/brands` | DTO/service testleri, filtre ve detay browser akışı; anonim gerçek liste 401 |
| Profiller | GET/POST `/profiles`, GET/PUT/DELETE `/profiles/{id}`, GET `/profiles/statistics` | İzole CRUD ve sürüm testleri; anonim gerçek liste 401 |
| Bileşen | POST `/profiles/{id}/components`, PUT/DELETE `/profiles/{id}/components/{componentId}` | Manuel ve markasız katalog seçimi; sürüm ve silme fixture testleri |
| Gizlilik | GET/PUT `/profiles/{id}/privacy` | Doğru profileVersion ve görünürlük body testi |
| Karşılaştırma | POST `/comparisons/catalog-products`, `/comparisons/profiles`, `/comparisons/components` | Gerçek DTO biçimi; katalog ve bileşen browser akışı; profil adapter sözleşmesi |
| Değerlendirme | GET/POST `/catalog/products/{id}/reviews`, GET `/reviews/mine`, PUT/DELETE `/reviews/{id}` | İzole create/edit/delete; owner UI; backend puan özeti |
| Rapor | POST `/reviews/{id}/reports` | İzole yabancı yorum raporu; merkezi teslim başarılı gösterilmez |
| Kampanya | GET `/campaigns`, `/campaigns/{id}` | Liste/detail fixture akışı; anonim gerçek liste 401 |

Toplam 25 method/path sözleşmesi tüketilir. Gerçek Gateway'e sır/cookie taşımadan yapılan üç anonim
GET kontrolü 401 verdi. Bu, oturumlu business endpoint kabulü veya gerçek DB CRUD kanıtı değildir.

## 4. Backend ve P2 forum sınırı

Backend, migration, DB, Gateway/Auth/User veya başka servisin kaynak kodu değiştirilmedi.
API eklenmedi/değiştirilmedi; bu nedenle `docs/api.md` değiştirilmedi.
Aktif Hardware migration'ları V1–V8'dir; V9/V10 doluymuş gibi bir engel ileri sürülmedi.

Kampanya forumunun güvenilir teslimi için somut eksikler:

1. Editör kampanyalarından ayrı owner gönderisi/comment/vote entity, repository ve migration'ları.
2. Doğrulanmış aktörden sahiplik; URL/metin validation; sayfalama, filtre ve gerçek etkileşim özetleri.
3. Tekil oy/rapor, spam/rate-limit, source moderasyon/audit ve görünürlük kuralları.
4. Mevcut hesap silme fence/erasure akışına yeni verilerin katılması.
5. Bu yeni model için gerçek PostgreSQL/Flyway, IDOR, rol, eşzamanlılık ve kalite kapısı testleri.

Bu paket bu oturumda eklenmedi; mevcut editör API'si forum gibi sunulmadı.
Kampanya admin mutasyonları da güvenilir frontend rol verisi olmadan açılmadı.

## 5. Teknik kararlar ve veri doğruluğu

- Bileşen → hook → feature service → ortak apiRequest katmanı korundu; yeni bağımlılık yok.
- Java record nullability, enum, UTC tarih, para, quantity ve version alanları karşılandı.
- Marka seçimi isteğe bağlı; katalogda model yoksa manuel giriş var.
- Profile/review sürümleri backend'in beklediği query/body alanında gönderilir.
- Sorgu iptali ve eski yanıt koruması; 20 saniye timeout; aynı mutation için eşzamanlı gönderim kilidi.
- 409, ağ ve belirsiz 5xx sonucu sonrası güncel durum sorgulanır. Form girdisi korunur;
  kontrol edilmeden tekrar gönderim açılmaz. Non-idempotent POST otomatik tekrarlanmaz.
- Katalog ve kişisel veri `no-store`; HttpOnly token okunmaz/saklanmaz. Yalnız mevcut CSRF çerezi
  Gateway sözleşmesindeki başlığa aktarılır. Güvenlik modeli değiştirilmedi.
- Gizlilikte belirtilmeyen alan/kategori PRIVATE olur; profil görünürlüğünü miras almaz.
- Runtime örnek ürün, kullanıcı/avatar, fiyat, puan, FPS veya seed yoktur. Sadece test dosyalarında fixture var.
- Fotoğraf yerine kayıt yok durumu; benchmark/kazanan, stok ve fiyat geçmişi türetilmez.
- API'nin mevcut generic ProblemDetail yanıtında alan hata listesi yoktur; yerelleştirilmiş statü mesajı
  ve güvenli code korunur, sunucunun ham hata metni gösterilmez.

## 6. Navigasyon, locale ve SEO

- Donanımlar ve Kampanyalar mevcut sidebar'da gerçek rotalara bağlandı.
- Bilgisayarlarım yalnız profil dropdown'undadır; detay, gizlilik, yorum ve karşılaştırma ayrı menü değildir.
- Kampanyalar için en özel rota aktif olur; katalog ve kampanya aynı anda aktif gösterilmez.
- Sayfalar dashboard noindex alanındadır; özel veri SSR metadata/OG/sitemap'e taşınmaz.
- Dinamik ürün ID'si ve katalog query'si dil değişiminde korunur.
- Next proxy'nin next-intl rewrite'ını yeniden yönlendirip döngü oluşturması giderildi; regresyon testi var.
- Locale, root html layout'unu değiştirir. Navbar dil seçiminde belge gezinmesi yapılarak mevcut
  pre-paint tercih betiği yeniden çalıştırılır; tema korunur ve eski private client state temizlenir.

## 7. Görsel QA

- Ortak AppShell/navbar/sidebar/footer ve marka görselleri kullanıldı; globals.css paleti değiştirilmedi.
- Koyu/açık yüzeyler, semantik metin/kenar/focus tokenları; 44 px kontrol yüksekliği;
  native dialog focus/escape/scroll-lock; mobil filtre diyaloğu ve klavye geri odağı.
- Son düzenlemede mobil kategori alanı yatay şerit oldu; desktop bilgisayar özet paneli sağa alındı.
- Dört ekran için 375/768/1440 × Dark/Light × TR/EN matrisi **48 screenshot** üretir.
  `frontend/test-results/screens/hardware-{0..3}-{width}-{theme}-{locale}.png`.
- Matrisin otomatik taşma/tema/dil kontrolleri çalıştı; dört ekranı kapsayan 8 temsilî görüntü
  ayrıca araçla açılarak incelendi. Kaynak mockup bulunamadığından birebir sadakat doğrulanamadı.
- Test görüntülerindeki Fixture kayıtlar yalnız izole Playwright route yanıtlarıdır; normal UI'da yoktur.
- Dev modunda mevcut brand wordmark için LCP/eager uyarısı görüldü; ortak logo bileşeni yeniden tasarlanmadı.

## 8. Gerçek kullanılan araçlar

Sites skill'inin mevcut checkout'ta yerel geliştirme/preview doğrulama akışı kullanıldı; Site oluşturulmadı/yayımlanmadı.
PowerShell/Node, apply_patch, TypeScript/ESLint, Vitest/Testing Library, Playwright Chromium ve view_image kullanıldı.
DOCX metni .NET ZipArchive/XML ile okundu. Yeni Next dev'in ürettiği `frontend/AGENTS.md` ve yerel Next rehberleri okundu.
Yeni JSX dosyalarının biçimi, transpile edilen JavaScript tokenları aynı kalacak şekilde düzenlendi.

İstenen frontend-design, image-to-code, ui-ux-pro-max, taste, impeccable, frontend-design-review,
Context7 ve Chrome DevTools entegrasyonları erişilebilir skill/tool kataloğunda bulunmadı; kullanılmış sayılmadı.
ImageGen veya dış ürün görseli indirme gerekmedi.

## 9. Test sonuçları

Nihai sonuçlar aşağıdaki tabloda güncellenir. Build/browser komutları gerçek ENV yüklemesini kapatan
geçici `@next/env` preload'u ile çalıştırıldı; bu başlatıcı production uygulamasına eklenmedi.

| Kontrol | Sonuç |
| --- | --- |
| `npm run lint` | PASS |
| `npm run typecheck` | PASS |
| `npm run test -- --reporter=dot` | PASS — 365 test; önceden mevcut 1 skip |
| `npm run build` | PASS — nihai kaynak; ENV dosyası yüklemesi engelli |
| `npm run test:e2e -- --workers=2` | PASS — 97/97; bunun 26'sı Hardware senaryosu |
| Son birleşik filtre/aralık/geri-ileri düzeltmesi sonrası `--grep katalog` | PASS — 3/3 hedefli browser testi; ardından lint/typecheck/build PASS |
| Canlı anonim katalog/profil/kampanya GET | 3/3 beklenen 401 |
| Gerçek oturumlu owner/admin/UGC CRUD | NOT VERIFIED |
| `pre-push.cmd hardware-service` | FAIL/BLOCKED — JDK java.security erişimi; SAFE TO COMMIT yok |

Önceki başarısız browser koşuları gizlenmedi: önce Türkçe rewrite döngüsü, sonra yardımcı metinle
karışan form etiketleri, 409 sonrası formun unmount olması ve eski navbar test beklentileri bulundu.
Ürün sorunları düzeltildi; testler mevcut gerçek rota/ayar sayfası/tek tema düğmesi sözleşmesine uyarlandı.
Son kalan tema testi başlangıç sistem tercihini deterministik seçmek üzere güncellendi.
Test/coverage/security bypass, yeni skip veya disable kuralı eklenmedi.

Tam browser koşusundan sonra katalog filtre formunun kategori bilgisini koruması ve geçersiz
min/max aralığının düzeltilebilmesi güçlendirildi. İlgili katalog, katalogdan bileşen seçimi ve
empty state testleri yeniden geçti. Tam 97'li paket bu son küçük değişiklikten önce PASS'tır;
bu değişikliğin son doğrulaması hedefli üç browser testi ve nihai build'dir.

Hardware görsel matrisinde `pageerror` yok; belgede belirtilen viewport'larda yatay belge taşması yok.
Geliştirme konsolunda mevcut wordmark LCP uyarısı ve bazı ortak root-layout gezinmelerinde React
script-tag uyarısı görüldü. Bütün konsolun uyarısız olduğu iddia edilmez; gerçek mobil cihaz/ekran okuyucu
ve nicel WCAG kontrast ölçümü yapılmadı.

## 10. Teknik engeller ve doğrulama sınırları

- İlk SWC native binding denemesi sandbox kullanıcı önbelleği güven/erişim kısıtıyla başarısızdı.
  İzinli çalıştırmada tip üretimi/build/browser çalıştı; dependency veya ACL değiştirilmedi.
- Backend kalite kapısı şu hatayla başarısız oldu:
  `AccessDeniedException: ...jdk-25.0.4.1+1/conf/security/java.security`.
  Maven verify/test/coverage başarılı sayılmadı. Backend kaynak değişikliği yoktur.
- Yüklü Next runtime build çıktısında **16.4.2**, package.json/lock'ta **16.4.3** görünüyor.
  Bağımlılık dosyaları değiştirilmedi; kurulu ortam farkı raporlanıyor.
- Gerçek frontend login yok; oturumlu live CRUD, rol ve gerçek DB veri hacmi kabulü açık.
- Mockup ve ek karar dosyası bulunamadı; özgün görüntülere göre pixel-level kabul açık.

## 11. Değişen dosyalar

- Yeni route girişleri: `frontend/src/app/[locale]/(dashboard)/{hardware,profile/computers}`.
- Feature: `frontend/src/features/hardware/{components,hooks,schemas,services,types}`.
- Dar ortak entegrasyon: dashboard layout, profile-menu, language-menu, nav-item,
  navigation/pages/routing, locale proxy ve güvenli API hata kodu taşıması.
- TR/EN `Hardware` mesajları, navigation/profile/page metadata mesajları.
- Service/query/schema/UI/proxy unit testleri; Hardware browser senaryoları;
  mevcut shell/home/navbar/visual testlerinde gerçek navigasyon sözleşmesine uyum.
- `frontend/README.md` entegrasyon rehberi ve bu tek teslim raporu.
- `frontend/AGENTS.md`, `next dev` tarafından bu oturumda otomatik üretildi; kök kurallar değiştirilmedi.

## 12. Güvenlik ve Git teyidi

Git add/commit/push/merge/rebase/reset/stash/deploy yapılmadı; identity değiştirilmedi.
Başka geliştiricinin servis kaynakları, DB veya migration'ları değiştirilmedi.
ENV dosyaları değiştirilmedi, gizli değerler çıktılanmadı, token/gerçek kişisel veri koda eklenmedi.

**ENV sınırından sapma:** Standart backend pre-push komutunun mevcut `loadBackendTestEnv` yolu
`.env.dev` dosyasını test alt sürecine dolaylı yükledi. Bu çağrı, kullanıcının okuma yasağına uymadı;
tam “ENV okunmadı” teyidi verilemez. Değerler görüntülenmedi, rapora alınmadı veya değiştirilmedi.
Bu durum kullanıcıya bildirildi. Sonraki Next/build/browser doğrulamalarında ENV dosyası yüklemesi engellendi.

## 13. Manuel inceleme — en fazla beş nokta

1. Gerçek login/cookie oturumuyla bilgisayar/bileşen/gizlilik/yorum akışlarının live kabulü.
2. Gerçek katalog hacmi ve opsiyonel üretici ilişkileriyle arama/sayfalama.
3. Eksik dört mockup ile yerleşim ve görsel yoğunluk karşılaştırması.
4. Kampanya forumu domain/moderasyon/hesap silme tasarımı ve admin UI rol sözleşmesi.
5. Kurulu Next/lock sürüm farkı ve backend JDK güvenlik dosyası erişimi giderildikten sonra ekip kalite kapısı.

## 14. Önerilen commit mesajı

Yalnız öneridir; commit atılmadı.

```text
feature: HW-01–HW-16 donanım frontend akışları
- katalog, bilgisayar, bileşen, gizlilik ve yorum ekranlarını gerçek API sözleşmelerine bağla
- karşılaştırma, kampanya görünümü ve TR/EN navigasyon entegrasyonunu ekle
- frontend testlerini, responsive görsel doğrulamayı ve entegrasyon rehberini tamamla
```
