# LTZ Frontend

LobbyTwoZero (LTZ) web arayüzü. Bu klasör paylaşılan **uygulama kabuğunu** (navbar, kenar çubuğu, footer, ayarlar, arama, tema/dil altyapısı) ve ilk herkese açık sayfaları içerir. Giriş/kayıt, Hardware paneli ve gerçek servis entegrasyonları henüz yoktur; bunlar kabukta dürüstçe "hazır değil" olarak gösterilir.

Bağlayıcı belgeler: [../AGENTS.md](../AGENTS.md), [../docs/web-rules.md](../docs/web-rules.md), [../docs/folder-structure.md](../docs/folder-structure.md), [../docs/SECURITY.md](../docs/SECURITY.md), [../docs/ltz-palette-and-tokens.md](../docs/ltz-palette-and-tokens.md).

## 1. Teknoloji

| Alan | Seçim |
| --- | --- |
| Çatı | Next.js 16.4 (App Router, Turbopack), React 19.3, TypeScript 5 |
| Stil | Tailwind CSS 4 (`@theme inline`, `@tailwindcss/turbopack`) |
| Dil | next-intl 4 (`tr` varsayılan, `en`; dil URL önekindedir: `/tr/...`, `/en/...`; Türkçe sitede Türkçe slug) |
| Derleyici | React Compiler (`babel-plugin-react-compiler`); lint kuralları buna göre sıkıdır |
| Test | Vitest 5 + Testing Library + jsdom; Playwright (Chromium) |
| Gerçek bağımlılıklar | Yalnız `next`, `next-intl`, `react`, `react-dom`. Animasyon, ikon, UI veya state kütüphanesi **yoktur**; yenisini eklemeden önce gerekçe ve onay gerekir. |

## 2. Komutlar

```bash
npm run dev            # geliştirme sunucusu
npm run lint           # ESLint
npm run typecheck      # tsc --noEmit
npm run test           # Vitest birim testleri
npm run test:coverage  # Vitest + kapsama
npm run build          # üretim derlemesi
npm run test:e2e:install   # Playwright Chromium (ilk kurulum)
npm run test:e2e           # Playwright (kendi sunucusunu 5101 portunda başlatır)
```

`test:e2e` aynı klasörde çalışan bir `next dev` varken **başlamaz** (Next ikinci dev sunucusuna izin vermez); önce mevcut sunucuyu kapat.

Playwright ekran görüntüleri (`e2e/visual.spec.ts`) `test-results/screens/` altına yazılır; bu klasör repoya girmez.

## 3. Klasör haritası

```text
src/
  app/                      rotalar, layout'lar, globals.css, robots/sitemap
    (public)/               herkese açık sayfalar (ana sayfa, about, faq, contact, legal/*)
    (auth) (dashboard) (admin)/   ileride; şimdilik yalnız layout iskeleti
  features/<servis>/        servis bazlı özellikler (components, hooks, services, schemas, types)
  shared/
    components/
      layout/               AppShell, Navbar, Sidebar, Footer, Settings/Search dialogları,
                            Language/Notification/Profile menüleri, LegalPage
      theme-transition/     tema geçiş örtüsü (theme-transition-layer.tsx) + paketler:
                            v3/ taktik V3 (varsayılan, piksel-art operatör), legacy/v2/ taktik V2,
                            legacy/v1/ klasik (gün doğumu, fırtına)
      ui/                   küçük, genel parçalar (ikonlar, bayraklar, ModalDialog, OptionGroup, Notice)
    config/                 site, pages (sayfa kataloğu), navigation, public-routes
    hooks/                  tercih ve kabuk hook'ları
    lib/
      api/client.ts         apiRequest (gateway çağrıları)
      i18n/                 config, request, actions, messages/{tr,en}.json
      preferences/          tema/kenar çubuğu/navbar/geçiş paketi tercihi + ilk boyama betiği
      search/               arama çekirdeği ve sayfa sağlayıcısı
      seo/                  sayfa başı metadata
      theme/                tema geçişi durum makinesi + sahne/süre kaydı (transition-scenes.ts)
e2e/                        Playwright testleri
test/setup.ts               Vitest kurulumu (<dialog> polyfill'i dahil)
public/                     llms.txt ve marka görselleri
```

