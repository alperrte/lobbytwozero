import { expect, test, type Page, type Route } from "@playwright/test";

// Test-only backend fixtures. No fixture is imported by the application.
const firstId = "00000000-0000-0000-0000-000000000001";
const secondId = "00000000-0000-0000-0000-000000000002";
const profileId = "00000000-0000-0000-0000-000000000010";
const now = "2026-11-07T00:00:00Z";
const specs = { capacityGb: null, clockMhz: 3600, coreCount: 8, powerWatts: 65, memoryType: null, socket: "Test socket", interfaceType: null, formFactor: null };
const products = [firstId, secondId].map((id, index) => ({ id, category: "CPU", model: `Fixture CPU ${index + 1}`, brands: [], specifications: specs, status: "ACTIVE", version: 0 }));
const profile = { id: profileId, name: "Fixture computer", deviceType: "DESKTOP", caseColor: "BLACK", source: "MANUAL", version: 0, createdAt: now, updatedAt: now };
// LTZ Atölyesi için beş kategoride katalog ürünü; üretici model adından okunur.
const part = (index: number, category: string, model: string, typed: Record<string, unknown> = {}) => ({ id: `20000000-0000-0000-0000-00000000000${index}`, category, model, brands: [], specifications: { ...specs, socket: null }, status: "ACTIVE", version: 0, source: { typedSpecifications: typed } });
const workshopParts = [
  part(1, "CPU", "Ryzen 7 7800X3D"), part(2, "MOTHERBOARD", "B650 Tomahawk"), part(3, "RAM", "Vengeance 32 GB", { moduleCount: { number: 2, text: null, unit: null } }),
  part(4, "STORAGE", "990 Pro 2 TB"), part(5, "GPU", "GeForce RTX 4070 Super"), part(6, "GPU", "Radeon RX 7800 XT")
];
const seededComponents = () => workshopParts.slice(0, 5).map((item, index) => ({ id: `00000000-0000-0000-0000-0000000001${index}0`, category: item.category, productId: item.id, modelName: item.model, brandName: null, brandId: null, quantity: 1, source: "MANUAL", version: 0, specifications: specs }));
const campaign = { id: secondId, productId: firstId, title: "Fixture campaign", sourceName: "Fixture store", sourceUrl: "https://store.example.test/offer", originalPrice: 200, salePrice: 150, currency: "TRY", discountPercent: 25, startsAt: "2026-01-01T00:00:00Z", endsAt: "2027-01-01T00:00:00Z", observedAt: now, status: "ACTIVE", version: 0 };
const review = { id: secondId, productId: firstId, body: "Fixture experience", rating: 4, status: "VISIBLE", version: 0, ownedByActor: false, createdAt: now, updatedAt: now };
const paged = (items: unknown[], page = 0) => ({ items, page, size: 12, totalElements: items.length, totalPages: items.length ? 1 : 0 });

