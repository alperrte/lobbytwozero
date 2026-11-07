import { expect, test, type Page, type Route } from "@playwright/test";

// Test-only backend fixtures. No fixture is imported by the application.
const firstId = "00000000-0000-0000-0000-000000000001";
const secondId = "00000000-0000-0000-0000-000000000002";
const profileId = "00000000-0000-0000-0000-000000000010";
const now = "2026-11-07T00:00:00Z";
const specs = { capacityGb: null, clockMhz: 3600, coreCount: 8, powerWatts: 65, memoryType: null, socket: "Test socket", interfaceType: null, formFactor: null };
const products = [firstId, secondId].map((id, index) => ({ id, category: "CPU", model: `Fixture CPU ${index + 1}`, brands: [], specifications: specs, status: "ACTIVE", version: 0 }));
const profile = { id: profileId, name: "Fixture computer", deviceType: "DESKTOP", source: "MANUAL", version: 0, createdAt: now, updatedAt: now };
const campaign = { id: secondId, productId: firstId, title: "Fixture campaign", sourceName: "Fixture store", sourceUrl: "https://store.example.test/offer", originalPrice: 200, salePrice: 150, currency: "TRY", discountPercent: 25, startsAt: "2026-01-01T00:00:00Z", endsAt: "2027-01-01T00:00:00Z", observedAt: now, status: "ACTIVE", version: 0 };
const review = { id: secondId, productId: firstId, body: "Fixture experience", rating: 4, status: "VISIBLE", version: 0, ownedByActor: false, createdAt: now, updatedAt: now };
const paged = (items: unknown[], page = 0) => ({ items, page, size: 12, totalElements: items.length, totalPages: items.length ? 1 : 0 });