Yeni dosya, [../docs/folder-structure.md](../docs/folder-structure.md) dışında bir yere konmaz.

## 4. Kabuk nasıl çalışır

- **Tek Navbar / Sidebar / Footer.** `AppShell` ([src/shared/components/layout/app-shell.tsx](src/shared/components/layout/app-shell.tsx)) hepsini bir kez çizer; masaüstü ve mobil için ayrı menü kopyası yoktur. Kenar çubuğu mobilde çekmece olur.
- **Navbar** yüzen karttır. Solda kenar çubuğu düğmesi ve sayfa adı, ortada arama, sağda bildirim, dil, tema ve profil bulunur. İki mod vardır: **Otomatik gizle** (varsayılan; aşağı kaydırınca gizlenir, yukarı kaydırınca/odakta/açık menü varken görünür) ve **Sabit**. Karar mantığı saf fonksiyondur: `decideNavbarVisibility`.
- **Arama** `Ctrl+K` / `Cmd+K` ile açılır. Şimdilik yalnız var olan sayfaları arar. Yeni kaynak (oyun, oyuncu, donanım) eklemek için `SearchProvider` yaz ve `searchProviders` dizisine ekle ([pages-provider.ts](src/shared/lib/search/pages-provider.ts)); arayüz değişmez.
- **Bildirimler** servis bağlanana kadar dürüst boş durum gösterir. Sahte sayaç/liste yoktur. Gerçek veri `useShellNotifications` içinden verilir.
- **Profil menüsü** misafir durumunu gösterir. Henüz var olmayan rotalar bağlantı yapılmaz, "Yakında" işaretlenir. "Çıkış yap" yalnız oturum varken görünür.
- **Ayarlar** (kenar çubuğundan): Görünüm (Sistem/Açık/Koyu), Dil (TR/EN), Üst çubuk (Otomatik/Sabit), Kenar çubuğu (Geniş/Daraltılmış), Tema geçişi (Taktik V3/Taktik V2 — Eski sürüm/Klasik V1/Sade). Hesap/güvenlik ayarı yoktur. Değişiklik anında uygulanır ve bu cihazda saklanır.

### Tercihler ve ilk boyama

Tercihlerin tek doğruluk kaynağı `<html>` niteliğidir: `data-theme`, `data-theme-pref`, `data-sidebar`, `data-navbar`, `data-transition`. Satır içi betik ([init-script.ts](src/shared/lib/preferences/init-script.ts)) bunları ilk boyamadan önce `localStorage` (`ltz-theme`, `ltz-sidebar`, `ltz-navbar`, `ltz-transition`) veya sistem tercihinden ayarlar; böylece tema yanıp sönmez ve hydration uyuşmazlığı oluşmaz. Hook'lar `useSyncExternalStore` ile bu niteliği okur. Tema ve dil birbirinden bağımsızdır.

### Tema geçişleri

**Mimari.** Tema (`data-theme`) ile geçiş paketi (`data-transition`) birbirinden bağımsızdır. Paket dört değerden biridir: `v3` (varsayılan), `v2`, `v1`, `minimal`. Varsayılan paket (`v3`) için `data-transition` niteliği ve `ltz-transition` kaydı yoktur; yalnız `v2`/`v1`/`minimal` yazılır. Geçersiz kayıt (ör. elle bozulmuş değer) güvenli biçimde `v3`'e düşer. Böylece sunucu ve ilk istemci çıktısı aynı kalır (hydration uyuşmazlığı yok).

| Paket | Light → Dark | Dark → Light | Süre (tema uygulanma anı) |
| --- | --- | --- | --- |
| `v3` Taktik V3 (varsayılan) | `v3-blackout` (Operasyon Karartma) | `v3-daybreak` (Operasyon Gün Doğumu) | 2600 ms (1560 / 1770); dar ekran 2200 ms (1140 / 1500) |
| `v2` Taktik V2 — eski sürüm | `blackout` | `daybreak` | 2200 ms; dar ekran 1700 ms |
| `v1` Klasik | `storm` (yağmur, şimşek, gece) | `sunrise` (gün doğumu, dağlar) | 1400 ms (560) |
| `minimal` | sahne yok, tema anında değişir | sahne yok | 0 |

Dar ekran: `max-width: 767px`.

