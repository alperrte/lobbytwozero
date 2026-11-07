import { expect, test, type Page } from "@playwright/test";

// Visual QA only: these records never enter the application or a real database.
const uid = (number: number) => `10000000-0000-0000-0000-${String(number).padStart(12, "0")}`;
const specifications = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
const catalog = [
  { id: uid(1), category: "CPU", model: "AMD Ryzen 7 7800X3D", specifications: { ...specifications, coreCount: 8, clockMhz: 4200, socket: "AM5", powerWatts: 120 } },
  { id: uid(2), category: "GPU", model: "NVIDIA GeForce RTX 5070", specifications: { ...specifications, capacityGb: 12, memoryType: "GDDR7", clockMhz: 2325, powerWatts: 250 } },
  { id: uid(3), category: "STORAGE", model: "Samsung 990 PRO 2TB", specifications: { ...specifications, capacityGb: 2000, interfaceType: "PCIe 4.0", formFactor: "M.2 2280" } },
  { id: uid(4), category: "RAM", model: "Corsair Vengeance RGB 32GB", specifications: { ...specifications, capacityGb: 32, clockMhz: 6000, memoryType: "DDR5", formFactor: "DIMM" } },
  { id: uid(5), category: "MOTHERBOARD", model: "MSI MPG B650 TOMAHAWK WIFI", specifications: { ...specifications, socket: "AM5", memoryType: "DDR5", formFactor: "ATX" } },
  { id: uid(6), category: "COOLING", model: "NZXT Kraken Elite 360 RGB", specifications: { ...specifications, formFactor: "360 mm", socket: "AM5" } },
  { id: uid(7), category: "GPU", model: "NVIDIA GeForce RTX 4070 SUPER", specifications: { ...specifications, capacityGb: 12, memoryType: "GDDR6X", powerWatts: 220 } },
  { id: uid(8), category: "CPU", model: "AMD Ryzen 9 9900X", specifications: { ...specifications, coreCount: 12, socket: "AM5", powerWatts: 120 } }
].map((product) => ({ ...product, brands: [], status: "ACTIVE", version: 0 }));

