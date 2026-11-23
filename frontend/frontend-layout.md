# LTZ Frontend — Ortak Layout, Kontroller ve Renk Paleti

> **Bağlayıcıdır.** `frontend/` altında sayfa, bileşen, arama kutusu, açılır liste veya renk kullanan her iş bu belgeye uyar.
> Kural sırası: kullanıcının açık talimatı > kök `AGENTS.md` > bu belge > `frontend/CLAUDE.md` > `docs/web-rules.md`.
> Referans ekran **Donanımlar** kataloğudur (`/hardware`). Tüm ekranlar onunla aynı genişliğe ve hizaya sahiptir.

---

## 1. Sayfa layout sözleşmesi

### 1.1 Kim neyi belirler?

| Katman | Dosya | Görevi |
| --- | --- | --- |
| Route group layout | `src/app/[locale]/(public)/layout.tsx`, `src/app/[locale]/(dashboard)/layout.tsx` | `AppShell`'i **tek kez** mount eder (`footer={<Footer />}`). `(dashboard)` ayrıca `SessionProvider` ile sarar. |
| AppShell | `src/shared/components/layout/app-shell.tsx` | Navbar, sidebar, mobil drawer, footer ve `<main>`. `<main>` yan/üst boşluğu verir ve içeriği **bir kez** `PageContainer` ile sarar. |
| PageContainer | `src/shared/components/layout/page-container.tsx` | Tek içerik kabı: `mx-auto w-full max-w-[100rem]` + `data-page-container`. Ek `px` yoktur. |
| Sayfa / feature bileşeni | `src/app/**/page.tsx`, `src/features/**` | Yalnız içerik. Genişlik, yan boşluk ve üst boşluk **yazmaz**. |

### 1.2 Ölçüler

| Ölçü | Değer | Nerede |
| --- | --- | --- |
| İçerik üst sınırı | `100rem` (1600px), daha geniş ekranda ortalanır | `PageContainer` |
| Yan boşluk | mobil `px-3` (12px), `sm+` `px-5` (20px) | `<main>` |
| Üst boşluk | `pt-3` (12px), navbar altından | `<main>` |
| Alt boşluk | `pb-8` / `sm:pb-10` | `<main>` |
| Sidebar eşiği | `lg` (1024px). Altında mobil drawer, üstünde kalıcı sidebar. | `AppShell` |

### 1.3 Sayfa şunları YAZMAZ

- `AppShell`, `Footer` veya `PageContainer` import etmez. Bunlar yalnız grup layout'undadır.
- Kök elemanda `mx-auto max-w-*`, `container`, `px-*` veya üst boşluk (`pt-*`, `py-*`, `mt-*`) yazmaz.
- Negatif margin ile kabı aşmaz (ör. eski `margin-top: -12px` hilesi kaldırıldı).
- Kendi alt layout'unda ikinci bir kabuk kurmaz. Alt `layout.tsx` yalnız metadata, provider (ör. `ComparisonProvider`) veya overlay (ör. Topluluk `PublishOverlay`) içerebilir.

Sayfa içi dikey ritim serbesttir; kök `div` üzerinde `space-y-*`, `flex flex-col gap-*` kullanılabilir.

**Bilinçli istisna:** okuma sütunu bir *kap* değil, *içerik* genişliğidir. Ör. SSS'deki `max-w-[56rem]` metin sütunu kalır. Navbar (`60rem`) ve footer (`90rem`) kendi iç ölçülerini korur.

### 1.4 Yeni sayfa / yeni route group

1. Yeni sayfayı mevcut `(public)` veya `(dashboard)` grubunun altına koy. Kabuk ve genişlik otomatik gelir.
2. Oturum gerektiren sayfa `(dashboard)` altına girer.
3. Yeni bir route group gerçekten gerekiyorsa (ör. `(auth)`, `(admin)` gibi kendi kabuğu olan alanlar) önce ekip liderine sor. Ortak kabuğu kullanacaksa `AppShell`'i o grubun `layout.tsx` dosyasında tek kez mount et ve ESLint izin listesine ekle.
4. Kapsamdaki ekranlar: Ana Sayfa, Oyunlar, Kütüphanem, İncelemeler, Topluluk, Arkadaşlar, Donanımlar, Kampanyalar, Performans, Ayarlar, Bilgisayarlarım, Profilim, Hesap Ayarları ve diğer tüm `(public)`/`(dashboard)` rotaları. `(auth)` ve yönetim paneli kendi kabuklarında kalır.