**V3 nasıl çalışır?** Özgün, olgun oranlı bir piksel-art LTZ operatörü: kenarlı (boonie) şapka, kızıl sakal, yeşil kamuflaj, plaka taşıyıcı, eldiven, ten rengi bacak kılıfı, dizlik ve bot; ekipman olarak NVG, stilize tüfek ve işaret fişeği tabancası. Küçük kırmızı LTZ vurgusu taşır. Var olan bir oyun karakterinin kopyası değildir.

- **Çizim kaynağı:** [art/operator-art.ts](src/shared/components/theme-transition/v3/art/operator-art.ts) içindeki palet ve poz tanımlarıdır (64×64 hücre, 28 kare, sabit pivot: ayaklar y = 63, kalça x = 35). [art/pixel-canvas.ts](src/shared/components/theme-transition/v3/art/pixel-canvas.ts) ve [art/sprite-sheet.ts](src/shared/components/theme-transition/v3/art/sprite-sheet.ts) kareleri çizer, [art/png-codec.ts](src/shared/components/theme-transition/v3/art/png-codec.ts) PNG'ye yazar.
- **Üretilmiş çıktılar:** [assets/operator-sprites.png](src/shared/components/theme-transition/v3/assets/operator-sprites.png) şeffaf zeminli yatay sprite sayfasıdır. [assets/operator-character-sheet.png](src/shared/components/theme-transition/v3/assets/operator-character-sheet.png) inceleme için kare tablosudur. [operator-frames.ts](src/shared/components/theme-transition/v3/operator-frames.ts) kare indeksleri, namlu, NVG ve el bağlantı noktalarını içerir; elle düzenlenmez.
- **Yeniden üretme:** `LTZ_WRITE_SPRITES=1 npx vitest run src/shared/components/theme-transition/v3/art/sprite-sheet.test.ts`. Normal test koşusu, depodaki PNG'nin kaynakla piksel piksel aynı olduğunu doğrular.
- **Ölçek:** sprite `image-rendering: pixelated` ile tam sayı ölçekte çizilir. Masaüstünde 2× (128 px), dar ekranda 1.5× (96 px). PNG yalnız örtü çizildiğinde istenir; ilk sayfa yüklemesine eklenmez.
- **Zaman ve konum:** [timeline.ts](src/shared/components/theme-transition/v3/timeline.ts) her iki sahnenin saf zaman çizelgesidir (kare ipuçları, atış/isabet/sönme anları, fişek yayı). [stage.ts](src/shared/components/theme-transition/v3/stage.ts) operatörü ve lambaları ekran boyutuna yerleştirir: masaüstünde 3, dar ekranda 2 lamba olur ve her biri nişan ışınının üstündedir. [scenery.ts](src/shared/components/theme-transition/v3/scenery.ts) deterministik piksel sırtları ve güneş diskini SVG yolu olarak üretir.
- **Oynatma:** [scene-player.ts](src/shared/components/theme-transition/v3/scene-player.ts) bu izleri Web Animations API ile oynatır. Animasyonlar denetleyicinin `startedAt` saatine göre hizalanır. Sahne bileşenleri [v3-scenes.tsx](src/shared/components/theme-transition/v3/v3-scenes.tsx) içinde, durağan görünüm [v3.css](src/shared/components/theme-transition/v3/v3.css) içindedir. Yalnız `transform`, `opacity`, `clip-path` ve `background-position` anime edilir.

- **Blackout (Light → Dark):**
  1. Tavandan sarkan ince konili lambalar yanar.
  2. Operatör sağ alttan gizli yürüyüşle girer ve çöker.
  3. Telsiz levhası operatörün yanında "LTZ BİRİMİ // KARARTMA BAŞLIYOR" ve "OPERASYON KARARTMA" yazar.
  4. Her lamba için nişan, namlu parlaması ve iz mermisi gelir; isabette kıvılcım çıkar, lamba kısa titreyip söner. Patlama ve ekran sarsıntısı yoktur.
  5. Son lamba söndüğünde Dark tema uygulanır.
  6. Operatör NVG'yi eliyle indirir. Yeşil ışık yalnız gözlükte yanar.
  7. Operatör gölgeye karışır ve örtü kalkar.
