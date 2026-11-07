import { test } from "@playwright/test";

// Görsel inceleme: ekran görüntülerini test-results/screens altına yazar (repoya eklenmez).
// İddia (assert) içermez; insan gözüyle denetlemek içindir.
const widths = [375, 768, 1440, 1920] as const;
const themes = ["dark", "light"] as const;
const languages = ["tr", "en"] as const;

for (const width of widths) {
  for (const theme of themes) {
    for (const language of languages) {
      test(`ekran görüntüsü ${width} ${theme} ${language}`, async ({ page }) => {
        await page.context().addCookies([
          { name: "locale", value: language, url: test.info().project.use.baseURL ?? "http://127.0.0.1:5101" }
        ]);
        await page.setViewportSize({ width, height: 900 });
        await page.addInitScript((value) => {
          localStorage.setItem("ltz-theme", value);
        }, theme);
        await page.goto("/");
        await page.waitForFunction(() => Object.keys(document.querySelector("button") ?? {}).some((key) => key.startsWith("__reactProps")));
        await page.screenshot({
          path: `test-results/screens/${width}-${theme}-${language}.png`
        });
      });
    }
  }
}