async function installHardware(page: Page, options: { empty?: boolean; status?: number; conflict?: boolean; workshop?: boolean; seeded?: boolean; rejectCategory?: string } = {}) {
  let computer: typeof profile | undefined = options.empty ? undefined : { ...profile };
  let components: Array<Record<string, unknown>> = options.seeded ? seededComponents() : [];
  const catalog = [...products, ...workshopParts];
  let reviews: Array<typeof review> = [{ ...review }];
  let privacy = { profileId, profileVersion: 0, visibility: "PRIVATE", fields: {}, categories: {} };
  const calls: Array<{ method: string; path: string; body: Record<string, unknown>; query: string }> = [];
  async function respond(route: Route, value: unknown, status = 200) {
    await route.fulfill({ status, contentType: "application/json", headers: { "Access-Control-Allow-Origin": new URL(page.url()).origin, "Access-Control-Allow-Credentials": "true" }, ...(status === 204 ? {} : { body: JSON.stringify(value) }) });
  }
  await page.route("**/api/v1/hardware/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1/hardware", "");
    const method = request.method();
    const body = request.postData() ? request.postDataJSON() : {};
    calls.push({ method, path, body, query: url.search });
    if (method === "OPTIONS") return respond(route, null, 204);
    if (options.status) return respond(route, { code: "TEST_ERROR" }, options.status);
    if (path === "/catalog/brands") return respond(route, paged([]));
    if (path === "/catalog/facets") return respond(route, { category: "CPU", facets: { brandId: [{ value: "30000000-0000-0000-0000-000000000001", label: "Maker", count: 1 }], coreCount: [{ value: "4", label: "4", count: 1 }, { value: "8", label: "8", count: 1 }] } });
    if (path === "/catalog/products") return respond(route, paged(options.workshop ? workshopParts.filter((item) => item.category === url.searchParams.get("category")) : options.empty ? [] : products));
    if (/^\/catalog\/products\/[^/]+$/.test(path)) return respond(route, catalog.find((item) => path.endsWith(item.id)) ?? {}, catalog.some((item) => path.endsWith(item.id)) ? 200 : 404);
    if (path === "/comparisons/catalog-products") return respond(route, { left: { ...products[0], modelName: products[0].model }, right: { ...products[1], modelName: products[1].model } });
    if (path === "/comparisons/components") return respond(route, { left: { ...products[0], modelName: products[0].model }, right: { ...products[1], modelName: products[1].model } });
    if (path === "/profiles/statistics") return respond(route, { totalProfiles: computer ? 1 : 0, profilesByDeviceType: {}, totalComponentEntries: components.length, totalComponentUnits: components.reduce((sum, item) => sum + Number(item.quantity), 0), componentsByCategory: {}, recordedRamCapacityGb: 0, ramUnitsWithoutCapacity: 0, recordedStorageCapacityGb: 0, storageUnitsWithoutCapacity: 0 });
    if (path === "/profiles" && method === "GET") return respond(route, paged(computer ? [computer] : []));
    if (path === "/profiles" && method === "POST") { computer = { ...profile, ...body }; return respond(route, computer, 201); }
    if (path === `/profiles/${profileId}`) {
      if (!computer) return respond(route, {}, 404);
      if (method === "PUT") { expect(body.profileVersion).toBe(computer.version); computer = { ...computer, name: body.name, deviceType: body.deviceType, caseColor: body.caseColor ?? computer.caseColor, version: computer.version + 1 }; return respond(route, computer); }
      if (method === "DELETE") { expect(Number(url.searchParams.get("profileVersion"))).toBe(computer.version); computer = undefined; components = []; return respond(route, null, 204); }
      return respond(route, { profile: computer, components, page: 0, size: 20, totalComponents: components.length, totalPages: components.length ? 1 : 0 });
    }
    if (path.includes("/components")) {
      if (options.conflict) return respond(route, { code: "DATA_CONFLICT" }, 409);
      if (!computer) return respond(route, {}, 404);
      // Sunucu doğrulaması bu kategorideki parçayı reddeder (profil oluşmuş, kayıt yarım kalmıştır).
      if (options.rejectCategory && method !== "DELETE" && body.category === options.rejectCategory) return respond(route, { code: "VALIDATION_FAILED" }, 422);
      if (method === "DELETE") { expect(Number(url.searchParams.get("profileVersion"))).toBe(computer.version); components = components.filter((item) => !path.endsWith(String(item.id))); computer.version++; return respond(route, null, 204); }
      expect(body.profileVersion).toBe(computer.version);
      const componentId = method === "PUT" ? path.split("/").at(-1) : `00000000-0000-0000-0000-00000000000${components.length + 3}`;
      const item = { id: componentId, ...body, modelName: body.modelName ?? catalog.find((entry) => entry.id === body.productId)?.model ?? products[0].model, brandName: body.brandName ?? null, brandId: body.brandId ?? null, productId: body.productId ?? null, source: "MANUAL", version: 0, specifications: { ...specs, ...body.specifications } };
      components = [...components.filter((entry) => entry.id !== componentId), item]; computer.version++;
      return respond(route, { component: item, profileVersion: computer.version });
    }
    if (path.endsWith("/privacy")) {
      if (method === "PUT") { expect(body.profileVersion).toBe(computer?.version); computer!.version++; privacy = { ...privacy, ...body, profileVersion: computer!.version }; }
      return respond(route, { ...privacy, profileVersion: computer?.version ?? 0 });
    }
    if (path === "/reviews/mine") return respond(route, paged(reviews.filter((item) => item.ownedByActor)));
    if (path.endsWith("/reports")) return respond(route, { id: firstId, deliveryStatus: "PENDING" }, 201);
    if (path === `/catalog/products/${firstId}/reviews`) {
      if (method === "POST") { reviews.push({ ...review, id: firstId, body: body.body, rating: body.rating, ownedByActor: true }); return respond(route, reviews.at(-1), 201); }
      return respond(route, { ...paged(options.empty ? [] : reviews), summary: { ratedReviews: reviews.length, averageRating: 4 } });
    }
    if (path.startsWith("/reviews/")) {
      if (method === "PUT") { const existing = reviews.find((item) => path.endsWith(item.id))!; expect(body.reviewVersion).toBe(existing.version); Object.assign(existing, { body: body.body, rating: body.rating, version: existing.version + 1 }); return respond(route, existing); }
      if (method === "DELETE") { const existing = reviews.find((item) => path.endsWith(item.id))!; expect(Number(url.searchParams.get("reviewVersion"))).toBe(existing.version); reviews = reviews.filter((item) => !path.endsWith(item.id)); return respond(route, null, 204); }
    }
    if (path === "/campaigns") return respond(route, paged(options.empty ? [] : [campaign]));
    if (path === `/campaigns/${secondId}`) return respond(route, campaign);
    return respond(route, { code: "UNEXPECTED_TEST_REQUEST" }, 404);
  });
  return calls;
}

