import { expect, test, type Page } from "@playwright/test";

// Yalnız test fixture'ı: bu kayıtlar uygulamaya veya gerçek veritabanına girmez. Fiyat/stok alanı yoktur.
type Typed = Record<string, { number?: number; text?: string; unit?: string }>;
const uid = (number: number) => `20000000-0000-0000-0000-${String(number).padStart(12, "0")}`;
const empty = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
const brand = (name: string) => ({ id: uid(900), name, status: "ACTIVE", version: 0 });
const product = (n: number, category: string, model: string, maker: string, typed: Typed) => ({
  id: uid(n), category, model, brands: [brand(maker)], specifications: empty, status: "ACTIVE", version: 0,
  source: { provider: "PC_PART_DATASET", typedSpecifications: Object.fromEntries(Object.entries(typed).map(([key, value]) => [key, { number: null, text: null, unit: null, ...value }])) }
});
const catalog = [
  product(1, "CPU", "Ryzen 7 7800X3D", "AMD", { microarchitecture: { text: "Zen 4" }, coreCount: { number: 8 }, clockMhz: { number: 4200, unit: "MHz" }, boostClockMhz: { number: 5000, unit: "MHz" }, powerWatts: { number: 120, unit: "W" } }),
  product(2, "CPU", "Core i7-14700K", "Intel", { microarchitecture: { text: "Raptor Lake" }, coreCount: { number: 20 }, clockMhz: { number: 3400, unit: "MHz" }, boostClockMhz: { number: 5600, unit: "MHz" }, powerWatts: { number: 125, unit: "W" }, integratedGraphics: { text: "true" } }),
  product(3, "GPU", "PULSE", "Sapphire", { chipset: { text: "Radeon RX 7900 XTX" }, capacityGb: { number: 24, unit: "GB" }, boostClockMhz: { number: 2500, unit: "MHz" }, lengthMm: { number: 320, unit: "mm" } }),
  product(4, "MOTHERBOARD", "MPG B650 TOMAHAWK WIFI", "MSI", { socket: { text: "AM5" }, formFactor: { text: "ATX" }, maximumMemoryGb: { number: 192, unit: "GB" }, memorySlots: { number: 4 } }),
  product(5, "RAM", "Vengeance RGB", "Corsair", { memoryType: { text: "DDR5" }, capacityGb: { number: 32, unit: "GB" }, clockMhz: { number: 6000, unit: "MHz" }, casLatency: { number: 30 }, moduleCount: { number: 2 }, moduleCapacityGb: { number: 16, unit: "GB" } }),
  product(6, "STORAGE", "990 PRO", "Samsung", { storageType: { text: "SSD" }, capacityGb: { number: 2000, unit: "GB" }, interfaceType: { text: "M.2 PCIe 4.0 X4" }, formFactor: { text: "M.2-2280" }, cacheMb: { number: 2048, unit: "MB" } })
];
const now = "2026-11-07T00:00:00Z";
const review = { id: uid(70), productId: uid(1), body: "Oyunlarda ve derlemede tutarlı performans verdi.", rating: 5, status: "VISIBLE", version: 0, ownedByActor: false, createdAt: now, updatedAt: now };
const paged = (items: unknown[]) => ({ items, page: 0, size: 12, totalElements: items.length, totalPages: items.length ? 1 : 0 });

async function install(page: Page, reviews: unknown[] = [review]) {
  const calls: Array<{ method: string; path: string; query: string }> = [];
  await page.route("**/api/v1/hardware/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace("/api/v1/hardware", "");
    calls.push({ method: route.request().method(), path, query: url.search });
    const category = url.searchParams.get("category");
    const q = url.searchParams.get("q")?.toLowerCase();
    if (path === "/catalog/brands") return route.fulfill({ json: paged([brand("AMD"), brand("Intel"), brand("MSI")]) });
    if (path === "/catalog/facets") return route.fulfill({ json: { category, facets: { brandId: ["AMD", "Intel", "MSI"].map((name, index) => ({ value: uid(910 + index), label: name, count: 1 })) } } });
    if (path === "/catalog/products") {
      const items = catalog.filter((item) => (!category || item.category === category) && (!q || item.model.toLowerCase().includes(q)));
      return route.fulfill({ json: { ...paged(items), totalElements: items.length } });
    }
    const detail = path.match(/^\/catalog\/products\/([^/]+)$/);
    if (detail) { const item = catalog.find((entry) => entry.id === detail[1]); return route.fulfill({ status: item ? 200 : 404, json: item ?? { code: "NOT_FOUND" } }); }
    if (/^\/catalog\/products\/[^/]+\/reviews$/.test(path)) {
      if (route.request().method() === "POST") return route.fulfill({ status: 401, json: { code: "UNAUTHENTICATED" } });
      return route.fulfill({ json: { ...paged(reviews), summary: { ratedReviews: reviews.length, averageRating: reviews.length ? 5 : null } } });
    }
    if (path === "/comparisons/catalog-products") {
      const body = route.request().postDataJSON();
      const convert = (id: string) => { const item = catalog.find((entry) => entry.id === id)!; return { id: item.id, category: item.category, modelName: item.model, quantity: null, specifications: item.specifications }; };
      return route.fulfill({ json: { left: convert(body.leftId), right: convert(body.rightId) } });
    }
    return route.fulfill({ status: 404, json: { code: "UNEXPECTED_TEST_REQUEST" } });
  });
  return calls;
}