async function installHardware(page: Page, options: { empty?: boolean; status?: number; conflict?: boolean } = {}) {
  let computer: typeof profile | undefined = options.empty ? undefined : { ...profile };
  let components: Array<Record<string, unknown>> = [];
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
    if (path === "/catalog/products") return respond(route, paged(options.empty ? [] : products));
    if (/^\/catalog\/products\/[^/]+$/.test(path)) return respond(route, products.find((item) => path.endsWith(item.id)) ?? {}, products.some((item) => path.endsWith(item.id)) ? 200 : 404);
    if (path === "/comparisons/catalog-products") return respond(route, { left: { ...products[0], modelName: products[0].model }, right: { ...products[1], modelName: products[1].model } });
    if (path === "/comparisons/components") return respond(route, { left: { ...products[0], modelName: products[0].model }, right: { ...products[1], modelName: products[1].model } });
    if (path === "/profiles/statistics") return respond(route, { totalProfiles: computer ? 1 : 0, profilesByDeviceType: {}, totalComponentEntries: components.length, totalComponentUnits: components.reduce((sum, item) => sum + Number(item.quantity), 0), componentsByCategory: {}, recordedRamCapacityGb: 0, ramUnitsWithoutCapacity: 0, recordedStorageCapacityGb: 0, storageUnitsWithoutCapacity: 0 });
    if (path === "/profiles" && method === "GET") return respond(route, paged(computer ? [computer] : []));
    if (path === "/profiles" && method === "POST") { computer = { ...profile, ...body }; return respond(route, computer, 201); }
    if (path === `/profiles/${profileId}`) {
      if (!computer) return respond(route, {}, 404);
      if (method === "PUT") { expect(body.profileVersion).toBe(computer.version); computer = { ...computer, name: body.name, deviceType: body.deviceType, version: computer.version + 1 }; return respond(route, computer); }
      if (method === "DELETE") { expect(Number(url.searchParams.get("profileVersion"))).toBe(computer.version); computer = undefined; components = []; return respond(route, null, 204); }
      return respond(route, { profile: computer, components, page: 0, size: 20, totalComponents: components.length, totalPages: components.length ? 1 : 0 });
    }
    if (path.includes("/components")) {
      if (options.conflict) return respond(route, { code: "DATA_CONFLICT" }, 409);
      if (!computer) return respond(route, {}, 404);
      if (method === "DELETE") { expect(Number(url.searchParams.get("profileVersion"))).toBe(computer.version); components = components.filter((item) => !path.endsWith(String(item.id))); computer.version++; return respond(route, null, 204); }
      expect(body.profileVersion).toBe(computer.version);
      const componentId = method === "PUT" ? path.split("/").at(-1) : `00000000-0000-0000-0000-00000000000${components.length + 3}`;
      const item = { id: componentId, ...body, modelName: body.modelName ?? products[0].model, brandName: body.brandName ?? null, brandId: body.brandId ?? null, productId: body.productId ?? null, source: "MANUAL", version: 0, specifications: { ...specs, ...body.specifications } };
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
  await page.locator('input[name="minCoreCount"]').fill("12");
  await page.locator('input[name="maxCoreCount"]').fill("4");
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page.locator('input[name="maxCoreCount"]:invalid')).toHaveCount(1);
  await page.locator('input[name="minCoreCount"]').fill("2");
  await page.getByLabel("Üretici adı", { exact: true }).fill("maker");
  await page.getByRole("button", { name: "Filtreleri uygula" }).click();
  await expect(page).toHaveURL(/brand=maker/);
  expect(new URL(page.url()).searchParams.get("category")).toBe("CPU");
  expect(new URL(page.url()).searchParams.get("minCoreCount")).toBe("2");
  await page.goBack();
  await expect(page).not.toHaveURL(/brand=maker/);
  await page.goForward();
  await expect(page).toHaveURL(/brand=maker/);
  await page.getByRole("button", { name: "Karşılaştırmaya ekle", exact: true }).nth(0).click();
  await page.getByRole("button", { name: "Karşılaştırmaya ekle", exact: true }).click();
  await page.getByRole("button", { name: "Karşılaştırmayı aç", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("columnheader", { name: "Fixture CPU 1" })).toBeVisible();
  await expect(dialog).toContainText("Bilinmiyor");
  await page.keyboard.press("Escape"); await expect(dialog).not.toBeVisible();
  await page.getByRole("link", { name: "Fixture CPU 1", exact: true }).click();
  await expect(page).toHaveURL(`/tr/donanimlar/${firstId}`);
  await expect(page.getByRole("heading", { name: "Fixture CPU 1", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page).toHaveURL(`/en/hardware/${firstId}`);
  await expect(page.getByRole("heading", { name: "Technical specifications" })).toBeVisible();
});

test("bilgisayar ve bileşen CRUD; opsiyonel marka, sürüm ve gizlilik", async ({ page }) => {
  const calls = await installHardware(page, { empty: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  await page.getByRole("button", { name: "Bilgisayar ekle" }).click();
  await page.getByLabel("Bilgisayar adı", { exact: true }).fill("My test computer");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("heading", { name: "My test computer" })).toBeVisible();
  await page.getByRole("button", { name: "Bileşen ekle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Manual model");
  await page.getByLabel("Adet", { exact: true }).fill("2");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Manual model" })).toBeVisible();
  expect(calls.find((call) => call.method === "POST" && call.path.endsWith("/components"))!.body).not.toHaveProperty("brandId");
  await page.getByRole("button", { name: "Gizlilik", exact: true }).click();
  await page.getByLabel("Bilgisayarın görünürlüğü").selectOption("FRIENDS");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  expect(calls.find((call) => call.method === "PUT" && call.path.endsWith("/privacy"))!.body).toMatchObject({ profileVersion: 1, visibility: "FRIENDS" });
  await page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Manual model" }) }).getByRole("button", { name: "Düzenle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Manual model updated");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Manual model updated" })).toBeVisible();
  await page.getByRole("article").filter({ has: page.getByRole("heading", { name: "Manual model updated" }) }).getByRole("button", { name: "Sil", exact: true }).click();
  await page.getByRole("button", { name: "Silmeyi onayla" }).click();
  await expect(page.getByRole("heading", { name: "Bileşenlerin için hazır" })).toBeVisible();
  await page.getByRole("button", { name: "Sil", exact: true }).click();
  await page.getByRole("button", { name: "Silmeyi onayla" }).click();
  await expect(page.getByRole("heading", { name: "Bilgisayarını buraya ekle" })).toBeVisible();
});

test("409 bileşen çakışmasında form ve girilen model korunur", async ({ page }) => {
  await installHardware(page, { conflict: true });
  await page.goto("/tr/profil/bilgisayarlarim");
  await page.getByRole("button", { name: "Bileşen ekle" }).click();
  await page.getByLabel("Model adı", { exact: true }).fill("Unsaved model");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Kayıt değişti");
  await expect(page.getByLabel("Model adı", { exact: true })).toHaveValue("Unsaved model");
  await expect(page.getByRole("button", { name: "Kaydet", exact: true })).toBeDisabled();
});

test("katalogdan bileşen seçimi üreticiyi zorunlu tutmaz ve teknik karşılaştırma çalışır", async ({ page }) => {
  const calls = await installHardware(page);
  await page.goto("/tr/profil/bilgisayarlarim");
  for (const model of ["Fixture CPU 1", "Fixture CPU 2"]) {
    await page.getByRole("button", { name: "Bileşen ekle" }).click();
    await page.getByLabel("Ekleme yöntemi").selectOption("catalog");
    await page.getByRole("radio", { name: model, exact: true }).check();
    await expect(page.getByText(`Seçilen model: ${model}`, { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Kaydet", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
  }
  const saved = calls.filter((call) => call.method === "POST" && call.path.endsWith("/components"));
  expect(saved).toHaveLength(2);
  for (const entry of saved) { expect(entry.body.productId).toBeTruthy(); expect(entry.body).not.toHaveProperty("brandId"); expect(entry.body).not.toHaveProperty("modelName"); }
  await page.getByRole("button", { name: "Bileşenleri karşılaştır" }).click();
  const second = page.getByLabel("İkinci bileşen");
  await second.selectOption({ index: 1 });
  await page.getByRole("dialog").getByRole("button", { name: "Karşılaştır", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("table")).toBeVisible();
  expect(calls.find((call) => call.path === "/comparisons/components")!.body).toMatchObject({ left: { profileId }, right: { profileId } });
});

test("boş katalog, bilgisayar ve kampanya kayıtları örnek veriye dönüşmez", async ({ page }) => {
  await installHardware(page, { empty: true });
  for (const [path, heading] of [["/tr/donanimlar", "Bu seçimde donanım bulunamadı"], ["/tr/profil/bilgisayarlarim", "Bilgisayarını buraya ekle"], ["/tr/donanimlar/kampanyalar", "Şu an geçerli kampanya yok"]]) {
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
  await page.getByLabel("Puan (isteğe bağlı)").selectOption("5");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  const own = page.getByRole("article").filter({ hasText: "My test experience" });
  await own.getByRole("button", { name: "Düzenle" }).click();
  await page.getByLabel("Deneyimin", { exact: true }).fill("Updated experience");
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
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
    await expect(page.getByRole("button", { name: "Bilgisayar ekle" })).toHaveCount(0);
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