test("katalog arama/filtre URL'si, karşılaştırma ve dil değişimi", async ({ page }) => {
  const calls = await installHardware(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/tr/donanimlar?category=CPU");
  await expect(page.getByRole("heading", { name: "Fixture CPU 1" })).toBeVisible();
  await page.getByRole("searchbox", { name: "Model ara" }).fill("new model");
  await expect.poll(() => calls.some((call) => call.query.includes("q=new+model"))).toBe(true);
  // Filtre paneli varsayılan kapalıdır; düğmeyle açılır.
  await page.getByRole("button", { name: "Filtreler", exact: true }).click();
  await page.locator('input[name="minClockMhz"]').fill("5000");
  await page.locator('input[name="maxClockMhz"]').fill("3000");
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page.locator('input[name="maxClockMhz"]:invalid')).toHaveCount(1);
  await page.locator('input[name="minClockMhz"]').fill("2000");
  await page.getByRole("combobox", { name: "Çekirdek sayısı", exact: true }).click();
  await page.getByRole("option", { name: "8", exact: true }).click();
  await page.keyboard.press("Escape");
  await page.getByRole("combobox", { name: "Marka", exact: true }).click();
  await page.getByRole("option", { name: "Maker", exact: true }).click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page).toHaveURL(/brandId=30000000/);
  expect(new URL(page.url()).searchParams.get("category")).toBe("CPU");
  expect(new URL(page.url()).searchParams.getAll("coreCount")).toEqual(["8"]);
  expect(new URL(page.url()).searchParams.get("minClockMhz")).toBe("2000");
  await page.goBack();
  await expect(page).not.toHaveURL(/brandId=30000000/);
  await page.goForward();
  await expect(page).toHaveURL(/brandId=30000000/);
  await page.getByRole("button", { name: "Karşılaştırmaya ekle", exact: true }).nth(0).click();
  await page.getByRole("button", { name: "Karşılaştırmaya ekle", exact: true }).click();
  await page.getByRole("button", { name: "Karşılaştırmayı aç", exact: true }).click();
  await expect(page).toHaveURL(/\/tr\/donanimlar\/karsilastir\?ids=/);
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const view = page.getByTestId("comparison-view");
  await expect(view).toBeVisible();
  await expect(view.getByRole("columnheader", { name: /Fixture CPU 1/ })).toBeVisible();
  await expect(view.getByRole("status")).toContainText("aynı");
  await expect(view.getByRole("button", { name: "Yalnız farklıları göster" })).toBeVisible();
  await page.getByRole("link", { name: "Fixture CPU 1", exact: true }).click();
  await expect(page).toHaveURL(`/tr/donanimlar/${firstId}`);
  await expect(page.getByRole("heading", { name: "Fixture CPU 1", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page).toHaveURL(`/en/hardware/${firstId}`);
  await expect(page.getByRole("heading", { name: "Technical specifications" })).toBeVisible();
});

/** Atölye altındaki klasik kayıt yönetimini açar; bileşen CRUD, gizlilik ve karşılaştırma burada kalır. */
async function openAdvanced(page: Page) {
  const advanced = page.getByTestId("workshop-advanced");
  await advanced.locator("summary").click();
  await expect(advanced).toHaveAttribute("open", "");
  return advanced;
}

test("bilgisayar ve bileşen CRUD; opsiyonel marka, sürüm ve gizlilik", async ({ page }) => {
  const calls = await installHardware(page, { empty: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  await page.getByTestId("workshop-first-run").getByRole("button", { name: "Kuruluma başla" }).click();
  await expect(page.getByLabel("Bilgisayar adı", { exact: true })).toBeFocused();
  await page.getByLabel("Bilgisayar adı", { exact: true }).fill("My test computer");
  await page.getByRole("button", { name: "Bilgisayarı kaydet", exact: true }).click();
  await expect(page.getByRole("button", { name: "My test computer", pressed: true })).toBeVisible();
  expect(calls.find((call) => call.method === "POST" && call.path === "/profiles")!.body).toEqual({ name: "My test computer", deviceType: "DESKTOP", caseColor: "BLACK" });
  const advanced = await openAdvanced(page);
  await expect(advanced.getByRole("heading", { name: "My test computer" })).toBeVisible();
  await advanced.getByRole("button", { name: "Bileşen ekle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Manual model");
  await page.getByLabel("Adet", { exact: true }).fill("2");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(advanced.getByRole("heading", { name: "Manual model" })).toBeVisible();
  expect(calls.find((call) => call.method === "POST" && call.path.endsWith("/components"))!.body).not.toHaveProperty("brandId");
  await advanced.getByRole("button", { name: "Gizlilik", exact: true }).click();
  await page.getByLabel("Bilgisayarın görünürlüğü").selectOption("FRIENDS");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  expect(calls.find((call) => call.method === "PUT" && call.path.endsWith("/privacy"))!.body).toMatchObject({ profileVersion: 1, visibility: "FRIENDS" });
  await advanced.getByRole("article").filter({ has: page.getByRole("heading", { name: "Manual model" }) }).getByRole("button", { name: "Düzenle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Manual model updated");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(advanced.getByRole("heading", { name: "Manual model updated" })).toBeVisible();
  await advanced.getByRole("article").filter({ has: page.getByRole("heading", { name: "Manual model updated" }) }).getByRole("button", { name: "Sil", exact: true }).click();
  await page.getByRole("button", { name: "Silmeyi onayla" }).click();
  await expect(advanced.getByRole("heading", { name: "Bileşenlerin için hazır" })).toBeVisible();
  await advanced.getByRole("button", { name: "Sil", exact: true }).click();
  await page.getByRole("button", { name: "Silmeyi onayla" }).click();
  await expect(page.getByRole("heading", { name: "İlk bilgisayarını kur" })).toBeVisible();
});

test("409 bileşen çakışmasında form ve girilen model korunur", async ({ page }) => {
  await installHardware(page, { conflict: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  const advanced = await openAdvanced(page);
  await advanced.getByRole("button", { name: "Bileşen ekle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Unsaved model");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Kayıt değişti");
  await expect(page.getByLabel("Model adı", { exact: true })).toHaveValue("Unsaved model");
  await expect(page.getByRole("button", { name: "Kaydet", exact: true })).toBeDisabled();
});

test("katalogdan bileşen seçimi üreticiyi zorunlu tutmaz ve teknik karşılaştırma çalışır", async ({ page }) => {
  const calls = await installHardware(page);
  await page.goto("/tr/profil/bilgisayarlarim");
  const advanced = await openAdvanced(page);
  for (const model of ["Fixture CPU 1", "Fixture CPU 2"]) {
    await advanced.getByRole("button", { name: "Bileşen ekle" }).click();
    await page.getByLabel("Ekleme yöntemi").selectOption("catalog");
    await page.getByRole("radio", { name: model, exact: true }).check();
    await expect(page.getByText(`Seçilen model: ${model}`, { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Kaydet", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
  }
  const saved = calls.filter((call) => call.method === "POST" && call.path.endsWith("/components"));
  expect(saved).toHaveLength(2);
  for (const entry of saved) { expect(entry.body.productId).toBeTruthy(); expect(entry.body).not.toHaveProperty("brandId"); expect(entry.body).not.toHaveProperty("modelName"); }
  await advanced.getByRole("button", { name: "Bileşenleri karşılaştır" }).click();
  const second = page.getByLabel("İkinci bileşen");
  await second.selectOption({ index: 1 });
  await page.getByRole("dialog").getByRole("button", { name: "Karşılaştır", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("table")).toBeVisible();
  expect(calls.find((call) => call.path === "/comparisons/components")!.body).toMatchObject({ left: { profileId }, right: { profileId } });
});

/** Konsol hataları ve hydration uyarıları; atölye testlerinde boş kalmalıdır. */
function collectProblems(page: Page) {
  const problems: string[] = [];
  page.on("pageerror", (error) => problems.push(error.message));
  page.on("console", (message) => { if (message.type() === "error" || /hydrat/i.test(message.text())) problems.push(message.text()); });
  return problems;
}

async function pickPart(page: Page, trigger: string, model: string) {
  await page.getByTestId("workshop-builder").getByRole("button", { name: trigger, exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: new RegExp(model) }).click();
  await expect(dialog).toHaveCount(0);
}

/** Renk radyosu görsel olarak gizlidir; kullanıcı gibi etiketine tıklanır ve seçim doğrulanır. */
async function pickColor(page: Page, label: string) {
  await page.getByRole("group", { name: "Kasa rengi" }).getByText(label, { exact: true }).click();
  await expect(page.getByRole("radio", { name: label })).toBeChecked();
}

test("atölye: eksik parça rehberi, tam kurulum kaydı, otomatik montaj ve yeniden oynatma", async ({ page }) => {
  test.setTimeout(90_000);
  const problems = collectProblems(page);
  const calls = await installHardware(page, { empty: true, workshop: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  const stage = page.getByTestId("workshop-stage");
  await expect(stage).toHaveAttribute("data-assembly", "incomplete");
  await expect(page.getByTestId("workshop-missing")).toContainText("Montaja başlamak için parçalarını seç");
  await expect(page.getByTestId("workshop-status")).toHaveText("Montaj için 5 parça eksik.");
  await expect(page.getByRole("button", { name: "Montajı başlat" })).toBeDisabled();

  await page.getByLabel("Bilgisayar adı", { exact: true }).fill("Atölye kasası");
  await pickColor(page, "Beyaz");
  await expect(stage).toHaveAttribute("data-case-color", "WHITE");
  // Rehberdeki düğme ilk eksik parçanın (form sırasıyla işlemci) seçicisini açar.
  await page.getByTestId("workshop-missing").getByRole("button", { name: "Eksik parçayı seç" }).click();
  await expect(page.getByRole("dialog")).toContainText("İşlemci");
  await page.getByRole("dialog").getByRole("button", { name: /Ryzen 7 7800X3D/ }).click();
  await expect(stage).toHaveAttribute("data-cpu-vendor", "AMD");
  await pickPart(page, "Anakart seç", "B650 Tomahawk");
  await pickPart(page, "Bellek seç", "Vengeance 32 GB");
  await expect(stage).toHaveAttribute("data-ram-sticks", "2");
  await pickPart(page, "Ekran kartı seç", "GeForce RTX 4070 Super");
  await expect(page.getByTestId("workshop-status")).toHaveText("Montaj için 1 parça eksik.");
  await pickPart(page, "Depolama seç", "990 Pro 2 TB");
  await expect(page.getByTestId("workshop-missing")).toHaveCount(0);
  await expect(stage).toHaveAttribute("data-gpu-vendor", "NVIDIA");

  await page.getByRole("button", { name: "Bilgisayarı kaydet" }).click();
  await expect(page.getByTestId("workshop-summary")).toBeVisible();
  expect(calls.find((call) => call.method === "POST" && call.path === "/profiles")!.body).toEqual({ name: "Atölye kasası", deviceType: "DESKTOP", caseColor: "WHITE" });
  const saved = calls.filter((call) => call.method === "POST" && call.path.endsWith("/components"));
  expect(saved.map((call) => [call.body.category, call.body.profileVersion])).toEqual([["MOTHERBOARD", 0], ["CPU", 1], ["RAM", 2], ["STORAGE", 3], ["GPU", 4]]);
  await expect(page.getByRole("heading", { name: "Kurulumu düzenle" })).toBeVisible();
  await expect(page.getByText("Kurulum kaydedildi.")).toBeVisible();
  await expect(page.getByTestId("workshop-first-run")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Atölye kasası", pressed: true })).toBeVisible();
  const summary = page.getByTestId("workshop-summary");
  await expect(summary.getByRole("heading", { name: "Bilgisayar Bilgilerim" })).toBeVisible();
  await expect(summary).toContainText("Atölye kasası / Beyaz kasa");
  for (const text of ["360 mm sıvı soğutma", "7 kasa fanı"]) await expect(summary).toContainText(text);

  // Headless Chromium WebGL'i yazılımla sunar; yeni bilgisayar sahnesi kurulduğunda montaj kendiliğinden oynar.
  // Sahne görünür alanda değilken çizim (ve montaj zamanı) durur; kayıttan sonra sayfa özetteyse sahneye dönülür.
  await expect(stage).toHaveAttribute("data-stage-mode", "3d", { timeout: 30_000 });
  await stage.scrollIntoViewIfNeeded();
  await expect(stage).toHaveAttribute("data-assembly", /playing|done/, { timeout: 15_000 });
  await expect(stage).toHaveAttribute("data-assembly", "done", { timeout: 30_000 });
  await expect(page.getByTestId("workshop-status")).toHaveText("Montaj tamamlandı.");
  await expect(page.getByRole("list", { name: "Montaj sırası" }).locator('[data-state="done"]')).toHaveCount(9);
  await page.screenshot({ path: "test-results/screens/workshop-final-1440-tr.png" });

  await page.getByRole("button", { name: "Montajı yeniden oynat" }).first().click();
  await expect(stage).toHaveAttribute("data-assembly", "playing");
  await expect(page.getByTestId("workshop-status")).toContainText("Takılıyor:");
  await page.getByRole("button", { name: "Montajı atla" }).click();
  await expect(stage).toHaveAttribute("data-assembly", "done");
  await page.getByRole("button", { name: "Görsel görünüm" }).click();
  await expect(stage).toHaveAttribute("data-stage-mode", "image");
  await expect(stage.getByRole("img", { name: "Beyaz kasa içinde tamamlanmış LTZ Atölyesi kurulumu" })).toBeVisible();

  // Yenileme sonrası kurulum sunucudan geri okunur: ad, kasa rengi ve beş parça korunur, ilk kurulum çağrısı dönmez.
  await page.reload();
  await expect(page.getByRole("button", { name: "Atölye kasası", pressed: true })).toBeVisible();
  await expect(page.getByTestId("workshop-first-run")).toHaveCount(0);
  await expect(page.getByTestId("workshop-summary")).toContainText("Atölye kasası / Beyaz kasa");
  await expect(stage).toHaveAttribute("data-case-color", "WHITE");
  for (const [slot, model] of [["CPU", "Ryzen 7 7800X3D"], ["MOTHERBOARD", "B650 Tomahawk"], ["RAM", "Vengeance 32 GB"], ["GPU", "GeForce RTX 4070 Super"], ["STORAGE", "990 Pro 2 TB"]]) {
    await expect(page.getByTestId(`workshop-info-${slot}`)).toContainText(model);
  }
  expect(problems).toEqual([]);
});

test("atölye: parça kaydı reddedilirse başarı yazmaz; oluşan profil listede kalır, seçimler korunur", async ({ page }) => {
  test.setTimeout(90_000);
  const calls = await installHardware(page, { empty: true, workshop: true, rejectCategory: "STORAGE" });
  await page.goto("/tr/profil/bilgisayarlarim");
  await expect(page.getByTestId("workshop-first-run")).toBeVisible();
  await page.getByLabel("Bilgisayar adı", { exact: true }).fill("Yarım kasa");
  for (const [trigger, model] of [["İşlemci seç", "Ryzen 7 7800X3D"], ["Anakart seç", "B650 Tomahawk"], ["Bellek seç", "Vengeance 32 GB"], ["Ekran kartı seç", "GeForce RTX 4070 Super"], ["Depolama seç", "990 Pro 2 TB"]]) await pickPart(page, trigger, model);
  await page.getByRole("button", { name: "Bilgisayarı kaydet" }).click();
  const builder = page.getByTestId("workshop-builder");
  await expect(builder.getByRole("alert")).toBeVisible();
  await expect(page.getByText("Kurulum kaydedildi.")).toHaveCount(0);
  // Profil sunucuda oluştu: liste ve Bilgisayar Bilgilerim dolar, ilk kurulum çağrısı ve boş durum görünmez.
  await expect(page.getByRole("button", { name: "Yarım kasa", pressed: true })).toBeVisible();
  await expect(page.getByTestId("workshop-first-run")).toHaveCount(0);
  await expect(page.getByTestId("workshop-summary")).toContainText("Ryzen 7 7800X3D");
  // Kaydedilemeyen depolama seçimi taslakta kalır; yeniden denemede yalnız o gider.
  await expect(page.getByTestId("workshop-slot-STORAGE")).toContainText("990 Pro 2 TB");
  await expect(page.getByText("Kaydedilmemiş değişiklikler var.")).toBeVisible();
  expect(calls.filter((call) => call.method === "POST" && call.path === "/profiles")).toHaveLength(1);
  await page.getByRole("button", { name: "Değişiklikleri kaydet" }).click();
  await expect(builder.getByRole("alert")).toBeVisible();
  expect(calls.filter((call) => call.method === "POST" && call.path === "/profiles")).toHaveLength(1);
  expect(calls.filter((call) => call.path.endsWith("/components")).map((call) => call.body.category)).toEqual(["MOTHERBOARD", "CPU", "RAM", "STORAGE", "STORAGE"]);
  await page.reload();
  await expect(page.getByRole("button", { name: "Yarım kasa", pressed: true })).toBeVisible();
  await expect(page.getByTestId("workshop-missing")).toBeVisible();
});

test("atölye: kayıtlı bilgisayar geri yüklenir; ekran kartı ve kasa rengi düzenlenir, parça kaldırılır", async ({ page }) => {
  test.setTimeout(90_000);
  const problems = collectProblems(page);
  // Oturumda daha önce oynatılmış gibi: açılışta otomatik montaj yok, final görünüm gelir.
  await page.addInitScript((id) => sessionStorage.setItem(`ltz-workshop-played:${id}`, "1"), profileId);
  const calls = await installHardware(page, { workshop: true, seeded: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  const stage = page.getByTestId("workshop-stage");
  await expect(page.getByRole("heading", { name: "Kurulumu düzenle" })).toBeVisible();
  await expect(stage).toHaveAttribute("data-case-color", "BLACK");
  await expect(stage).toHaveAttribute("data-gpu-vendor", "NVIDIA");
  await expect(page.getByTestId("workshop-missing")).toHaveCount(0);
  await expect(page.getByTestId("workshop-slot-GPU")).toContainText("GeForce RTX 4070 Super");
  await expect(page.getByTestId("workshop-info-RAM")).toContainText("Vengeance 32 GB");
  await expect(stage).toHaveAttribute("data-stage-mode", "3d", { timeout: 30_000 });
  await expect(stage).toHaveAttribute("data-assembly", "idle");
  await expect(page.getByTestId("workshop-status")).toHaveText("Kurulum hazır. Montajı başlatabilirsin.");

  await pickPart(page, "Ekran kartı değiştir", "Radeon RX 7800 XT");
  await pickColor(page, "Kırmızı");
  await expect(stage).toHaveAttribute("data-gpu-vendor", "AMD");
  await expect(stage).toHaveAttribute("data-case-color", "RED");
  await expect(page.getByText("Kaydedilmemiş değişiklikler var.")).toBeVisible();
  await page.getByRole("button", { name: "Değişiklikleri kaydet" }).click();
  await expect(page.getByText("Kurulum kaydedildi.")).toBeVisible();
  expect(calls.find((call) => call.method === "PUT" && call.path === `/profiles/${profileId}`)!.body).toEqual({ name: "Fixture computer", deviceType: "DESKTOP", caseColor: "RED", profileVersion: 0 });
  const gpu = calls.find((call) => call.method === "PUT" && call.path.includes("/components/"))!;
  expect(gpu.path.endsWith(seededComponents()[4].id)).toBe(true);
  expect(gpu.body).toMatchObject({ category: "GPU", productId: workshopParts[5].id, profileVersion: 1 });
  // Düzenleme kaydından sonra aynı sahne montajı baştan oynatır.
  await expect(stage).toHaveAttribute("data-assembly", /playing|done/);
  await expect(page.getByTestId("workshop-info-GPU")).toContainText("Radeon RX 7800 XT");
  // Düzenleme yenilemeden sonra da korunur.
  await page.reload();
  await expect(stage).toHaveAttribute("data-case-color", "RED");
  await expect(page.getByTestId("workshop-info-GPU")).toContainText("Radeon RX 7800 XT");
  await expect(page.getByTestId("workshop-summary")).toContainText("Kırmızı kasa");

  await page.getByTestId("workshop-summary").getByRole("button", { name: "Depolama kaldır" }).click();
  const dialog = page.getByRole("dialog", { name: "Parçayı kaldır" });
  await expect(dialog).toContainText("990 Pro 2 TB");
  await dialog.getByRole("button", { name: "Kaldır", exact: true }).click();
  await expect(page.getByTestId("workshop-missing")).toBeVisible();
  await expect(stage).toHaveAttribute("data-assembly", "incomplete");
  expect(calls.some((call) => call.method === "DELETE" && call.path.endsWith(seededComponents()[3].id))).toBe(true);
  expect(problems).toEqual([]);
});

for (const [width, theme, locale] of [[375, "light", "en"], [768, "dark", "tr"], [1440, "light", "en"]] as const) {
  test(`atölye hareket azaltma ${width} ${theme} ${locale}: animasyon oynamaz, final görünüm ve taşma yok`, async ({ page }) => {
    test.setTimeout(90_000);
    const problems = collectProblems(page);
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript((value) => { localStorage.setItem("ltz-theme", value); localStorage.setItem("ltz-transition", "minimal"); }, theme);
    await installHardware(page, { workshop: true, seeded: true });
    await page.goto(locale === "tr" ? "/tr/profil/bilgisayarlarim" : "/en/profile/computers");
    const stage = page.getByTestId("workshop-stage");
    await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
    await expect(page.getByRole("heading", { name: locale === "tr" ? "Bilgisayar Bilgilerim" : "My computer details" })).toBeVisible();
    await expect(stage).toHaveAttribute("data-stage-mode", /3d|image/, { timeout: 30_000 });
    await expect(page.getByTestId("workshop-status")).not.toHaveText(/yükleniyor|Loading/, { timeout: 30_000 });
    await expect(stage).not.toHaveAttribute("data-assembly", "playing");
    if (await stage.getAttribute("data-stage-mode") === "3d") {
      await expect(page.getByTestId("workshop-status")).toHaveText(locale === "tr" ? "Hareket azaltma açık; montaj animasyonsuz gösteriliyor." : "Reduced motion is on; the build is shown without animation.");
      await expect(page.getByRole("button", { name: locale === "tr" ? "Fanları çalıştır" : "Spin fans" })).toHaveCount(0);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/screens/workshop-${width}-${theme}-${locale}.png`, fullPage: true });
    expect(problems).toEqual([]);
  });
}

test("boş katalog, bilgisayar ve kampanya kayıtları örnek veriye dönüşmez", async ({ page }) => {
  await installHardware(page, { empty: true });
  for (const [path, heading] of [["/tr/donanimlar", "Bu seçimde donanım bulunamadı"], ["/tr/profil/bilgisayarlarim", "İlk bilgisayarını kur"], ["/tr/donanimlar/kampanyalar", "Şu an geçerli kampanya yok"]]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible();
    await expect(page.locator("main")).not.toContainText("Fixture");
  }
});

test("mobil filtre diyaloğu Escape ile kapanır ve odağı tetikleyiciye verir", async ({ page }) => {
  await installHardware(page);
  await page.setViewportSize({ width: 375, height: 900 });
  await page.goto("/tr/donanimlar");
  const opener = page.getByRole("button", { name: "Filtreler", exact: true });
  await opener.click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(opener).toBeFocused();
});

test("yorum oluşturma, owner düzenleme/silme ve yabancı yorum raporu", async ({ page }) => {
  const calls = await installHardware(page);
  await page.goto(`/tr/donanimlar/${firstId}`);
  await page.getByRole("button", { name: "Deneyimini paylaş" }).click();
  await page.getByLabel("Deneyimin", { exact: true }).fill("My test experience");
  const stars = page.getByRole("slider", { name: "Puan" });
  await stars.focus();
  await page.keyboard.press("End");
  await expect(page.getByTestId("star-rating-text")).toHaveText("5 / 5");
  await page.keyboard.press("ArrowLeft");
  await expect(page.getByTestId("star-rating-text")).toHaveText("4,5 / 5");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Emin misiniz?" })).toBeVisible();
  await page.getByRole("button", { name: "Evet, kaydet" }).click();
  const own = page.getByRole("article").filter({ hasText: "My test experience" });
  await own.getByRole("button", { name: "Düzenle" }).click();
  await page.getByLabel("Deneyimin", { exact: true }).fill("Updated experience");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await page.getByRole("button", { name: "Evet, kaydet" }).click();
  await expect(page.getByText("Updated experience", { exact: true })).toBeVisible();
  await page.getByRole("article").filter({ hasText: "Updated experience" }).getByRole("button", { name: "Sil", exact: true }).click();
  await page.getByRole("button", { name: "Silmeyi onayla" }).click();
  await expect(page.getByText("Updated experience", { exact: true })).not.toBeVisible();
  await page.getByRole("article").filter({ hasText: "Fixture experience" }).getByRole("button", { name: "Raporla" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Raporla" }).click();
  await expect(page.getByRole("dialog")).toContainText("teslimi henüz doğrulanmadı");
  expect(calls.some((call) => call.path.endsWith("/reports") && call.body.reason === "SPAM")).toBe(true);
});

for (const status of [401, 403, 404, 429, 500, 503]) {
  test(`hata ${status}: özel veri ve oluşturma eylemi açılmaz`, async ({ page }) => {
    await installHardware(page, { status });
    await page.goto("/tr/profil/bilgisayarlarim");
    await expect(page.getByRole("alert").first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Kuruluma başla" })).toHaveCount(0);
    await expect(page.getByTestId("workshop-builder")).toHaveCount(0);
    await expect(page.getByText("Fixture computer")).toHaveCount(0);
  });
}

test("kampanya detayı gerçek detail isteğini kullanır; forum eylemi uydurulmaz", async ({ page }) => {
  const calls = await installHardware(page);
  await page.goto("/tr/donanimlar/kampanyalar");
  await page.getByRole("button", { name: "Fixture campaign" }).click();
  await expect(page.getByRole("dialog").getByRole("link", { name: "Kaynağa git" })).toHaveAttribute("href", campaign.sourceUrl);
  expect(calls.some((call) => call.path === `/campaigns/${secondId}`)).toBe(true);
  await expect(page.getByRole("button", { name: /paylaş|oy ver/i })).toHaveCount(0);
});

for (const width of [375, 768, 1440]) for (const theme of ["dark", "light"]) for (const locale of ["tr", "en"]) {
  test(`Hardware görsel QA ${width} ${theme} ${locale}`, async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await installHardware(page);
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript((value) => { localStorage.setItem("ltz-theme", value); localStorage.setItem("ltz-transition", "minimal"); }, theme);
    const paths = locale === "tr" ? ["/tr/donanimlar", `/tr/donanimlar/${firstId}`, "/tr/profil/bilgisayarlarim", "/tr/donanimlar/kampanyalar"] : ["/en/hardware", `/en/hardware/${firstId}`, "/en/profile/computers", "/en/hardware/campaigns"];
    for (const [index, path] of paths.entries()) {
      await page.goto(path);
      await expect(page.locator("main h1")).toBeVisible();
      await expect(page.getByRole("status").filter({ hasText: /yükleniyor|Loading/ })).toHaveCount(0);
      await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
      await page.screenshot({ path: `test-results/screens/hardware-${index}-${width}-${theme}-${locale}.png`, fullPage: true });
    }
    expect(errors).toEqual([]);
  });
}