const tabs = [["CPU", "İşlemci"], ["GPU", "Ekran kartı"], ["MOTHERBOARD", "Anakart"], ["RAM", "Bellek"], ["STORAGE", "Depolama"]] as const;

test("beş kategori sekmesi sırayla gelir ve fiyat/stok alanı hiçbir yerde yoktur", async ({ page }) => {
  await install(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/tr/donanimlar");
  const nav = page.getByRole("navigation", { name: "Donanım kategorileri" });
  await expect(nav.getByRole("button")).toHaveCount(5);
  expect(await nav.getByRole("button").allInnerTexts()).toEqual(expect.arrayContaining(tabs.map(([, label]) => expect.stringContaining(label))));
  await expect(page.locator("main")).not.toContainText(/₺|\bTL\b|sepete ekle|stokta|satın al/i);
});

for (const [category, label] of tabs) {
  test(`${category} kataloğu kategoriye özel kart bilgisi ve filtre gösterir`, async ({ page }) => {
    const calls = await install(page);
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.addInitScript(() => { localStorage.setItem("ltz-theme", "dark"); localStorage.setItem("ltz-transition", "minimal"); });
    await page.goto("/tr/donanimlar");
    await page.getByRole("navigation", { name: "Donanım kategorileri" }).getByRole("button", { name: label }).click();
    await expect(page).toHaveURL(new RegExp(`category=${category}`));
    const cards = page.getByTestId("catalog-product");
    await expect(cards.first()).toBeVisible();
    for (const card of await cards.all()) await expect(card).toContainText(/\d|DDR|AM5|SSD|ATX/);
    await expect(page.locator("main aside")).toBeHidden();
    await page.getByRole("button", { name: "Filtreler", exact: true }).click();
    await expect(page.getByRole("group", { name: "Teknik filtreler" }).or(page.locator("main aside"))).toBeVisible();
    expect(calls.some((call) => call.path === "/catalog/products" && call.query.includes(`category=${category}`))).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/catalog-categories/catalog-${category.toLowerCase()}-1440-dark.png` });
  });
}

test("arama ve üretici seçimi API parametresi olarak gider", async ({ page }) => {
  const calls = await install(page);
  await page.goto("/tr/donanimlar?category=CPU");
  await page.getByRole("searchbox", { name: "Model ara" }).fill("ryzen");
  await expect(page.getByTestId("catalog-product")).toHaveCount(1);
  expect(calls.some((call) => call.query.includes("q=ryzen"))).toBe(true);
  await page.getByRole("button", { name: "Filtreler", exact: true }).click();
  await page.getByRole("combobox", { name: "Marka", exact: true }).click();
  await page.getByRole("option", { name: "AMD", exact: true }).click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect.poll(() => calls.some((call) => call.path === "/catalog/products" && call.query.includes(`brandId=${uid(910)}`))).toBe(true);
});

test("tümleşik grafik onay kutusu yalnız işlemcide görünür ve API parametresi olarak gider", async ({ page }) => {
  const calls = await install(page);
  await page.goto("/tr/donanimlar?category=GPU");
  await page.getByRole("button", { name: "Filtreler", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "Marka", exact: true })).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Tümleşik grafik" })).toHaveCount(0);
  await page.goto("/tr/donanimlar?category=CPU");
  await page.getByRole("button", { name: "Filtreler", exact: true }).click();
  await page.getByRole("checkbox", { name: "Tümleşik grafik" }).check();
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page).toHaveURL(/integratedGraphics=true/);
  await expect.poll(() => calls.some((call) => call.path === "/catalog/products" && call.query.includes("integratedGraphics=true"))).toBe(true);
  await page.getByRole("checkbox", { name: "Tümleşik grafik" }).uncheck();
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page).not.toHaveURL(/integratedGraphics/);
});

for (const [index, [category]] of tabs.entries()) {
  test(`${category} detay sayfası kategoriye özel tablo ve yorum bölümünü gösterir`, async ({ page }) => {
    await install(page);
    const item = catalog.find((entry) => entry.category === category)!;
    await page.setViewportSize({ width: 1440, height: 1100 });
    await page.addInitScript(() => { localStorage.setItem("ltz-theme", "dark"); localStorage.setItem("ltz-transition", "minimal"); });
    await page.goto(`/tr/donanimlar/${item.id}`);
    await expect(page.getByTestId("product-title")).toContainText(item.model);
    const table = page.getByTestId("product-specifications");
    await expect(table).toBeVisible();
    await expect(table.locator("dt")).not.toHaveCount(0);
    await expect(page.getByRole("button", { name: "Karşılaştırmaya ekle" }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: /Tüm donanımlar|Kataloğa dön/ }).first()).toBeVisible();
    if (index === 0) {
      await expect(page.getByText("Oyunlarda ve derlemede tutarlı performans verdi.")).toBeVisible();
      await expect(table).toContainText("—");
    }
    await page.screenshot({ path: `test-results/catalog-categories/detail-${category.toLowerCase()}-1440-dark.png`, fullPage: true });
  });
}

test("yorum yokken boş durum gösterilir; bulunamayan ürün 404 ayrı mesaj verir", async ({ page }) => {
  await install(page, []);
  await page.goto(`/tr/donanimlar/${catalog[0].id}`);
  await expect(page.locator("#reviews")).toBeVisible();
  await expect(page.locator("#reviews")).not.toContainText("tutarlı performans");
  await page.goto(`/tr/donanimlar/${uid(999)}`);
  await expect(page.getByRole("alert").or(page.getByRole("heading", { name: /bulunamadı/i })).first()).toBeVisible();
  await page.screenshot({ path: "test-results/catalog-categories/detail-not-found.png" });
});

test("aynı kategoride karşılaştırma yan yana tablo ve fark işareti verir; farklı kategori reddedilir", async ({ page }) => {
  const calls = await install(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.addInitScript(() => { localStorage.setItem("ltz-theme", "dark"); localStorage.setItem("ltz-transition", "minimal"); });
  await page.goto("/tr/donanimlar?category=CPU");
  const boxes = page.getByRole("checkbox", { name: /Karşılaştırmak için/ });
  await boxes.nth(0).check();
  await boxes.nth(1).check();
  await page.getByRole("region", { name: "Donanım karşılaştırma" }).getByRole("button", { name: "Karşılaştırmayı aç" }).click();
  await expect(page).toHaveURL(/\/tr\/donanimlar\/karsilastir\?ids=/);
  const view = page.getByTestId("comparison-view");
  await expect(view).toBeVisible();
  await expect(view.locator('tr[data-state="different"]').first()).toBeVisible();
  await expect(view.locator('tr[data-state="missing"]').first()).toContainText("—");
  await expect(view).not.toContainText(/kazanan:|en iyi/i);
  expect(calls.filter((call) => /\/catalog\/products\/[^/]+$/.test(call.path)).length).toBeGreaterThanOrEqual(2);
  expect(calls.some((call) => call.path === "/comparisons/catalog-products")).toBe(false);
  await page.screenshot({ path: "test-results/catalog-categories/comparison-1440-dark.png" });
  await page.goto("/tr/donanimlar");
  await page.getByRole("checkbox", { name: new RegExp(`Karşılaştırmak için .*${catalog[0].model}`) }).check();
  const gpu = page.getByRole("checkbox", { name: new RegExp(`Karşılaştırmak için .*${catalog[2].model}`) });
  await gpu.click();
  await expect(gpu).not.toBeChecked();
  await expect(page.getByRole("region", { name: "Donanım karşılaştırma" }).getByRole("status")).toBeVisible();
  await expect(page.getByRole("region", { name: "Donanım karşılaştırma" }).getByRole("button", { name: "Karşılaştırmayı aç" })).toBeDisabled();
});

for (const theme of ["dark", "light"]) for (const width of [1920, 1024, 768, 375]) {
  test(`katalog ve detay ${width} ${theme} yatay taşma yapmaz`, async ({ page }) => {
    await install(page);
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript((value) => { localStorage.setItem("ltz-theme", value); localStorage.setItem("ltz-transition", "minimal"); }, theme);
    await page.goto("/tr/donanimlar?category=GPU");
    await expect(page.getByTestId("catalog-product").first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/catalog-categories/catalog-gpu-${width}-${theme}.png` });
    await page.goto(`/tr/donanimlar/${catalog[2].id}`);
    await expect(page.getByTestId("product-specifications")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/catalog-categories/detail-gpu-${width}-${theme}.png`, fullPage: true });
  });
}