- **Daybreak (Dark → Light):**
  1. Operatör NVG'yi kaldırır ve ışık söner.
  2. Fişek tabancasını çeker ve geri tepmeyle ateşler. Fişek sıcak bir izle yükselir.
  3. Levha "OPERASYON GÜN DOĞUMU" ve "IŞIKLAR AÇIK" yazar.
  4. Katmanlı piksel sırtların arkasından güneş doğar. Sıcak kehribar ışık yayıldığında Light tema uygulanır.
  5. Operatör şapkasını düzeltir, yürüyerek çıkar ve örtü kalkar.

**Eski paketler (legacy).** V1 ve V2 silinmedi. Seçilebilir ve aynen çalışır. V3 bunların varlıklarını kullanmaz.

- **V2:** [legacy/v2/tactical-scenes.tsx](src/shared/components/theme-transition/legacy/v2/tactical-scenes.tsx), [legacy/v2/operator-sprite.tsx](src/shared/components/theme-transition/legacy/v2/operator-sprite.tsx), [legacy/v2/sprite-data.ts](src/shared/components/theme-transition/legacy/v2/sprite-data.ts) ve [legacy/v2/tactical.css](src/shared/components/theme-transition/legacy/v2/tactical.css).
- **V1:** [legacy/v1/classic-scenes.tsx](src/shared/components/theme-transition/legacy/v1/classic-scenes.tsx), [legacy/v1/scenes.tsx](src/shared/components/theme-transition/legacy/v1/scenes.tsx) ve [legacy/v1/classic.css](src/shared/components/theme-transition/legacy/v1/classic.css).

**Ortak denetleyici.** Durum makinesi [theme-transition.ts](src/shared/lib/theme/theme-transition.ts) içindedir: hangi sahnenin oynayacağını paketten seçer, temayı doğru anda uygular, art arda tıklamada son isteği kazandırır ve zamanlayıcıları temizler. Sahne adları ve süreler [transition-scenes.ts](src/shared/lib/theme/transition-scenes.ts) kaydındadır. Örtü bileşeni [theme-transition-layer.tsx](src/shared/components/theme-transition/theme-transition-layer.tsx) `popover="manual"` üst katmanında, `pointer-events: none`, `aria-hidden` ve `contain: strict` ile çizilir; tema durumu ile animasyon durumu ayrıdır. Ortak taban stil [theme-transition.css](src/shared/components/theme-transition/theme-transition.css) içindedir. Ses, WebGL ve animasyon kütüphanesi yoktur. V1/V2 CSS + SVG, V3 WAAPI + PNG sprite + SVG kullanır.

**Ayarlarla bağlantı.** Ayarlar sayfasındaki "Tema geçişi" kartı ([settings-form.tsx](src/shared/components/layout/settings-form.tsx)) `useTransitionPack` hook'unu ([use-transition-pack.ts](src/shared/hooks/use-transition-pack.ts)) kullanır. Seçim `localStorage` + `data-transition` ile saklanır ve tema seçiminden bağımsızdır. Durum sahipliği: tercih `preferences.ts` ve `<html>` niteliğinde, animasyon durumu `theme-transition.ts` deposunda, çizim `theme-transition-layer.tsx` içindedir.

**Reduced motion.** `prefers-reduced-motion: reduce` açıksa sahne hiç oynamaz ve tema anında değişir; CSS'te de `.ltz-fx` gizlenir (ek güvenlik ağı). `minimal` paketi de aynı sonucu verir. V1, V2 ve V3 aynı davranışı izler.

**Yeni geçiş nasıl eklenir?**
1. `transition-scenes.ts` içinde `ThemeScene` birleşimine yeni sahne adını, `sceneTimings` kaydına süreleri (toplam + tema uygulanma anı, gerekiyorsa dar ekran) ekle; `sceneFor` paket + hedef temayı yeni sahneye eşlesin.
2. Sahne bileşenini ve stilini yeni bir paket klasöründe (`v3/` gibi) yaz; renkleri yerel değişkenlerle tanımla, yalnız `transform`/`opacity`/`clip-path` (sprite için `background-position`) anime et. Eskiyen paketi silmek yerine `legacy/` altına taşı.
3. `theme-transition-layer.tsx` içindeki `sceneContent` eşlemesine sahneyi, CSS'i de aynı dosyadaki import listesine ekle.
4. Yeni paket gerekiyorsa `preferences.ts` içindeki paket tipine/ayrıştırıcısına, `init-script.ts`'e, Ayarlar kartındaki seçeneklere ve TR/EN `Settings.transition` metinlerine ekle.
5. Test ekle: denetleyici için `theme-transition.test.ts`, örtü için `theme-transition-layer.test.tsx`, sahneye özel saf mantık için `v3/` altındaki gibi ayrı testler.

