import { expect, test, type Page } from "@playwright/test";

const navbar = (page: Page) => page.locator("[data-hidden]").first();

/** Sayfayı kaydırılabilir yapar (ana sayfa kısa olduğundan test için yükseklik eklenir). */
async function makeScrollable(page: Page) {
  await page.evaluate(() => {
    const spacer = document.createElement("div");
    spacer.style.height = "4000px";
    document.querySelector("main")?.append(spacer);
  });
}

async function scrollTo(page: Page, y: number) {
  await page.evaluate((top) => window.scrollTo(0, top), y);
  await page.waitForTimeout(150);
}

/** React bağlanana kadar bekler (etkileşim hydration öncesi kaybolmasın). */
async function waitForHydration(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) =>
      key.startsWith("__reactProps")
    )
  );
}

test.beforeEach(async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/");
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await waitForHydration(page);
});

test("navbar yüzer: kenarlardan ayrık ve kenar çubuğunun sağında durur", async ({
  page
}) => {
  const box = await page.getByRole("banner").boundingBox();
  const sidebar = await page.locator("#app-sidebar").boundingBox();
  expect(box).not.toBeNull();
  expect(box!.y).toBeGreaterThan(4);
  expect(box!.x).toBeGreaterThanOrEqual((sidebar?.x ?? 0) + (sidebar?.width ?? 0));
  expect(box!.x + box!.width).toBeLessThan(1440);
});

test("otomatik gizleme: aşağı kaydırınca gizlenir, yukarı kaydırınca ve başta görünür", async ({
  page
}) => {
  await makeScrollable(page);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "false");

  await scrollTo(page, 600);
  await scrollTo(page, 900);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "true");

  await scrollTo(page, 700);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "false");

  await scrollTo(page, 900);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "true");
  await scrollTo(page, 0);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "false");
});

test("klavye odağı gizli navbar'ı geri getirir", async ({ page }) => {
  await makeScrollable(page);
  await scrollTo(page, 600);
  await scrollTo(page, 900);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "true");

  const language = page.getByRole("button", { name: "Dil: Türkçe" });
  await language.focus();
  await expect(language).toBeInViewport();
});

test("sabit mod: kaydırınca gizlenmez ve yeniden yüklemede korunur", async ({
  page
}) => {
  await page.getByRole("link", { name: /^Ayarlar/ }).click();
  await page.getByLabel(/^Sabit/).check({ force: true });
  await expect(page.locator("html")).toHaveAttribute("data-navbar", "fixed");
  await page.keyboard.press("Escape");

  await makeScrollable(page);
  await scrollTo(page, 600);
  await scrollTo(page, 900);
  await expect(navbar(page)).toHaveAttribute("data-hidden", "false");

  await page.reload();
  await waitForHydration(page);
  await expect(page.locator("html")).toHaveAttribute("data-navbar", "fixed");
});

test("arama: Ctrl+K açar, yalnız sayfaları arar, Enter gider, Escape kapatır", async ({
  page
}) => {
  await page.keyboard.press("Control+K");
  const input = page.getByRole("combobox", { name: "Sayfalarda ara" });
  await expect(input).toBeFocused();
  await input.fill("zzzzzz");
  await expect(page.getByText("Sonuç bulunamadı")).toBeVisible();

  await input.fill("sss");
  await expect(page.getByRole("option").first()).toBeVisible();
  await input.press("Enter");
  await expect(page).toHaveURL(/\/tr\/sss$/);
  await expect(page.getByRole("listbox")).toBeHidden();

  await page.keyboard.press("Control+K");
  await expect(input).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(input).toHaveValue("");
});

test("bildirim paneli dürüst boş durum gösterir", async ({ page }) => {
  const bell = page.getByRole("button", { name: "Bildirimler" });
  await bell.click();
  const panel = page.getByRole("region", { name: "Bildirimler" });
  await expect(panel.getByText("Henüz bildirim yok")).toBeVisible();
  await expect(panel.getByRole("listitem")).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
});

test("dil menüsü: TR/EN sunar ve seçimi kalıcı yapar", async ({ page }) => {
  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await expect(
    page.getByRole("menuitemradio", { name: "Türkçe" })
  ).toHaveAttribute("aria-checked", "true");
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");

  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
});