async function fixture(page: Page, failure?: number | "network") {
  const calls: Array<{ path: string; method: string; query: string; body: unknown }> = [];
  await page.route("**/api/v1/hardware/**", async (route) => {
    const url = new URL(route.request().url());
    calls.push({ path: url.pathname, method: route.request().method(), query: url.search, body: route.request().postData() ? route.request().postDataJSON() : null });
    if (failure === "network") return route.abort();
    if (failure) return route.fulfill({ status: failure, json: { code: "TEST_ERROR" } });
    if (url.pathname.endsWith("/catalog/brands")) return route.fulfill({ json: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    if (url.pathname.endsWith("/catalog/products")) {
      const items = catalog.filter((item) => !url.searchParams.get("category") || item.category === url.searchParams.get("category"));
      return route.fulfill({ json: { items, page: 0, size: 12, totalElements: items.length, totalPages: 1 } });
    }
    if (url.pathname.endsWith("/comparisons/catalog-products")) {
      const body = route.request().postDataJSON();
      const convert = (id: string) => { const item = catalog.find((product) => product.id === id)!; return { id: item.id, category: item.category, modelName: item.model, quantity: null, specifications: item.specifications }; };
      return route.fulfill({ json: { left: convert(body.leftId), right: convert(body.rightId) } });
    }
    return route.fulfill({ status: 404, json: { code: "UNEXPECTED_TEST_REQUEST" } });
  });
  return calls;
}

const viewports = [{ width: 1672, height: 941 }, { width: 1440, height: 940 }, { width: 1920, height: 1080 }, { width: 768, height: 1024 }, { width: 375, height: 900 }];
for (const viewport of viewports) for (const theme of ["dark", "light"]) for (const locale of ["tr", "en"]) {
  test(`catalog reference ${viewport.width} ${theme} ${locale}`, async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await fixture(page);
    await page.setViewportSize(viewport);
    await page.addInitScript((value) => { localStorage.setItem("ltz-theme", value); localStorage.setItem("ltz-transition", "minimal"); }, theme);
    await page.goto(locale === "tr" ? "/tr/donanimlar" : "/en/hardware");
    await expect(page.getByRole("heading", { name: catalog[0].model, exact: true })).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    const grid = page.getByTestId("catalog-grid");
    const columns = await grid.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(" ").length);
    expect(columns).toBe(viewport.width >= 1440 ? 3 : viewport.width === 768 ? 2 : 1);
    const card = await page.getByTestId("catalog-product").first().boundingBox();
    expect(card!.height).toBeLessThanOrEqual(360);
    expect(card!.height).toBeGreaterThan(280);
    if (viewport.width >= 1440) {
      await expect(page.getByRole("button", { name: locale === "tr" ? "Filtreler" : "Filters", exact: true })).toBeHidden();
      const banner = await page.locator("main header").boundingBox();
      expect(banner!.height).toBe(100);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/catalog-visual/catalog-${viewport.width}-${theme}-${locale}.png` });
    expect(errors).toEqual([]);
  });
}

for (const failure of [401, 503, "network"] as const) {
  test(`catalog error remains readable ${failure}`, async ({ page }) => {
    await fixture(page, failure);
    await page.goto("/tr/donanimlar");
    await expect(page.locator("main").getByRole("alert")).toBeVisible();
    await expect(page.locator("main").getByRole("alert")).toHaveCount(1);
    await expect(page.getByTestId("catalog-product")).toHaveCount(0);
    await expect(page.locator("main").getByRole("alert")).toContainText(failure === 401 ? "Oturum" : failure === 503 ? "kullanılamıyor" : "Bağlantı");
    await page.screenshot({ path: `test-results/catalog-visual/error-${failure}.png` });
  });
}

for (const width of [1672, 375]) for (const theme of ["dark", "light"]) {
  test(`catalog selection bar and comparison API ${width} ${theme}`, async ({ page }) => {
    const calls = await fixture(page);
    await page.setViewportSize({ width, height: width === 375 ? 900 : 941 });
    await page.addInitScript((value) => localStorage.setItem("ltz-theme", value), theme);
    await page.goto("/tr/donanimlar");
    const first = page.getByRole("checkbox", { name: `Karşılaştırmak için ${catalog[0].model} seç`, exact: true });
    await first.check();
    const bar = page.getByRole("region", { name: "Donanım karşılaştırma" });
    await expect(bar.getByRole("button", { name: "Karşılaştırmayı aç" })).toBeDisabled();
    await page.getByRole("checkbox", { name: `Karşılaştırmak için ${catalog[7].model} seç`, exact: true }).check();
    await page.evaluate(() => window.scrollTo(0, 0));
    await expect(bar).toBeInViewport();
    await expect(bar).toContainText(catalog[0].model);
    await expect(bar).toContainText(catalog[7].model);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/catalog-visual/comparison-${width}-${theme}.png` });
    await bar.getByRole("button", { name: "Karşılaştırmayı aç" }).click();
    await expect(page.getByRole("dialog").getByRole("columnheader", { name: catalog[0].model })).toBeVisible();
    expect(calls.find((call) => call.path.endsWith("/comparisons/catalog-products"))).toMatchObject({ method: "POST", body: { leftId: uid(1), rightId: uid(8) } });
    await page.keyboard.press("Escape");
    await bar.getByRole("button", { name: `${catalog[0].model} ürününü karşılaştırmadan çıkar`, exact: true }).click();
    await expect(first).not.toBeChecked();
    await bar.getByRole("button", { name: "Temizle", exact: true }).click();
    await expect(bar).toHaveCount(0);
  });
}

test("catalog category overflow and mobile technical filters preserve the API contract", async ({ page }) => {
  const calls = await fixture(page);
  await page.setViewportSize({ width: 375, height: 900 });
  await page.goto("/tr/donanimlar?q=Ryzen");
  await page.getByRole("combobox", { name: "Diğer donanım kategorileri" }).selectOption("PSU");
  await expect(page).toHaveURL(/category=PSU/);
  await expect(page.getByRole("heading", { name: "Bu seçimde donanım bulunamadı" })).toBeVisible();
  await page.getByRole("button", { name: "Filtreler", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox", { name: "Donanım kategorileri" }).selectOption("CPU");
  await dialog.locator('[name="minCoreCount"]').fill("4");
  await dialog.locator('[name="maxCoreCount"]').fill("16");
  await dialog.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page).toHaveURL(/minCoreCount=4/);
  await expect.poll(() => calls.some((call) => call.path.endsWith("/catalog/products") && call.query.includes("category=CPU") && call.query.includes("minCoreCount=4") && call.query.includes("q=Ryzen"))).toBe(true);
  await page.getByRole("button", { name: "Liste", exact: true }).click();
  await expect(page.getByRole("button", { name: "Liste", exact: true })).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({ path: "test-results/catalog-visual/list-375.png" });
});

test("catalog main error retries without duplicating the manufacturer failure", async ({ page }) => {
  await fixture(page, 503);
  await page.goto("/tr/donanimlar");
  const error = page.locator("main").getByRole("alert");
  await expect(error).toHaveCount(1);
  await fixture(page);
  await error.getByRole("button", { name: "Yeniden yükle" }).click();
  await expect(page.getByRole("heading", { name: catalog[0].model, exact: true })).toBeVisible();
  await expect(error).toHaveCount(0);
  await expect(page.getByText("Üretici listesi yüklenemedi; adla arayabilirsin.")).toBeVisible();
  await page.locator("main aside").getByRole("button", { name: "Yeniden yükle" }).click();
  await expect(page.locator("main aside select[name=brandId]")).toBeEnabled();
});
