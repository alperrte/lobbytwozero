# LTZ Frontend

LobbyTwoZero (LTZ) web arayüzü. Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4 ve next-intl (TR varsayılan, EN) kullanır.

Kurallar ve tasarım sözleşmesi: [CLAUDE.md](CLAUDE.md), [../docs/web-rules.md](../docs/web-rules.md), [../docs/ltz-palette-and-tokens.md](../docs/ltz-palette-and-tokens.md).

## Komutlar

```bash
npm run dev         # geliştirme sunucusu
npm run lint        # ESLint
npm run typecheck   # TypeScript
npm run test        # Vitest birim testleri
npm run test:e2e    # Playwright (kendi sunucusunu 5101 portunda başlatır)
npm run build       # üretim derlemesi
```

`test:e2e` çalışırken aynı klasörde başka bir `next dev` açık olmamalıdır.

## Yapı (özet)

- `src/app`: rotalar ve layout'lar. `(public)`, `(auth)`, `(dashboard)`, `(admin)` route group'ları.
- `src/shared/components/layout`: ortak kabuk (AppShell, header, sidebar, footer).
- `src/shared/config`: site, public rota ve gezinme tanımları.
- `src/shared/lib/i18n`: dil altyapısı ve `messages/tr.json`, `messages/en.json`.
- `src/app/globals.css`: tek renk kaynağı (CSS değişkenleri ve `@theme inline`).

## Tema ve dil

Tema (Light / Dark / sistem) `<html data-theme>` ile seçilir ve ilk boyamadan önce satır içi bir betikle ayarlanır. Dil çerez ile tutulur; URL'de dil öneki yoktur. İkisi birbirinden bağımsızdır.

## Ortam değişkeni

| Değişken | Zorunlu | Açıklama |
| --- | --- | --- |
| `NEXT_PUBLIC_SITE_URL` | Hayır | Yayındaki doğrulanmış `https` site kökü. Tanımsızsa `robots.txt` her şeyi kapatır, `sitemap.xml` boş döner ve canonical yazılmaz. |

Gerçek değerler `.env.local` gibi dosyalara yazılır ve repoya eklenmez.