**Test.**
- `npm run test -- theme-transition`: denetleyici, örtü ve tüm V3 testleri (`timeline.test.ts`, `stage.test.ts`, `scenery.test.ts`, `v3-scenes.test.tsx`, `art/sprite-sheet.test.ts`).
- `npm run test -- sprite-data`: V2 sprite verisi (`legacy/v2/sprite-data.test.ts`).
- `npm run test -- preferences settings-form`: tercih ve ayar kartı.
- Tüm paket: `npm run test`.

## 5. Tasarım sistemi kuralları

- **Renk yalnız tokenlarla.** Tek kaynak [src/app/globals.css](src/app/globals.css). Bileşende hex veya `rgb()` yazılmaz; `bg-surface`, `text-text-primary`, `border-border`, `text-brand-text`, `bg-primary` gibi semantik sınıflar kullanılır. Dark palet onaylıdır ve **değiştirilmez**; Light palet kontrast testleriyle korunur.
- **Tipografi:** başlık ve etiketlerde `font-display` (Chakra Petch), gövdede `font-sans` (IBM Plex Sans). Satır uzunluğu `max-w-prose` civarı tutulur.
- **Köşe ve gölge:** `rounded-control` (keskin, logo dili), `rounded-panel`, yalnız yüzen öğelerde `rounded-float`; gölge `shadow-float` / `shadow-popover` / `shadow-drawer`.
- **Boşluk:** Tailwind ölçeği; navbar yüksekliği `h-header`, ana içerik üst boşluğu `--spacing-header-offset` ile bağlıdır.
- **İkonlar:** tek yerde, [icons.tsx](src/shared/components/ui/icons.tsx) (satır içi SVG, `currentColor`). Bayraklar [flags.tsx](src/shared/components/ui/flags.tsx) (yerel SVG). Emoji ikon olarak kullanılmaz.
- **Erişilebilirlik:** WCAG 2.2 AA hedefi. Görünür odak halkası, erişilebilir adlar, `aria-expanded/-controls`, modal için yerel `<dialog>`.
- **Yasaklı kalıplar ("AI slop"):** her şeyi aynı kartlara bölmek, dekoratif gradyan/parıltı yığını, anlamsız numaralı etiketler, tüm büyük harf etiket yığını, gereksiz hover animasyonu, sahte veri (kullanıcı, bildirim, sayaç, metrik). Süs eklemeden önce neyi anlattığını sor.

## 6. Dil (i18n)

- Mesajlar: [tr.json](src/shared/lib/i18n/messages/tr.json) ve [en.json](src/shared/lib/i18n/messages/en.json). **İki dosyanın anahtarları aynı olmalıdır** (`messages.test.ts` doğrular).
- Sunucu bileşeninde `getTranslations("Bolum")`, istemcide `useTranslations("Bolum")`.
- Yeni metin: iki dosyaya aynı anahtarı ekle, bileşende anahtarı kullan. Metni koda gömme.
- **Dil URL önekindedir** (`localePrefix: "always"`): `/tr/hakkimizda`, `/en/about`. Önek yoksa `proxy.ts` çerez (`locale`), `Accept-Language` ve varsayılan (`tr`) ile yönlendirir.
- **Slug'lar dile göre çevrilir.** Tek kaynak [routing.ts](src/shared/lib/i18n/routing.ts) içindeki `pathnames` tablosudur; klasör adları (`app/[locale]/(public)/about`) dahili anahtardır, görünen adres tablodan gelir. Yeni sayfa: klasörü aç, `pathnames`'e TR ve EN slug'ı ekle, `pages.ts`'e kaydet.
- Bağlantı ve yönlendirme için `next/link` / `next/navigation` değil, [navigation.ts](src/shared/lib/i18n/navigation.ts) içindeki `Link`, `usePathname`, `useRouter`, `getPathname` kullanılır; `href` olarak dahili anahtar (`"/about"`) verilir.
- Dil değişimi `router.replace(pathname, { locale })` ile yapılır; açık sayfanın eşdeğerine geçer (`/tr/hakkimizda` → `/en/about`). Seçim `locale` çerezine yazılır.
- Aynı sayfanın ikinci adresleri (`/tr/about`, `/en/hakkimizda`, öneksiz `/about`) 308 ile kanonik adrese yönlendirilir ([canonical-path.ts](src/shared/lib/i18n/canonical-path.ts)); böylece Türkçe sitede İngilizce slug yaşamaz ve yinelenen içerik oluşmaz.
- `hreflang` alternatifleri (`tr`, `en`, `x-default`) ve sitemap'teki dil çiftleri aynı `pathnames` tablosundan üretilir.

