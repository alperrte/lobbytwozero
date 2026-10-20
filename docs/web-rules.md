# LTZ (LobbyTwoZero) — Web Tasarım ve Frontend Geliştirme Kuralları

> Tarih: 2026-10-20
>
> **Belge türü:** Kalıcı geliştirme ve tasarım kuralları. Bu belge yapılacaklar listesi, durum raporu veya özelliklerin tamamlandığına ilişkin kanıt değildir.
>
> **Kapsam:** LTZ'nin Next.js frontend'i (`frontend/`): halka açık sayfalar, oyun kataloğu, oyuncu paneli, sosyal/mesajlaşma ekranları, LTZ Performance web modülü ve yönetim arayüzleri.
>
> **İlgili belgeler:** [SECURITY.md](SECURITY.md) güvenlik, kimlik doğrulama, yetkilendirme, veri sahipliği ve sır yönetiminde **önceliklidir**. Mimari için [architecture.md](architecture.md), klasör düzeni için [folder-structure.md](folder-structure.md).

## 1. Temel ilkeler ve uygulama kapsamı

LTZ; oyuncuların oyun keşfettiği, kütüphanesini yönettiği, inceleme yazdığı, arkadaş bulduğu, mesajlaştığı, etkinliklere katıldığı ve donanım/performans verilerini takip ettiği bir topluluk platformudur. Tasarım kararları yalnız görselliğe değil **anlaşılabilirliğe, hıza, erişilebilirliğe, mahremiyete ve güvene** dayanır.