### 1.5 ESLint koruması (`frontend/eslint.config.mjs`)

| Kural | Kapsam | Ne yasaklar? |
| --- | --- | --- |
| `no-restricted-imports` | `src/**` (`src/shared/components/layout/**` hariç) | `@/shared/components/layout/page-container` ve `@/shared/components/layout/app-shell` import'u |
| `no-restricted-imports` (gevşek) | `(public)/layout.tsx`, `(dashboard)/layout.tsx` | Yalnız `page-container` (AppShell burada serbest) |
| `no-restricted-syntax` | `src/**` | JSX `<select>` (bkz. §3) |

Kuralı susturmak (`eslint-disable`) yasaktır. Gerçek bir istisna varsa belgeyi ve kuralı birlikte güncelle, ekip liderinin onayını al.

---

## 2. Responsive kuralları

- Hedef genişlikler: **375 / 768 / 1440 / 1920 px** (ve 2560'ta ortalanmış 1600px kap).
- Hiçbir genişlikte yatay sayfa kaydırması olmaz (`document.documentElement.scrollWidth <= clientWidth`). Geniş tablo/kod blokları kendi içinde `overflow-x-auto` ile kayar.
- Araç çubukları (arama + filtreler) `flex flex-wrap` ile kırılır. Arama alanına `flex-1 basis-64`, filtrelere `min-w-*` ver; sabit `w-[...]px` verme.
- Mobilde sidebar drawer'dır ve içeriği kalıcı olarak örtmez. Sabit (`fixed`) overlay'ler `<main>` kabına değil viewport'a göre konumlanır.
- Doğrulama: `e2e/layout.spec.ts` her ekranda tek kap, hardware ile aynı sol kenar, 12px üst boşluk ve taşma yokluğunu ölçer.

---

## 3. Ortak kontroller: SearchField, Select, MultiSelect

Tüm arama kutuları ve açılır listeler **LTZ ovali** görünümündedir:

- `rounded-xl`, `bg-surface-elevated` yüzey, saydam kenarlık.
- Hover'da `border-control-border`, açıkken `border-primary`, hata durumunda (`aria-invalid`) `border-primary`.
- Yükseklik `md` = 40px (varsayılan), `sm` = 36px.
- Fare üzerine geldiğinde çerçevede **bir tur kırmızı iz** döner ve durur (`CardSnake trigger="hover"`). Sürekli dönmez. Klavye odağı tetiklemez. `prefers-reduced-motion` ve dokunmatik cihazlarda oynamaz.

Ortak sınıf: `ovalControlClass` + `ovalControlSize` (`src/shared/components/ui/styles.ts`). Feature içinde arama veya dropdown görünümünü elle yeniden çizme.

### 3.1 Yasaklar

- Yerel `<select>` / `<option>` kullanılmaz. ESLint hata verir. `Select` veya `MultiSelect` kullan.
- `type="search"` input'u elle stillenmez. `SearchField` kullan.
- Feature'a özel arama CSS'i (ikon konumu, kenarlık, radius) yazılmaz.
- Navbar'daki global arama (`navbar-search`) ve menüler (profil, dil, bildirim, gönderi işlemleri) bu bileşenlerin kapsamı dışındadır.

### 3.2 `SearchField` — `@/shared/components/ui/search-field`

`ComponentProps<"input">` (type/size/className hariç) alır ve aşağıdaki prop'ları ekler. `ref` input'a iletilir.

| Prop | Açıklama |
| --- | --- |
| `label` (zorunlu) | Erişilebilir ad. Varsayılan `sr-only`. |
| `showLabel` | Etiketi alanın üstünde görünür yapar. |
| `size` | `"md"` (varsayılan) / `"sm"` |
| `className` | **Dış sarmalayıcıya** uygulanır (genişlik/yerleşim: `flex-1 basis-64`, `max-w-sm`). |
| `inputClassName` | Input'a ek sınıf. Nadiren gerekir. |

```tsx
// Kontrollü
<SearchField className="flex-1 basis-64" label={t("search")} showLabel maxLength={128}
  value={draft} onChange={(event) => setDraft(event.target.value)} />

// Form (submit'te FormData ile okunur)
<form role="search" onSubmit={onSubmit} className="flex items-end gap-2">
  <SearchField className="flex-1" label={t("search")} showLabel name="q" defaultValue={q} maxLength={160} />
  <button type="submit" className={button}>{t("search")}</button>
</form>
```

- Alanın yanında ayrı bir görünür `<label htmlFor>` yazma. Görünür etiket için `showLabel` kullan, yoksa etiket iki kez okunur.
- `aria-describedby`, `aria-invalid`, `required`, `maxLength`, `placeholder` doğrudan input'a gider.

### 3.3 `Select` — `@/shared/components/ui/select`

APG "select-only combobox" kalıbıdır. Tetikleyici `role="combobox"` bir `<button>`'dır.

- **Liste konumu:** liste, en yakın açık `<dialog>` içine, yoksa `body`'ye portal edilir. Kırpılmaz, yer yoksa yukarı açılır.
- **Klavye:**
  - ↑/↓/Home/End gezinir, Enter/Boşluk seçer.
  - Esc kapatır; üstteki dialog Esc'yi görmez.
  - Tab kapatır, harf yazınca ilgili seçeneğe atlar.

| Prop | Açıklama |
| --- | --- |
| `options` | `{ value: string; label: string; disabled?: boolean }[]`. Değerler **string**'dir; sayıyı `String()` ile ver, `Number()` ile geri çevir. |
| `value` / `onChange(value: string)` | Kontrollü kullanım |
| `defaultValue` | Kontrolsüz kullanım. `form.reset()` bu değere döndürür. |
| `name` | Gizli input ile form'a taşınır: `new FormData(form).get(name)` |
| `id`, `placeholder`, `disabled`, `required`, `size`, `className` | `className` tetikleyiciye eklenir. Yüzey/kenarlık ezilmez, genişlik için dış `div` tercih et. |
| `aria-label`, `aria-labelledby`, `aria-describedby`, `aria-invalid` | Tetikleyiciye gider |

**Etiketleme — `<label>` ile SARMA.** Tetikleyici bir düğme olduğundan sarmalayan `<label>` tıklaması ve erişilebilir adı bozulur. İki doğru yol:

```tsx
// a) Görünür etiket + htmlFor (form alanları)
<label htmlFor={`${id}-category`} className="text-sm font-medium">{t("category")}</label>
<Select id={`${id}-category`} name="category" value={category} onChange={setCategory} options={categoryOptions} />

// b) Toolbar filtresi: görünür metin aria-hidden, ad aria-label ile
<div className="flex min-w-44 flex-col gap-2 text-sm">
  <span aria-hidden="true">{t("sort")}</span>
  <Select aria-label={t("sort")} value={sort} onChange={setSort} options={sortOptions} />
</div>
```

**Yerel `<select>`'ten farklar:**

- Değer seçeneklerden hiçbiriyle eşleşmezse **ilk seçeneği otomatik seçmez**, placeholder gösterir. Gerekirse `defaultValue`'yu kendin ilk seçeneğe düşür.
- Form'a yerel `input`/`change` olayı göndermez. "Form kirlendi" takibi için `onChange` kullan.
- Odaklamak için `ref` yoktur. `document.getElementById(id)` veya `container.querySelector("[role=combobox]")` ile tetikleyiciye odaklan. `form.elements.namedItem(name)` gizli input'u döndürür, odaklanamaz.

### 3.4 `MultiSelect` — `@/shared/components/ui/multi-select`

`Select` ile aynı görünüm ve klavye davranışı. Farkları:

- `value`/`defaultValue` `string[]` olur ve `onChange(value: string[])` çağrılır.
- Seçim yapınca liste açık kalır.
- `name` verilirse her değer ayrı gizli input'ta taşınır (`FormData.getAll(name)`).
- Tetikleyici tek seçimde etiketi, çoklu seçimde "ilk etiket +N" gösterir.

### 3.5 `CardSnake` tetikleyici modları — `@/shared/components/ui/card-snake`

| `trigger` | Ne zaman döner? | Kullanım |
| --- | --- | --- |
| `"hover-focus"` (varsayılan) | Fare girişi **ve** klavye odağı karta girince | Auth kartları, diyaloglar |
| `"hover"` | Yalnız fare girişi | SearchField, Select, MultiSelect |

- Her tetiklemede tek tur döner. Çalışırken gelen tetikleme yok sayılır.
- Durum `data-snake-state="running" | "idle"` ile okunur. Test kimliği `testId` prop'uyla verilir; kontrollerde `control-snake`.
- Ebeveyn `relative` ve kontrolle aynı radius'ta olmalıdır.

### 3.6 Test kalıbı (Vitest + Testing Library)

```tsx
// Seçim: tetikleyiciyi aç, seçeneği tıkla. Liste portal'dadır → within(form) değil screen kullan
// (dialog içindeyse liste dialog'a bağlanır; within(dialog) çalışır).
fireEvent.click(screen.getByRole("combobox", { name: "Sıralama" }));
fireEvent.click(screen.getByRole("option", { name: "Puan" }));

// Görünen değer
expect(screen.getByRole("combobox", { name: "Sıralama" })).toHaveTextContent("Puan");
// Form değeri
expect(new FormData(form).get("sort")).toBe("RATING");
// Arama alanı
fireEvent.change(screen.getByRole("searchbox", { name: "Ara" }), { target: { value: "elden" } });
```

`fireEvent.change(combobox, …)`, `toHaveValue` ve `select.options` artık çalışmaz. Bunlar yerel `<select>` kalıplarıdır.

---

## 4. Renk paleti (`src/app/globals.css`)

**Tek renk kaynağı `globals.css`'tir.**

- Tema, `<html data-theme="light|dark">` ile seçilir. Attribute yoksa Dark kullanılır. İlk boyamadan önce `shared/lib/preferences/init-script.ts` bu attribute'u ayarlar.
- Tokenlar `--ltz-*` CSS değişkenidir. `@theme inline` bunları Tailwind renklerine bağlar.
- Dark kaynak değerleri **değiştirilmez**.

### 4.1 Marka ve yüzey tokenları

| Token (`--ltz-*`) | Tailwind | Dark | Light | Kullanım |
| --- | --- | --- | --- | --- |
| `background` | `bg-background` | `#0B0B0F` | `#F5F5F7` | Sayfa zemini |
| `surface` | `bg-surface` | `#141419` | `#FFFFFF` | Kart, panel, liste paneli |
| `surface-elevated` | `bg-surface-elevated` | `#1E1F26` | `#EEEFF2` | Oval kontroller, hover satırı, yükseltilmiş yüzey |
| `primary` | `bg-primary`, `border-primary` | `#E11D2E` | `#E11D2E` | Ana eylem, aktif/açık kontrol kenarı, snake izi |
| `primary-foreground` | `text-primary-foreground` | `#FFFFFF` | `#FFFFFF` | `bg-primary` üzerindeki metin |
| `secondary` | `bg-secondary` | `#DB1B60` | `#DB1B60` | Birincil düğme hover'ı, ikincil vurgu |
| `accent` | `text-accent` | `#FF2E88` | `#FF2E88` | Yalnız vurgu. Light'ta beyaz yazılı küçük düğme zemini **olmaz**. |
| `text-primary` | `text-text-primary` | `#F1F1F1` | `#17171B` | Gövde metni |
| `text-secondary` | `text-text-secondary` | `#A0A7B4` | `#555B66` | Yardımcı metin, placeholder, ikon |
| `success` | `text-success` | `#22C55E` | `#167A3E` | Başarı durumu |
| `warning` | `text-warning` | `#F59E0B` | `#915600` | Uyarı durumu |
| `border` | `border-border` | `#34343C` | `#D6D7DE` | Kart/panel ayırıcı |
| `control-border` | `border-control-border` | `#737985` | `#8C919B` | Form kontrolü kenarı, oval kontrol hover'ı (≥3:1) |
| `brand-text` | `text-brand-text` | = `accent` | `#C8142A` | Küçük marka metni/ikonu, hata metni, seçili seçenek |
| `focus` | `outline-focus` | = `accent` | = `primary` | Odak halkası |

### 4.2 Yardımcı tokenlar

| Token | Tailwind | Dark | Light | Kullanım |
| --- | --- | --- | --- | --- |
| `scrim` | `bg-scrim` | `rgb(0 0 0 / .62)` | `rgb(23 23 27 / .46)` | Modal/drawer arka örtüsü |
| `shadow-drawer` / `-float` / `-popover` | `shadow-drawer`, `shadow-float`, `shadow-popover` | koyu gölgeler | yumuşak gölgeler | Drawer, yüzen kart, açılır liste |
| `sun` / `moon` / `on-celestial` | `text-sun`, `text-moon`, `text-on-celestial` | `#F5A524` / `#C9D1E0` / `#17171B` | `#E8920C` / `#8B94A8` / `#17171B` | Yalnız tema anahtarı ikonları |
| `scrollbar-track` | (CSS) | `#0B0B0F` | `#F5F5F7` | Kaydırma çubuğu izi |
| `scene-ink` | `text-scene-ink` | `#0B0B0F` | `#17171B` | Sahne illüstrasyonu siluetleri |
| `stage`, `on-stage`, `on-stage-muted`, `stage-border` | `bg-stage`, `text-on-stage`… | `#0B0B0F`, `#F1F1F1`, `#A0A7B4`, `#34343C` | `#DFE0E6`, `#17171B`, `#555B66`, `#C9CAD3` | LTZ Atölyesi 3D vitrini |

### 4.3 Radius ve ölçü tokenları

| Token | Tailwind | Değer | Kullanım |
| --- | --- | --- | --- |
| `--radius-control` | `rounded-control` | 4px | Düğme, rozet, küçük kontrol |
| `--radius-panel` | `rounded-panel` | 6px | Kart, panel |
| `--radius-float` | `rounded-float` | 14px | Yüzen kart/popover |
| `--radius-navbar` | `rounded-navbar` | 18px | Navbar kapsülü |
| (istisna) | `rounded-xl` | 12px | **Oval kontroller** (SearchField, Select, MultiSelect ve listeleri) |
| `--spacing-header` / `--spacing-header-offset` | `h-header`, `top-header-offset`… | 4rem / 5.5rem | Navbar yüksekliği ve yapışkan öğe ofseti |

### 4.4 Kullanım kuralları

- JSX/CSS'te **hex, `rgb()`, Tailwind'in ham paleti (`red-500`, `zinc-900`…) yasaktır.** Yalnız semantik tokenlar kullanılır.
- Yeni renk gerekiyorsa `globals.css`'e **iki tema için de** `--ltz-*` tokenı ve `@theme inline` eşlemesi ekle, bu tabloyu güncelle.
- Küçük metin/ikon vurgusu `text-brand-text`'tir. Dark'ta kırmızı küçük metin yeterli kontrast vermez, Light'ta pembe vermez.
- Odak her zaman `focus-visible:outline-2 focus-visible:outline-focus` (veya `ovalControlClass`) ile görünür olur.
- Durum renkleri (`success`, `warning`, `primary` hata) yalnız renge dayanmaz; metin veya ikonla birlikte verilir.
- Kontrast hedefleri: gövde metni ≥ 4.5:1, büyük metin ≥ 3:1, kontrol kenarı/odak ≥ 3:1 (WCAG 2.2 AA).

---

## 5. Kontrol listesi (PR öncesi)

- [ ] Sayfa `AppShell`/`PageContainer` import etmiyor; kökte `max-w-*`/`mx-auto`/`px-*`/üst boşluk yok.
- [ ] Arama → `SearchField`; dropdown → `Select`/`MultiSelect`; `<select>` yok; `<label>` ile sarılmış Select yok.
- [ ] Renkler yalnız semantik token.
- [ ] `npm run lint`, `npm run typecheck`, `npm run test` geçti.
- [ ] Layout veya kontrol değiştiyse `npm run test:e2e -- layout controls` geçti; 375/768/1440/1920'de light/dark gözle kontrol edildi.
- [ ] E2E testinde ortak `Select` seçimi `locator.selectOption` ile değil, `e2e/fixtures/select.ts` içindeki `chooseOption(trigger, "deger")` (veya `{ index }`) ile yapılır; seçenekler `data-value` taşır.