## 7. SEO

- Site adı her yerde **LobbyTwoZero**; `shared/config/site.ts` ve `Metadata.*` mesajlarından gelir.
- `robots.ts`, `sitemap.ts` ve `public/llms.txt` herkese açık sayfa kataloğundan ([pages.ts](src/shared/config/pages.ts)) beslenir. Sitemap her sayfayı her dil için ayrı adres olarak (karşılıklı `hreflang` ile) listeler. `indexable: false` sayfalar (iletişim, yasal) sitemap dışıdır ve `noindex` yayınlar.
- Site kökü bilinmiyorsa (`NEXT_PUBLIC_SITE_URL` boş/geçersiz) canonical yazılmaz, sitemap boş döner, `robots.txt` her şeyi kapatır. Domain veya `localhost` uydurulmaz.
- Özel alanlar (`(auth)`, `(dashboard)`, `(admin)`) indekslenmez.
- Sayfa başı metadata için `buildPageMetadata("<id>")` kullan ([page-metadata.ts](src/shared/lib/seo/page-metadata.ts)).

### `NEXT_PUBLIC_SITE_URL`

| Değişken | Zorunlu | Açıklama |
| --- | --- | --- |
| `NEXT_PUBLIC_SITE_URL` | Production'da evet | Yayındaki `https` site kökü (ör. `https://alanadi.com`). Yerel örnek `http://localhost:5001`; yerel adreste site indekslenmez. |

- **Build anında koda gömülür.** Değeri değiştirince frontend yeniden build edilmelidir.
- Örnek satırlar [../.env.example](../.env.example) ve [.env.example](.env.example) içindedir. Gerçek `.env` / `.env.local` dosyaları repoya girmez ve bu araçlar tarafından okunmaz.
- Değeri doğrulayıp yazdırmak için (dosya değiştirmez):

```bash
node ../ops/frontend/site-url.mjs https://alanadi.com --production
node ../ops/frontend/site-url.mjs --help
```