- **Tutarlılık:** Aynı anlamı taşıyan eylemler, alanlar, durumlar ve bileşenler tüm ekranlarda aynı davranır.
- **Açıklık:** Kullanıcı ne yapacağını, işlemin hangi aşamada olduğunu ve sonraki adımı kolayca anlar.
- **Responsive / mobile-friendly:** Tüm ekranlar telefon, tablet ve masaüstünde kullanılabilir (FR-UI). Veri yoğun paneller (performans grafikleri, admin tabloları) masaüstünde optimize edilir, mobilde uygun alternatif sunar.
- **Accessibility by design:** Hedef **WCAG 2.2 AA** (NFR-COMP: klavye erişimi, kontrast, etiketler).
- **Privacy by design:** Mesaj, donanım, performans, bağlı hesap ve gizli profil alanları metadata, URL, log, analitik veya paylaşım önizlemesine taşınmaz.
- **Progressive enhancement:** Temel işlev yalnız animasyona veya hover'a bağlı olmaz.
- **KISS / DRY / YAGNI:** İşlevi olmayan sayfa, sahte içerik, gereksiz paket veya PWA altyapısı yalnız bir standardı görünürde karşılamak için üretilmez.
- **Dürüst veri:** Olmayan ölçüm, puan, istatistik veya özellik varmış gibi gösterilmez (ör. tarayıcı FPS/sıcaklık ölçemez; masaüstü uygulaması MVP'de yoktur).

Koşullu kurallar yalnız ilgili özellik mevcutsa uygulanır. Hukuki yükümlülükler iş modeline göre değerlendirilir; bu belge hukuki danışmanlık yerine geçmez.

## 2. Kullanıcı yüzeylerini birbirinden ayır

Route grupları `src/app/(public)`, `(auth)`, `(dashboard)`, `(admin)` olarak ayrılmıştır. Her yüzeyin oturum, SEO ve tasarım politikası farklıdır.

| Yüzey | Örnek sayfalar | Tasarım önceliği | Arama motoru politikası |
| --- | --- | --- | --- |
| `(public)` tanıtım | Ana sayfa, hakkında, SSS, iletişim, yasal metinler | Güven, marka, dönüşüm | İndekslenebilir |
| `(public)` keşif | Oyun kataloğu/detayı, haberler, duyurular, herkese açık etkinlikler | Keşif, okunabilirlik, hızlı arama | Gerçekten herkese açıksa indekslenebilir |
| `(public)` profil | Herkese açık oyuncu profili | Kimlik, gizlilik tercihine uyum | Varsayılan `noindex`; ürün kararıyla değişir |
| `(auth)` | Giriş, kayıt, e-posta doğrulama, 2FA, şifre sıfırlama | Güvenlik, anlaşılır formlar | `noindex`, sitemap dışı |
| `(dashboard)` | Kütüphane, arkadaşlar, mesajlar, LFP, topluluk, bildirimler, ayarlar, donanım, performans, öneriler | Hız, kişisel bağlam, gizlilik | `noindex`, sitemap dışı |
| `(admin)` | Kullanıcı yönetimi, raporlar, moderasyon, içerik, istatistik, audit | Veri yoğunluğu, rol bazlı işlem, denetlenebilirlik | `noindex`, sitemap dışı |

## 3. Görsel dil ve tasarım sistemi

### 3.1 Marka kimliği ve hiyerarşi

- LTZ'nin tasarımı oyun kültürüne uygun, özgün ve profesyonel olur. Rastgele gradient, neon glow yığını, ilgisiz ikonlar ve jenerik "AI dashboard" görünümü kullanılmaz.
- Renk, tipografi, boşluk, köşe yarıçapı, gölge ve katmanlar **Tailwind 4 tasarım tokenları** (`globals.css` içindeki `@theme` / CSS değişkenleri) üzerinden yönetilir; değerler ekranlarda kopyalanmaz.
- Her ekranda ana başlık, birincil eylem ve en önemli içerik ilk bakışta anlaşılır. Eşit ağırlıklı çok sayıda CTA kullanılmaz.
- Kritik eylemlerde ikon + metin tercih edilir; yalnız ikonlu butonların erişilebilir adı bulunur.
- Oyun kapakları/medyası lisans ve kaynak bakımından uygun olmalı (sağlayıcı koşulları); placeholder görseller kalıcı içerik gibi sunulmaz.

### 3.2 Düzen, bileşenler ve tema

- Buton, form alanı, modal, toast, tablo, sekme, kart, avatar, rozet ve navigasyon kalıpları `src/shared/components/ui` altında ortak bileşen olarak oluşturulur (FR-UI ortak bileşenler). Feature'a özgü iş mantığı ortak UI katmanına taşınmaz.
- **Light/dark tema dilden bağımsızdır** (FR-UI). İki tema eşdeğer kontrast, odak ve durum görünürlüğü sağlar; sistem tercihi ve kullanıcı seçimi tutarlı yönetilir, ilk render'da tema yanıp sönmez.
- Navigasyonda aktif konum net görünür; logo uygun ana sayfaya götürür. Dashboard ile admin navigasyonu karıştırılmaz.
- Animasyonlar kısa ve amaçlıdır; `prefers-reduced-motion` dikkate alınır; otomatik ses başlatılmaz.

## 4. Sayfa davranışları ve durum yönetimi

Her veri getiren veya işlem başlatan ekranın davranışı başarısızlık durumunda da tasarlanır (FR-UI: hata/boş/yükleniyor durumları).

- **Loading:** Uygun yerde skeleton veya gösterge; skeleton gerçek yerleşimi yanıltmaz.
- **Empty:** Veri yoksa nedeni ve sonraki eylem açıklanır (ör. "Kütüphanen boş — katalogdan oyun ekle", "Bağlı cihaz yok — masaüstü uygulaması yakında").
- **Success:** Sunucu sonucu kesinleşmeden kayıt, gönderi, mesaj veya güncelleme başarılı gösterilmez. İyimser güncelleme (optimistic UI) kullanılıyorsa hata durumunda geri alınır ve kullanıcı bilgilendirilir.
- **Error:** Kullanıcı anlayacağı dilde neden ve güvenli tekrar deneme yolu. Stack trace veya sunucu ayrıntısı gösterilmez.
- **Disabled:** Pasif kontrolün nedeni anlaşılır; gönderim sırasında çift gönderim engellenir.
- **Confirmation / Undo:** Hesap silme, gönderi/inceleme silme, arkadaşlıktan çıkarma, engelleme, "herkesten sil", ilan kapatma, etkinlik iptali gibi eylemlerde açık onay. Hesap silme yeniden doğrulama + açık onay ister (AUTH-16).
- **Offline / Timeout:** Sonucu bilinmeyen mutasyon körlemesine tekrarlanmaz; durum sorgulanır veya idempotency anahtarıyla güvenli tekrar yapılır.

HTTP **401, 403, 404, 409, 429, 500, 503** tek bir genel mesaja indirgenmez. `ApiHttpError(status)` üzerinden duruma özgü mesaj ve eylem (giriş yap, yetkin yok, bulunamadı, çakışma, biraz bekle, servis geçici olarak kullanılamıyor) gösterilir.

## 5. Formlar, doğrulama ve geri bildirim

- Her alanın erişilebilir etiketi, gerekirse açıklaması ve beklenen formatı vardır. Placeholder label yerine geçmez.
- Form şemaları `features/<feature>/schemas` altında tutulur. İstemci doğrulaması UX içindir; **backend doğrulaması ve yetkilendirmesi nihai kaynaktır**. Sunucunun `ProblemDetail` alan hataları doğru alana bağlanır.
- Hatalar ilgili alanla ilişkilendirilir (`aria-describedby`), yalnız renkle anlatılmaz, ekran okuyucuya duyurulur; gönderimde ilk hatalı alana odaklanılır.
- Düzeltilebilir hatada kullanıcı girdisi silinmez; parola ve doğrulama kodu alanları güvenli yaşam döngüsüyle ele alınır.
- **6 haneli doğrulama kodu / 2FA:** tek alan veya erişilebilir çoklu kutu, yapıştırma desteği, `autocomplete="one-time-code"`, kalan deneme/süre bilgisi, yeniden gönderme için bekleme süresi.
- Uzunluk sınırı olan içeriklerde (inceleme, gönderi, ilan açıklaması, mesaj) kalan karakter sayacı erişilebilir biçimde gösterilir.
- Toast'lar anlaşılır, yeterli süre görünür ve `aria-live` ile duyurulur; kritik bilgi yalnız toast'ta bırakılmaz.

## 6. Erişilebilirlik standardı

**Hedef WCAG 2.2 AA.** Semantik HTML ve manuel testler önceliklidir; ARIA yanlış HTML'i düzeltmenin genel çözümü değildir.

### Görme ve ekran okuyucu

- Doğru `lang` (aktif locale), başlık hiyerarşisi, landmark'lar (`header`, `nav`, `main`, `footer`).
- Anlamlı görsellerde `alt`, dekoratif görsellerde boş `alt`. Oyun kapağı `alt`'ı oyun adını içerir.
- Performans grafikleri ve istatistikler için metinsel özet veya erişilebilir veri tablosu alternatifi (PWEB-06).
- Yeni mesaj, bildirim ve canlı durum güncellemeleri uygun `aria-live` politikasıyla duyurulur; mesaj akışı her yeni mesajda ekran okuyucuyu boğmaz.
- Renk, ses, hareket veya yalnız simge tek bilgi kanalı değildir (çevrimiçi durumu, okundu bilgisi, puan yıldızları metinle desteklenir).

### Klavye, motor erişim ve dokunma

- Tüm işlevler klavyeyle kullanılabilir; mantıklı sekme sırası, belirgin odak göstergesi, klavye tuzağı yok.
- Modal/dialog/emoji seçici/mesaj işlem menüsü odak yönetimi doğru; `Esc` ile kapanır. Sayfa başında "İçeriğe geç" bağlantısı.
- Dokunmatik hedefler yeterli boyut ve aralıkta; sürükle-bırak (ör. kütüphane sıralama) için alternatif eylem.
- Puanlama (REV-01) bileşeni klavye ve ekran okuyucu ile kullanılabilir (radio group semantiği).

### Bilişsel erişim ve hareket

- Tutarlı terimler, anlaşılır hata mesajları ve düzeltme yönlendirmeleri.
- Otomatik dönen içerik (öne çıkan haberler/oyunlar karuseli) durdurulabilir; yanıp sönme riski oluşturulmaz.
- Sesli mesaj (MSG-21) varsa görsel karşılık ve oynatma kontrolleri erişilebilir olur.

Lighthouse/axe sonucu tek başına yeterli değildir; klavye, ekran okuyucu ve temel akışlar manuel denenir.

## 7. LTZ'ye özgü deneyim kuralları

### 7.1 Oyun kataloğu, kütüphane ve inceleme (game / library / review)

- Katalog listeleri sayfalama veya sonsuz kaydırma + erişilebilir "daha fazla yükle" ile çalışır; arama debounce'lu, filtreler URL query'sinde tutulur (paylaşılabilir, geri/ileri çalışır).
- Oyun detayında veri kaynağı ve güncellik bilgisi (`metadataCompleteness`, sağlayıcı) gerektiğinde gösterilir; eksik detay "yükleniyor/zenginleştiriliyor" durumuyla anlatılır, uydurulmaz (GAME-17, GAME-23).
- Liste ekranlarında oyun özetleri ve puanlar **toplu (batch) API** ile alınır; kart başına ayrı istek atılmaz.
- Favori/bitirildi işaretleme oyunu otomatik olarak kütüphaneye ekler (LIB-15); UI bunu açıkça gösterir.
- Kullanıcı bir oyuna yalnız bir aktif inceleme yazabilir (REV-11); mevcut inceleme varsa "düzenle" akışı sunulur.
- Fiyat/indirim bilgisi (GAME-24) yalnız güvenilir kaynaktan ve tarihiyle gösterilir.

### 7.2 Sosyal, mesajlaşma ve LFP (social / messaging / lfp)

- Arkadaşlık isteği durumları (gönderildi, gelen, kabul, reddedildi, engelli) net ayrılır; kendine istek gönderme UI'da da engellenir.
- Engellenen kullanıcının içeriği, mesajı ve ilanı uygun şekilde gizlenir; engelleme sonrası eski önbellek verisi gösterilmez.
- Mesajlaşma: gönderiliyor/gönderildi/okundu/başarısız durumları ayrı gösterilir; başarısız mesaj için "tekrar dene" aynı `clientMessageId` ile yapılır. "Benden sil" ve "Herkesten sil" seçenekleri farklı onay metinleriyle sunulur.
- Medya yüklemeden önce istemci boyutu kontrol eder (≤ 1 MB) ama nihai kontrol sunucudadır; yükleme ilerlemesi ve iptal sunulur.
- Süresi dolan LFP ilanları "süresi doldu" olarak işaretlenir; ilan sahibiyle iletişim mesajlaşma ekranına yönlendirir.

### 7.3 Topluluk, içerik ve etkinlik (community / content / event)

- Repost orijinal yazarı ve bağlantıyı gösterir. Moderasyonla kaldırılmış içerik açıklayıcı bir yer tutucuyla gösterilir.
- Resmi duyurular (admin) kullanıcı içeriğinden görsel olarak ayrılır.
- Etkinlik durumları (`PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED`), kalan kontenjan ve katılım cezası (EVT-10) açıkça gösterilir. Katılım iptali öncesi ceza kuralı onay penceresinde anlatılır.
- Tarih/saat kullanıcının yerel saat diliminde gösterilir; backend UTC saklar.

### 7.4 LTZ Performance web modülü (hardware / performance / recommendation)

- Performance ana sayfası (PWEB-01) mevcut yetenekleri dürüstçe anlatır. **Masaüstü uygulaması indirme alanı (PWEB-02) MVP'de pasif/duyuru durumundadır**; yayımlanmamış dosyaya bağlantı verilmez.
- Cihaz yönetimi (PWEB-03) bağlı cihaz yokken anlamlı boş durum gösterir.
- Manuel girilen veri ile senkronize/masaüstü verisi görsel olarak ayırt edilir (kaynak etiketi).
- Grafikler (PWEB-06) eksen etiketleri, birimler (°C, %, FPS, MB), zaman aralığı seçimi ve metinsel özetle sunulur; veri yoksa sahte eğri çizilmez.
- Donanım uyumluluğu ve öneriler (PWEB-08/09) **neden** önerildiğini gösterir (REC-17) ve geri bildirim alır (REC-19). Tahmini FPS "tahmin" olarak etiketlenir.
- Gizlilik ve veri yönetimi (PWEB-10): görünürlük ayarları, veri dışa aktarma/silme eylemleri açık ve onaylıdır.

### 7.5 Bildirimler

- Okunmamış sayacı, tümünü okundu işaretle, tercih ayarları (FR-NOT). Bildirim tıklaması ilgili kaynağa gider; kaynak silinmiş/yetkisizse uygun mesaj gösterilir.

## 8. Yönetim (admin) arayüzü

- Admin tabloları erişilebilir başlıklar, filtre, sıralama, sayfalama ve boş/yükleniyor/hata durumlarıyla tasarlanır. Mobilde kullanılamayan geniş tablo yerine uygun alternatif sunulur.
- Askıya alma, yasaklama, içerik kaldırma ve rapor sonuçlandırma işlemleri **gerekçe alanı** ve onay ister; sonuç audit kaydına bağlanır (FR-ADM).
- Admin yetkileri rol bazında ayrıdır; yetkisi olmayan eylem UI'da gizlenir **ve** backend reddeder.
- Admin ekranı URL'sinin gizli olması güvenlik önlemi değildir (bkz. SECURITY.md §4).

## 9. Next.js 16 ve frontend kod mimarisi

- **Next.js App Router + TypeScript + Tailwind 4 + feature-based** düzen korunur. İş mantığı `src/features/<feature>/` (`components`, `hooks`, `schemas`, `services`, `types`) içinde; tekrar kullanılan kod `src/shared/` içinde. Ayrıntı: [folder-structure.md](folder-structure.md).
- Feature adları backend servisleriyle eşleşir: `admin, auth, community, content, event, game, hardware, lfp, library, messaging, notification, performance, recommendation, review, social, user`.
- `src/app` route, layout, metadata, loading/error boundary ve sayfa birleştirme katmanıdır; iş mantığı burada biriktirilmez.
- **Bileşen içinde doğrudan API çağrısı yapılmaz** (NFR-MAINT). Akış: `component → hook → features/<x>/services → shared/lib/api/client.ts (apiRequest)`.
- Tüm HTTP istekleri `apiRequest<T>()` üzerinden Gateway'e (`NEXT_PUBLIC_API_BASE_URL`, varsayılan `http://localhost:5080`) gider; path `/api/v1/<servis>/...` biçimindedir. Servis portlarına doğrudan istek atılmaz.
- Bir feature başka feature'ın iç dosyalarını import etmez; paylaşılması gereken tip/bileşen `shared`'a taşınır veya feature'ın açık export'u üzerinden kullanılır.
- Server/Client Component sınırları bilinçli kullanılır; gerekmedikçe `"use client"` yapılmaz. Sunucu sırları client bundle'a geçmez. React Compiler (`babel-plugin-react-compiler`) açık olduğundan gereksiz manuel `useMemo/useCallback` eklenmez.
- `reactStrictMode: true` korunur. `not-found.tsx`, `error.tsx`, `loading.tsx` mevcuttur; route gruplarına özel sınırlar ihtiyaçla eklenir. Hata ekranında teknik exception gösterilmez.
- Route slug'ları kararlı, okunabilir ve benzersizdir; Türkçe karakter transliterasyonu tutarlıdır. Deep-link yenileme, geri/ileri ve query filtreleri çalışır.

## 10. SEO, URL ve sosyal paylaşım

- SEO yalnız herkese açık ve **indekslenmesi istenen** sayfalarda uygulanır (tanıtım, oyun detayı, haber, herkese açık etkinlik).
- İndekslenebilir her sayfa özgün `title`, `description`, canonical URL ve anlamlı iç bağlantılar taşır (Metadata API, `generateMetadata`).
- `robots.ts` ile `/robots.txt` yönetilir. `(auth)`, `(dashboard)`, `(admin)` ve gizli profiller `noindex`'tir. **`robots.txt` erişim kontrolü değildir.**
- `sitemap.ts` yalnız gerçekten indekslenecek public sayfalar varsa eklenir; özel/oturumlu URL içermez.
- Dil cookie tabanlıdır (`locale` cookie); ayrı `/tr`, `/en` URL'leri yoksa `hreflang` uydurulmaz. URL tabanlı locale ancak ürün kararıyla eklenir.
- JSON-LD (`VideoGame`, `NewsArticle`, `Event` vb.) yalnız görünür ve doğrulanmış veriyle tutarlıysa eklenir; puan/yorum uydurulmaz.
- OpenGraph/X kartları yalnız public içerikten üretilir; mesaj, özel profil, donanım/performans verisi önizlemeye girmez. OG görselleri mutlak HTTPS URL.
- Open redirect ve kontrolsüz dış bağlantı engellenir; dış linkler `rel="noopener noreferrer"`.

## 11. Footer, kurumsal içerik ve destek

- Public sayfalarda marka/telif, Hakkımızda, SSS, iletişim/destek, gizlilik/KVKK, kullanım koşulları, çerez politikası, topluluk kuralları ve erişilebilirlik bildirimi erişilebilir footer'da bulunur.
- Olmayan sosyal hesap, lisans veya iletişim kanalı uydurulmaz.
- Steam, Discord ve diğer marka adları/logoları ilgili marka kurallarına uygun kullanılır; LTZ'nin resmi ortaklığı varmış gibi sunulmaz.

## 12. Hukuki uyum, çerezler ve veri mahremiyeti

- KVKK aydınlatması; veri sorumlusu, amaç, hukuki sebep, aktarım (ör. harici sağlayıcılar), saklama ve başvuru yollarını doğru anlatır. Gizlilik ve kullanım koşulları tutarlı ve günceldir.
- Açık rıza gereken durumlar (ör. bağlı hesap verisi, isteğe bağlı analitik, otomatik donanım senkronizasyonu) aydınlatmadan ayrı, özgür ve geri alınabilir olur. Ret seçeneği gizlenmez; yanıltıcı onay tasarımı (dark pattern) kullanılmaz.
- Çerezler kategori bazında envanterlenir; zorunlu oturum/locale/tema çerezleri ile isteğe bağlı çerezler ayrılır. Kullanılmayan analitik için sahte consent banner üretilmez.
- Kullanıcı içerikleri (UGC) için topluluk kuralları ve raporlama yolu görünür. Medya yükleme ekranında telif uyarısı bulunur.
- Hesap silme ve veri dışa aktarma talepleri ayarlar sayfasından erişilebilir.

## 13. Oturum, güvenlik ve özel veri gösterimi

- Giriş/kayıt/doğrulama/2FA/Steam-Google ile giriş/şifre sıfırlama akışları gerçek auth-service sözleşmesine uyar; uygulanmamış auth davranışı varmış gibi gösterilmez.
- Rol bazlı görünürlük yalnız UX'tir; her özel kaynak backend'de doğrulanır.
- Auth token'ları `localStorage`, `sessionStorage` veya IndexedDB'de tutulmaz; `apiRequest` `credentials: "include"` ile HttpOnly cookie kullanır (bkz. SECURITY.md §3).
- Kullanıcı içeriği React tarafından metin olarak escape edilir; `dangerouslySetInnerHTML` ile kullanıcı HTML'i render edilmez. Kullanıcı tarafından verilen linkler güvenli şemaya (`https:`) karşı doğrulanır.
- Çıkış yapıldığında veya hesap değiştiğinde önbellekteki eski kullanıcı verisi (mesajlar, bildirimler, kütüphane) temizlenir.
- CORS, CSRF, CSP, rate limit, dosya yükleme ve sır yönetimi ayrıntıları için [SECURITY.md](SECURITY.md) zorunludur.

## 14. Çoklu dil ve yerelleştirme

- `next-intl` ile **TR/EN** (FR-UI). Varsayılan dil `tr`, locale `locale` cookie'sinde tutulur (`src/shared/lib/i18n/config.ts`). Mesajlar `src/shared/lib/i18n/messages/{tr,en}.json`.
- Başlık, hata, validation, form, navigasyon, boş durum, bildirim ve metadata metinleri **her iki dilde** eşdeğer anahtarla bulunur. UI metni bileşene hard-code edilmez.
- Çeviri anahtarları feature bazında namespace'lenir (ör. `performance.devices.empty`).
- `html lang` aktif dile uygundur. Tarih, saat, sayı ve birimler `Intl`/`next-intl` biçimlendiricileriyle locale'e göre gösterilir.
- Eksik çeviri production'da ham anahtar olarak görünmez.
- Harici sağlayıcıdan gelen oyun açıklamaları dil bilgisiyle gösterilir; mevcut değilse fallback dili belirtilir (GAME-23).

## 15. Performans ve teknik kalite

- Public sayfalarda Core Web Vitals takip edilir. Hedef: 50 sanal kullanıcıda API **P95 ≤ 2 sn** (H-09); yerel katalog için p95 ≤ 300 ms (Hamza hedefi).
- `next/image`, responsive boyutlar, lazy loading, `next/font` ve gerekli kadar JavaScript kullanılır. Ağır grafik kütüphaneleri ve admin raporları dinamik import ile ertelenir.
- Büyük listelerde sayfalama veya sanallaştırma (mesaj geçmişi, katalog, admin tabloları).
- Cache stratejisi veri türüne göre seçilir: public katalog/haber ile özel mesaj, bildirim ve kütüphane verisi aynı politikayı paylaşmaz.
- API timeout, iptal (`AbortController`) ve ağ kopması davranışları tanımlıdır. Non-idempotent istekler otomatik tekrar gönderilmez.
- Bundle boyutu, gereksiz paketler ve production source map ifşası gözden geçirilir.

## 16. PWA, gerçek zamanlı ve analitik

- PWA zorunlu değildir. Eklenirse manifest, ikonlar, service worker, cache yaşam döngüsü ve offline ekran birlikte tasarlanır; özel veriler service worker cache'ine alınmaz.
- Gerçek zamanlı mesajlaşma/bildirim (WebSocket/SSE kararı bekleniyor) bağlantı kopmasında yeniden bağlanır, kaçırılan mesajları sunucudan senkronize eder ve bağlantı durumunu kullanıcıya gösterir.
- Analitik varsa asgari ve amaca uygun olaylar ölçülür; mesaj içeriği, token, özel URL, donanım seri bilgisi gönderilmez.

## 17. Test ve kabul kuralları

- Değişen özelliğe uygun testler yazılır:
  - **Unit/bileşen:** Vitest + Testing Library + jsdom (`*.test.ts(x)` dosyası test edilen dosyanın yanında; ör. `client.test.ts`). Kurulum: `test/setup.ts`.
  - **E2E:** Playwright (`e2e/*.spec.ts`).
- Komutlar (`frontend/` içinde): `npm run lint`, `npm run typecheck`, `npm run test`, `npm run test:coverage`, `npm run build`, `npm run test:e2e`.
- Kritik akışlar: kayıt + e-posta kodu, giriş/2FA, oyun arama/detay, kütüphaneye ekleme/favori, inceleme yazma, arkadaşlık isteği, mesaj gönderme, ilan oluşturma, etkinliğe katılma/iptal, donanım profili, performans raporu, dil ve tema değişimi. Başarılı ve hata senaryoları test edilir.
- Klavye, zoom/reflow, ekran okuyucu, gerçek mobil cihaz ve yavaş ağ davranışı özellik gerektirdikçe doğrulanır.
- Yerel kalite kapısı `pre-push/` sistemidir (`.\pre-push\pre-push.cmd full dev`). SonarQube bağımsız ve isteğe bağlıdır.
- Çalıştırılmayan test başarılı raporlanmaz.

## 18. Yayın, bakım ve operasyon

- Production domain, HTTPS, ENV ve secret yönetimi onaylanmadan canlıya çıkılmaz. `NEXT_PUBLIC_*` değerleri herkese açıktır.
- Frontend şu anda Docker Compose'da değildir ve host üzerinde çalışır (`FRONTEND_HOST_PORT`, DEV `5001`). Container'laştırma ürün kararıyla yapılır.
- Deploy sonrası smoke test, izleme ve geri alma planı gerçek deployment tasarımına uygun olur.

## 19. Kodlama ajanları için davranış kuralları

1. Göreve başlamadan önce ilgili mevcut kodu, [SECURITY.md](SECURITY.md) ve bu belgeyi oku. Bu belge uygulanmış özellik listesi değildir.
2. Görev kapsamındaki kuralları **kod ve test kararına dönüştür**. İstenmeyen sayfa, yapılandırma veya entegrasyonu "tam görünmek" için oluşturma.
3. Next.js, `next-intl`, feature-based yapı, Gateway/API sözleşmeleri ve veri sahipliği kararlarını koru. Mimari değişiklik gerekiyorsa önce gerekçeyi bildir ve onay iste.
4. Başka geliştiricinin feature klasöründe değişiklik gerekiyorsa önce kullanıcıya bildir.
5. Yeni ENV, dış servis, dependency veya auth mimarisi değişikliği öncesinde SECURITY.md'deki onay kuralını uygula.
6. Her geliştirmede ne değiştiğini, neden değiştiğini, hangi testlerin gerçekten çalıştığını ve ertelenen durumları kısa ve dürüst raporla.
7. Açık yetki olmadan `git commit`, `git push`, deploy, veri silme veya volume kaldırma yapma.

---

**Kuralın özü:** LTZ frontend'inde estetik, performans, erişilebilirlik, SEO ve hukuki uyum birlikte düşünülür; hiçbir görsel veya geliştirme kolaylığı güvenliği, kullanıcı mahremiyetini veya veri doğruluğunu zayıflatamaz.
