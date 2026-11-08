import { expect, test, type Page } from "@playwright/test";
import data from "./fixtures/hardware-visual-products.json";
import type { Product } from "../src/features/hardware/types/hardware";
import { productName } from "../src/features/hardware/schemas/spec-profile";

const products = data as unknown as Product[];
const pageResult = (items: Product[]) => ({ items, page: 0, size: 100, totalElements: items.length, totalPages: items.length ? 1 : 0 });

// Yalnız browser testinde API replay. Kaynak gerçek local katalog snapshot'ıdır;
// production test modu veya auth bypass eklenmez.
async function replay(page: Page) {
  await page.route("**/api/v1/hardware/**", (route) => {
    const url = new URL(route.request().url());
    const category = url.searchParams.get("category");
    if (url.pathname.endsWith("/catalog/products")) return route.fulfill({ json: pageResult(products.filter((p) => !category || p.category === category)) });
    if (url.pathname.endsWith("/catalog/brands")) return route.fulfill({ json: { ...pageResult([]), items: products.flatMap((p) => p.brands) } });
    if (url.pathname.endsWith("/catalog/facets")) return route.fulfill({ json: { category, facets: {} } });
    if (url.pathname.endsWith("/comparisons/catalog-products")) {
      const body = route.request().postDataJSON();
      const item = (id: string) => { const p = products.find((value) => value.id === id)!; return { id, category: p.category, modelName: p.model, quantity: null, specifications: p.specifications }; };
      return route.fulfill({ json: { left: item(body.leftId), right: item(body.rightId) } });
    }
    if (url.pathname.endsWith("/reviews")) return route.fulfill({ json: { ...pageResult([]), summary: { ratedReviews: 0, averageRating: null } } });
    const product = products.find((p) => url.pathname.endsWith(`/catalog/products/${p.id}`));
    return product ? route.fulfill({ json: product }) : route.fulfill({ status: 404, json: { code: "NOT_IN_SNAPSHOT" } });
  });
}

for (const width of [1920, 1440, 768, 375]) for (const theme of ["dark", "light"]) for (const locale of ["tr", "en"]) {
  test(`hardware product visuals ${width} ${theme} ${locale}`, async ({ page }) => {
    test.setTimeout(60000);
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("console", (message) => { if (message.type() === "error" && /MISSING_MESSAGE|hydration/i.test(message.text())) errors.push(message.text()); });
    page.on("response", (response) => { if (response.url().includes("/assets/hardware/") && response.status() >= 400) errors.push(`Asset ${response.status()}`); });
    await replay(page);
    await page.setViewportSize({ width, height: width < 900 ? 1000 : 1080 });
    await page.addInitScript((value) => { localStorage.setItem("ltz-theme", value); localStorage.setItem("ltz-transition", "minimal"); }, theme);
    const base = locale === "tr" ? "/tr/donanimlar" : "/en/hardware";
    await page.goto(base);
    await expect(page.getByTestId("catalog-product")).toHaveCount(10);
    await expect(page.locator('[data-gpu-vendor="NVIDIA"]')).toHaveCount(1);
    await expect(page.locator('[data-gpu-vendor="AMD"]')).toHaveCount(1);
    await expect(page.locator('[data-gpu-vendor="Intel"]')).toHaveCount(1);
    await expect(page.locator('[data-kind="STORAGE_M2"]')).toHaveCount(1);
    await expect(page.locator('[data-kind="STORAGE_SATA"]')).toHaveCount(1);
    await page.getByTestId("catalog-product").last().scrollIntoViewIfNeeded();
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => window.scrollTo(0, 0));
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/hardware-product-visual/catalog-${width}-${theme}-${locale}.png`, fullPage: true });

    const pair = theme === "dark" ? products.slice(2, 4) : products.slice(0, 2);
    for (const p of pair) await page.getByTestId("catalog-product").filter({ has: page.getByRole("heading", { name: productName(p), exact: true }) }).getByRole("checkbox").check();
    await page.getByRole("button", { name: locale === "tr" ? "Karşılaştırmayı aç" : "Open comparison", exact: true }).click();
    await expect(page.getByTestId("comparison-view")).toBeVisible();
    await expect(page.getByTestId("comparison-view").locator('[data-testid="hardware-product-visual"]')).toHaveCount(2);
    for (const visual of await page.getByTestId("comparison-view").locator('.hardwareVisual--compact').all()) {
      const box = await visual.boundingBox();
      expect(box!.width).toBeLessThanOrEqual(80);
      expect(box!.height).toBeLessThanOrEqual(60);
    }
    await page.screenshot({ path: `test-results/hardware-product-visual/compare-${width}-${theme}-${locale}.png` });

    // Matris boyunca CPU AMD/Intel, üç GPU üreticisi, anakart, RAM, M.2 ve SATA detayları.
    const index = ([1920, 1440, 768, 375].indexOf(width) * 4 + (theme === "light" ? 2 : 0) + (locale === "en" ? 1 : 0)) % 10;
    await page.goto(`${base}/${products[index].id}`);
    await expect(page.getByTestId("product-title")).toHaveText(productName(products[index]));
    await expect(page.locator(".hardwareVisual--detail")).toBeVisible();
    await expect.poll(() => page.locator('.hardwareVisual--detail img').evaluate((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0)).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/hardware-product-visual/detail-${width}-${theme}-${locale}.png` });
    expect(errors).toEqual([]);
  });
}