Araç adresi doğrular (http/https ayrımı, production'da yalnız https ve yerel olmayan alan adı), kök adrese indirger ve eklenecek `NEXT_PUBLIC_SITE_URL=...` satırını **ekrana yazar**; hiçbir ENV dosyasını okumaz veya değiştirmez. Testleri: `node --test ../ops/frontend/site-url.test.mjs`.

## 8. Nasıl eklenir?

**Yeni sayfa**
1. `src/app/(public)/<yol>/page.tsx` oluştur; `generateMetadata = () => buildPageMetadata("<id>")` kullan.
2. `shared/config/pages.ts` içine kaydı ekle (`indexable` kararını ver). Footer, arama, navbar adı, sitemap ve robots buradan beslenir.
3. `Pages.<id>.{title,description}` ve gerekli diğer mesajları TR ve EN dosyalarına ekle.
4. Uydurma içerik yazma; hazır değilse sayfada durumu dürüstçe belirt (`Notice`).

**Yeni özellik (feature)**
1. `src/features/<servis>/` altında `components`, `hooks`, `services`, `schemas`, `types` kullan.
2. Akış: bileşen → hook → `features/<x>/services` → `shared/lib/api/client.ts`. Bileşenden doğrudan `fetch` çağrılmaz.
3. Özelliğe ait bileşen `shared/` içine konmaz; birden fazla özellik kullanıyorsa `shared`'a taşı.

**Kenar çubuğuna bağlantı**
1. Menü verisi `shared/config/navigation.ts` içindeki `navigationGroups` (Genel, Sosyal, Donanım & Performans) dizisindedir; `primaryNavigation` bundan türetilir. Öğe zaten listelidir: rota yayınlandığında yalnız o öğeye `href` yazmak yeterlidir, bileşen değişmeden etkinleşir. Rota yoksa `href` yazma: öğe "Yakında" rozetiyle, tıklanamaz gösterilir (kırık bağlantı üretilmez). İkon `IconName` olmalıdır.
2. Yeni öğe/grup eklenirse etiketi `Navigation.*` (ve grup için `Navigation.groups.*`) mesajlarına, TR ve EN birlikte ekle.

**Navbar eylemi**
- `navbar.tsx` içine ekle; açılır panel için `usePopover` + `popoverPanelClass` kullan. Kaydırma/modal davranışını bozmamak için panel açıkken `aria-expanded="true"` ver (navbar bu durumda görünür kalır).

**API istemcisi**
- Servis adresleri gateway üzerinden `/api/v1/<servis>/**` biçimindedir. Sözleşmenin tek kaynağı [../docs/api.md](../docs/api.md); başka servisin API'sini çağırmadan önce oradan oku. `apiRequest` `credentials: "include"` ile çalışır. Yeni endpoint'i tüketmek için `features/<servis>/services` içine tipli bir fonksiyon yaz.

## 9. Gelecekteki auth bağlantısı

- Kabuk, kullanıcıyı yalnız [use-shell-user.ts](src/shared/hooks/use-shell-user.ts) üzerinden bilir; şimdilik her zaman `null` (misafir). Auth hazır olunca yalnız bu hook gerçek oturumu okuyacak şekilde değişir; `ProfileMenu` ve `Navbar` aynı kalır.
- Bildirimler için karşılığı [use-shell-notifications.ts](src/shared/hooks/use-shell-notifications.ts).
- **Token politikası:** token `localStorage`, `sessionStorage` veya IndexedDB'de tutulmaz; oturum HttpOnly cookie ile taşınır ([../docs/SECURITY.md](../docs/SECURITY.md)). Kabuk tercihleri (`ltz-theme` vb.) gizli veri değildir ve bu kurala girmez.

## 10. Test ve doğrulama

| Katman | Kapsam |
| --- | --- |
| Vitest | kabuk bileşenleri, navbar gizleme kararı, arama çekirdeği, tema geçiş durum makinesi, tercih deposu, sayfa/gezinme kataloğu, i18n anahtar eşitliği, site URL doğrulama, robots/sitemap |
| Playwright | navbar (otomatik/sabit, kalıcılık), arama, bildirim, dil, profil, ayarlar, tema geçişi, footer ve yasal sayfalar, SEO dosyaları, yatay taşma, ekran görüntüleri |
| Node test | `ops/frontend/site-url.test.mjs` |

Teslimden önce: `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`, `npm run test:e2e`. Çalıştırılamayan adım "geçti" sayılmaz.

**Duyarlı doğrulama:** 375, 768, 1440 (ve 1920) genişlikte, Dark ve Light, TR ve EN ile kontrol et: yatay taşma yok, navbar kenar çubuğuyla hizalı, popover'lar ekrana sığıyor, mobil çekmece odağı taşıyor ve geri veriyor.

## 11. Kabul listesi

- [ ] Renkler yalnız tokenlarla; yeni hex yok.
- [ ] TR ve EN mesaj anahtarları eşit.
- [ ] Yeni rota `pages.ts`'e ve gerekiyorsa menüye eklendi; `href="#"` yok.
- [ ] Sahte kullanıcı/bildirim/metrik yok; hazır olmayan şey dürüstçe işaretli.
- [ ] Klavye ile tüm kontroller erişilebilir; odak görünür; `Escape` açık panelleri kapatır.
- [ ] `prefers-reduced-motion` saygı görüyor.
- [ ] Hydration uyarısı ve tema yanıp sönmesi yok; konsol temiz.
- [ ] `lint`, `typecheck`, `test`, `build` (ve mümkünse `test:e2e`) çalıştırıldı ve sonuçlar kaydedildi.
