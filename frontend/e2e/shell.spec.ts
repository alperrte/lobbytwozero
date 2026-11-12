import { test, expect } from "@playwright/test";

const widths = [375, 768, 1440] as const;

for (const width of widths) {
  test("kabuk " + width + "px genişlikte yatay taşma yapmaz", async ({
    page
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
}

test("gezinme yalnız gerçek rotaya bağlanır, hazır olmayanlar 'Yakında' işaretlidir", async ({
  page
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");

  const nav = page.getByRole("navigation", { name: "Ana gezinme" });
  await expect(nav.getByRole("link")).toHaveCount(9);
  await expect(nav.getByRole("link", { name: "Performans", exact: true })).toHaveAttribute("href", "/tr/performans");
  await expect(nav.getByRole("link", { name: "İncelemeler", exact: true })).toHaveAttribute("href", "/tr/incelemeler");
  await expect(nav.locator('a[href="#"]')).toHaveCount(0);
  await expect(nav.getByText("Yakında", { exact: true })).toHaveCount(5);
  await expect(nav).not.toContainText("Profil");
  await expect(nav).toContainText("Kampanyalar");
});

test("mobil çekmece: odak taşınır, Escape kapatır ve odağı geri verir", async ({
  page
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");

  const opener = page.getByRole("button", { name: "Menüyü aç" });
  await opener.click();

  await expect(opener).toHaveAttribute("aria-expanded", "true");
  await expect(
    page.getByRole("button", { name: "Menüyü kapat" })
  ).toBeFocused();

  await page.keyboard.press("Escape");

  await expect(opener).toHaveAttribute("aria-expanded", "false");
  await expect(opener).toBeFocused();
});

test("masaüstünde kenar çubuğu daralır ve yeniden yüklemede korunur", async ({
  page
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/");

  const sidebar = page.locator("#app-sidebar");
  const before = (await sidebar.boundingBox())?.width ?? 0;

  await sidebar.getByRole("button", { name: "Kenar çubuğunu daralt" }).click();
  await expect
    .poll(async () => (await sidebar.boundingBox())?.width ?? 0)
    .toBeLessThan(before);

  await page.reload();
  await expect(
    sidebar.getByRole("button", { name: "Kenar çubuğunu genişlet" })
  ).toBeAttached();
});

test("tema seçimi yeniden yüklemede korunur ve dilden bağımsızdır", async ({
  page
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/");

  await page.getByRole("button", { name: "Açık tema" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
});

test("sistem teması Dark ve Light tercihini izler", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  await page.emulateMedia({ colorScheme: "light" });
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
});

test("skip link odağı ana içeriğe taşır", async ({ page }) => {
  await page.goto("/");

  await page.keyboard.press("Tab");
  const skip = page.getByRole("link", { name: "Ana içeriğe geç" });
  await expect(skip).toBeFocused();
  await expect(skip).toBeVisible();

  await page.keyboard.press("Enter");
  await expect(page.locator("#main-content")).toBeFocused();
});

test("robots, sitemap ve llms.txt yanıt verir; site kökü yokken kapalıdır", async ({
  request
}) => {
  const robots = await request.get("/robots.txt");
  expect(robots.status()).toBe(200);
  expect(await robots.text()).toContain("Disallow: /");

  const sitemap = await request.get("/sitemap.xml");
  expect(sitemap.status()).toBe(200);
  expect(await sitemap.text()).not.toContain("<loc>");

  const llms = await request.get("/llms.txt");
  expect(llms.status()).toBe(200);
  expect(await llms.text()).toContain("LobbyTwoZero");
});

test("bilinmeyen rota 404 ve noindex döner", async ({ page }) => {
  const response = await page.goto("/bu-sayfa-yok");

  expect(response?.status()).toBe(404);
  // Kök layout ve Next'in not-found varsayılanı ayrı meta ekler; hepsi noindex olmalı.
  const directives = await page
    .locator('meta[name="robots"]')
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("content") ?? ""));
  expect(directives.length).toBeGreaterThan(0);
  for (const directive of directives) {
    expect(directive).toContain("noindex");
  }
});
