# LTZ — Light / Dark Palet ve Tailwind 4 Token Sözleşmesi

**Durum:** Dark palet kaynak dosyadan birebir aktarılmıştır; Light palet **yeni öneridir**, kaynakta bulunmuyordu. İki temanın görsel kabulü gerçek browser ile yapılmalıdır.

## Renk eşlemesi

| Semantik alan | Dark (orijinal) | Light (yeni öneri) | Not |
| --- | --- | --- | --- |
| Background | `#0B0B0F` | `#F5F5F7` | Ana zemin |
| Surface | `#141419` | `#FFFFFF` | Navbar/sidebar/kart |
| Surface Elevated | `#1E1F26` | `#EEEFF2` | İç panel, input vb. |
| Primary | `#E11D2E` | `#E11D2E` | Onaylı kırmızı marka rengi |
| Secondary | `#DB1B60` | `#DB1B60` | Onaylı magenta marka rengi |
| Accent | `#FF2E88` | `#FF2E88` | Onaylı pembe vurgu/focus |
| Text Primary | `#F1F1F1` | `#17171B` | Ana metin |
| Text Secondary | `#A0A7B4` | `#555B66` | Açıklama |
| Success | `#22C55E` | `#167A3E` | Light değeri yeni kontrastlı metin türevi |
| Warning | `#F59E0B` | `#915600` | Light değeri yeni kontrastlı metin türevi |
| Border | `#34343C` | `#D6D7DE` | **Yeni** nötr yardımcı token |
| Control Border | `#737985` | `#8C919B` | **Yeni** kontrol sınırı |

Dark paletin ilk on ana rengi kaynaktan aynen alınmıştır. Border ve Control Border iki tema için yeni yardımcı tokenlardır.

## `src/app/globals.css` için uygulama örneği

Bu **bir kopyala-yapıştır önerisidir**; mevcut `globals.css` tamamen silinmez. Kurulu Tailwind config, fontlar ve tema yöneticisiyle uyum kontrolü yapılarak birleştirilir. Buradaki `data-theme` seçimi tema uygulamasının aynı attribute'u ayarlamasını gerektirir.

```css
@import "tailwindcss";

@custom-variant dark (&:where([data-theme="dark"], [data-theme="dark"] *));

:root,
:root[data-theme="dark"] {
  color-scheme: dark;
  --ltz-background: #0B0B0F;
  --ltz-surface: #141419;
  --ltz-surface-elevated: #1E1F26;
  --ltz-primary: #E11D2E;
  --ltz-secondary: #DB1B60;
  --ltz-accent: #FF2E88;
  --ltz-text-primary: #F1F1F1;
  --ltz-text-secondary: #A0A7B4;
  --ltz-success: #22C55E;
  --ltz-warning: #F59E0B;
  --ltz-border: #34343C;
  --ltz-control-border: #737985;
}

:root[data-theme="light"] {
  color-scheme: light;
  --ltz-background: #F5F5F7;
  --ltz-surface: #FFFFFF;
  --ltz-surface-elevated: #EEEFF2;
  --ltz-primary: #E11D2E;
  --ltz-secondary: #DB1B60;
  --ltz-accent: #FF2E88;
  --ltz-text-primary: #17171B;
  --ltz-text-secondary: #555B66;
  --ltz-success: #167A3E;
  --ltz-warning: #915600;
  --ltz-border: #D6D7DE;
  --ltz-control-border: #8C919B;
}

@theme inline {
  --color-background: var(--ltz-background);
  --color-surface: var(--ltz-surface);
  --color-surface-elevated: var(--ltz-surface-elevated);
  --color-primary: var(--ltz-primary);
  --color-secondary: var(--ltz-secondary);
  --color-accent: var(--ltz-accent);
  --color-text-primary: var(--ltz-text-primary);
  --color-text-secondary: var(--ltz-text-secondary);
  --color-success: var(--ltz-success);
  --color-warning: var(--ltz-warning);
  --color-border: var(--ltz-border);
  --color-control-border: var(--ltz-control-border);
}

@layer base {
  html { min-height: 100%; }
  body {
    min-height: 100%;
    background: var(--ltz-background);
    color: var(--ltz-text-primary);
  }
}
```

### Kullanım notları

- `bg-background`, `bg-surface`, `bg-surface-elevated`, `text-text-primary`, `text-text-secondary`, `border-border` gibi utility'ler semantik tokenlardan gelir.
- Başlangıçta Dark varsayılan olabilir; `html[data-theme]` ile tema seçimi ve sistem tercihi tarayıcıda tutarlı işletilmelidir. SSR/hydration flash'ı engelle.
- Başka bir theme provider `class="dark"` kullanıyorsa iki mekanizmayı aynı anda tutma; bu örneği mevcut provider'a uyumla.
- Orijinal Dark palet referansında Light yasakları Dark tema bağlamındadır. LTZ'nin Light tema ihtiyacı kullanıcı tarafından ayrıca istenmiştir.
- Normal metin için uygun kontrast ölçülmelidir. Ön değerlendirmede marka kırmızısı `#E11D2E` / beyaz `#FFFFFF` oranı **yaklaşık 4.75:1**, magenta/beyaz **4.84:1**, pembe/beyaz **3.50:1**. Pembe üstündeki küçük beyaz yazıyı kullanma.
- Gerçek tasarım tamamlandıktan sonra renk körlüğü, yüksek kontrast ve focus görünürlüğü ayrı test edilir; bu metin WCAG sertifikası değildir.