test("profil menüsü misafir durumunu dürüstçe gösterir", async ({ page }) => {
  await page.getByRole("button", { name: /Misafir/ }).click();
  const menu = page.getByRole("menu");
  await expect(menu).toContainText("Giriş yapılmadı");
  await expect(menu.getByRole("menuitem", { name: "Bilgisayarlarım" })).toHaveAttribute("href", "/tr/profil/bilgisayarlarim");
  await expect(menu.getByRole("menuitem", { name: /Çıkış yap/ })).toHaveAttribute("aria-disabled", "true");
  await page.keyboard.press("Escape");
  await expect(menu).toBeHidden();
});

test("Ayarlar sayfası: tema ve kenar çubuğu anında uygulanır ve kalıcıdır", async ({
  page
}) => {
  await page.getByRole("link", { name: /^Ayarlar/ }).click();
  await expect(page).toHaveURL(/\/tr\/ayarlar$/);
  const settings = page.getByRole("main");

  await settings.getByLabel(/^Açık/).check({ force: true });
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await settings.getByLabel(/^Daraltılmış/).check({ force: true });
  await expect(page.locator("html")).toHaveAttribute("data-sidebar", "collapsed");

  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("html")).toHaveAttribute("data-sidebar", "collapsed");
});

test("tema geçişi: örtü etkileşimi engellemez ve süre sonunda DOM'dan kalkar", async ({
  page
}) => {
  await page.getByRole("button", { name: "Açık tema" }).click();
  await page.getByRole("button", { name: "Bildirimler" }).click();
  await expect(page.getByRole("region", { name: "Bildirimler" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator(".ltz-fx")).toHaveCount(0, { timeout: 3000 });

  await page.getByRole("button", { name: "Koyu tema" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page.locator(".ltz-fx")).toHaveCount(0, { timeout: 3000 });
});

test("hızlı art arda tema değişiminde son seçim kazanır", async ({ page }) => {
  const light = page.getByRole("button", { name: "Açık tema" });
  const dark = page.getByRole("button", { name: "Koyu tema" });
  await light.click();
  await dark.click();
  await light.click();
  await dark.click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark", {
    timeout: 3000
  });
  await expect(page.locator(".ltz-fx")).toHaveCount(0, { timeout: 3000 });
});

test("hareket azaltma: tema anında değişir, sahne oynamaz", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.getByRole("button", { name: "Açık tema" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.locator(".ltz-fx")).toHaveCount(0);
});

test("footer: gerçek rotalara bağlanır, '#' bağlantı yoktur, yıl dinamiktir", async ({
  page
}) => {
  const footer = page.getByRole("contentinfo");
  await expect(footer.locator('a[href="#"]')).toHaveCount(0);
  for (const [name, href] of [
    ["Hakkımızda", "/tr/hakkimizda"],
    ["SSS", "/tr/sss"],
    ["İletişim", "/tr/iletisim"],
    ["KVKK Aydınlatma Metni", "/tr/yasal/kvkk"],
    ["Gizlilik Politikası", "/tr/yasal/gizlilik"],
    ["Kullanım Koşulları", "/tr/yasal/kullanim-kosullari"],
    ["Çerez Politikası", "/tr/yasal/cerezler"]
  ] as const) {
    await expect(footer.getByRole("link", { name })).toHaveAttribute("href", href);
  }
  await expect(footer).toContainText(
    `© ${new Date().getFullYear()} LobbyTwoZero. Tüm hakları saklıdır.`
  );
});

test("iletişim ve yasal sayfalar açılır, noindex yayınlar, mailto taşımaz", async ({
  page
}) => {
  for (const path of [
    "/tr/iletisim",
    "/tr/yasal/kvkk",
    "/tr/yasal/gizlilik",
    "/tr/yasal/kullanim-kosullari",
    "/tr/yasal/cerezler"
  ]) {
    const response = await page.goto(path);
    expect(response?.status()).toBe(200);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.locator('meta[name="robots"]').first()).toHaveAttribute(
      "content",
      /noindex/
    );
    await expect(page.locator('a[href^="mailto:"]')).toHaveCount(0);
  }
});

test("hakkımızda ve SSS açılır", async ({ page }) => {
  for (const path of ["/tr/hakkimizda", "/tr/sss"]) {
    const response = await page.goto(path);
    expect(response?.status()).toBe(200);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  }
});
