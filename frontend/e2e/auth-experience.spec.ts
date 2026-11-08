import { test, expect, type Page, type Route } from "@playwright/test";

/**
 * Auth Experience V2: tam ekran video, yüzen navbar/footer, tek tur ışık izi, giriş sahnesi ve ekranlar arası geçişler.
 * Gerçek Gateway gerekmez; yalnız arayüz davranışı doğrulanır (Gateway Playwright ile taklit edilir).
 */
const GATEWAY = "http://localhost:5080";

async function mockGateway(page: Page, handlers: Record<string, { status: number; body?: unknown }> = {}) {
  await page.route(`${GATEWAY}/**`, async (route: Route) => {
    const request = route.request();
    const cors = {
      "access-control-allow-origin": request.headers()["origin"] ?? "*",
      "access-control-allow-credentials": "true",
      "access-control-allow-headers": "content-type, x-xsrf-token, accept",
      "access-control-allow-methods": "GET, POST, OPTIONS"
    };
    if (request.method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers: cors });
      return;
    }
    const mock = handlers[`${request.method()} ${new URL(request.url()).pathname}`] ?? { status: 404 };
    await route.fulfill({
      status: mock.status,
      headers: { ...cors, "content-type": "application/json" },
      body: mock.body === undefined ? undefined : JSON.stringify(mock.body)
    });
  });
}

async function waitForHydration(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) => key.startsWith("__reactProps"))
  );
}

const snake = (page: Page) => page.getByTestId("auth-card-snake");
const horizontalOverflow = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

test.describe("sahne: tam ekran video, yüzen navbar ve footer", () => {
  test("masaüstünde video bütün ekranı kaplar; navbar ortada yüzer; footer altta; kart sağda", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    const viewport = page.viewportSize()!;
    const backdrop = await page.getByTestId("auth-backdrop").boundingBox();
    expect(backdrop).toMatchObject({ x: 0, y: 0, width: viewport.width, height: viewport.height });

    const bar = page.getByRole("banner", { name: "Üst çubuk" });
    const barBox = (await bar.boundingBox())!;
    expect(barBox.x).toBeGreaterThan(100); // kenarlardan ayrık
    expect(Math.abs(barBox.x + barBox.width / 2 - viewport.width / 2)).toBeLessThan(2); // yatayda ortalı
    expect(barBox.width).toBeLessThanOrEqual(960);

    const footerBox = (await page.getByRole("contentinfo").boundingBox())!;
    expect(footerBox.y + footerBox.height).toBeGreaterThan(viewport.height - 30); // içerik kısa: en altta

    const cardBox = (await page.getByRole("heading", { level: 1, name: "Giriş yap" }).locator("xpath=../..").boundingBox())!;
    expect(cardBox.x).toBeGreaterThan(viewport.width / 2);
    expect(await horizontalOverflow(page)).toBe(0);
  });

  test("sol karşılama başlığı ekran okuyucuya bir kez okunur ve animasyondan sonra sabittir", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    // Erişilebilirlik ağacında metin bir kez bulunur (harf katmanı aria-hidden).
    const snapshot = await page.getByRole("region", { name: "Karşılama" }).ariaSnapshot();
    expect(snapshot.match(/Lobine hoş geldin/g)).toHaveLength(1);
    await page.waitForTimeout(2200);
    const transforms = await page.$$eval(".auth-twist-char", (nodes) => nodes.map((node) => getComputedStyle(node).transform));
    expect(transforms.length).toBeGreaterThan(10);
    for (const value of transforms) {
      expect(["none", "matrix(1, 0, 0, 1, 0, 0)"]).toContain(value); // birim dönüşüm: harf yerine oturmuş
    }
  });

  test("ilk açılışta ışık izi bir kez döner ve durur", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await expect(snake(page)).toHaveAttribute("data-snake-state", "running", { timeout: 4000 });
    const first = Number(await snake(page).getAttribute("data-snake-start"));
    expect(first).toBeGreaterThanOrEqual(0);
    expect(first).toBeLessThan(1);
    await expect(snake(page)).toHaveAttribute("data-snake-state", "idle", { timeout: 6000 });
    await page.waitForTimeout(3000); // kendiliğinden tekrar etmez
    await expect(snake(page)).toHaveAttribute("data-snake-state", "idle");
  });

  test("hover yeni turu başlatır; tur sürerken tekrar gelen hover üst üste binmez", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await expect(snake(page)).toHaveAttribute("data-snake-state", "running", { timeout: 4000 });
    await expect(snake(page)).toHaveAttribute("data-snake-state", "idle", { timeout: 6000 });

    await page.evaluate(() => {
      const original = SVGElement.prototype.animate;
      (window as unknown as { __laps: number }).__laps = 0;
      SVGElement.prototype.animate = function patched(this: SVGElement, ...args: Parameters<typeof original>) {
        (window as unknown as { __laps: number }).__laps++;
        return original.apply(this, args);
      };
    });
    const card = page.getByRole("heading", { level: 1, name: "Giriş yap" }).locator("xpath=../..");
    for (let i = 0; i < 5; i++) {
      await card.hover({ position: { x: 30 + i * 10, y: 30 } });
      await page.mouse.move(5, 5);
    }
    await expect(snake(page)).toHaveAttribute("data-snake-state", "running");
    const animations = await page.evaluate(() => (window as unknown as { __laps: number }).__laps);
    // Beş hızlı hover tek tur (7 katman) başlatır; çakışan ikinci tur yok.
    expect(animations).toBe(7);
    await expect(snake(page)).toHaveAttribute("data-snake-state", "idle", { timeout: 6000 });
  });

  test("'hareketi azalt': iz oynamaz, sabit çerçeve görünür, içerik doğrudan görünür", async ({ page }) => {
    await mockGateway(page);
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await page.waitForTimeout(1500);
    await expect(snake(page)).toHaveAttribute("data-snake-state", "idle");
    const staticOpacity = await snake(page).locator("rect").first().evaluate((node) => getComputedStyle(node).opacity);
    expect(Number(staticOpacity)).toBeGreaterThan(0.3);
    await expect(page.getByRole("heading", { level: 1, name: "Giriş yap" })).toBeVisible();
    await expect(page.locator('[data-testid="auth-backdrop"] video')).toHaveCount(0);
  });
});

test.describe("ekranlar arası geçiş", () => {
  test("giriş → kayıt → şifremi unuttum → geri/ileri: URL doğru, video ve navbar yeniden kurulmaz", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await expect(page.locator('[data-testid="auth-backdrop"] video')).toHaveCount(2);
    await page.evaluate(() => {
      (document.querySelector('[data-testid="auth-backdrop"] video') as HTMLVideoElement & { __kept?: boolean }).__kept = true;
      (document.querySelector("header") as HTMLElement & { __kept?: boolean }).__kept = true;
    });
    const kept = () =>
      page.evaluate(
        () =>
          (document.querySelector('[data-testid="auth-backdrop"] video') as HTMLVideoElement & { __kept?: boolean }).__kept === true &&
          (document.querySelector("header") as HTMLElement & { __kept?: boolean }).__kept === true
      );

    await page.getByRole("main").getByRole("link", { name: "Kayıt ol" }).click();
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    await expect(page.getByRole("heading", { level: 1, name: "Kayıt ol" })).toBeFocused();
    await expect(page.getByText("Yeni lobin seni bekliyor")).toBeVisible();
    expect(await kept()).toBe(true);

    await page.getByRole("main").getByRole("link", { name: "Giriş yap" }).click();
    await expect(page).toHaveURL(/\/tr\/giris$/);
    await page.getByRole("link", { name: "Şifremi unuttum" }).click();
    await expect(page).toHaveURL(/\/tr\/sifremi-unuttum$/);
    await expect(page.getByRole("heading", { level: 1, name: "Şifreni mi unuttun?" })).toBeVisible();
    expect(await kept()).toBe(true);

    await page.goBack();
    await expect(page).toHaveURL(/\/tr\/giris$/);
    await expect(page.getByRole("heading", { level: 1, name: "Giriş yap" })).toBeVisible();
    await page.goBack();
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    await page.goForward();
    await expect(page).toHaveURL(/\/tr\/giris$/);
    expect(await kept()).toBe(true);

    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: "Giriş yap" })).toBeVisible();
  });

  test("giriş formundaki veri kayıt formuna taşınmaz", async ({ page }) => {
    await mockGateway(page);
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await page.getByLabel("E-posta veya kullanıcı adı").fill("ada@example.com");
    await page.getByRole("main").getByRole("link", { name: "Kayıt ol" }).click();
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    await expect(page.getByLabel("E-posta", { exact: true })).toHaveValue("");
  });

  test("hatalı gönderim sayfa geçişi tetiklemez; hata aynı ekranda kalır", async ({ page }) => {
    await mockGateway(page, { "GET /api/v1/auth/csrf": { status: 204 }, "POST /api/v1/auth/login": { status: 401, body: { code: "INVALID_CREDENTIALS" } } });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await page.getByLabel("E-posta veya kullanıcı adı").fill("ada");
    await page.getByLabel("Şifre", { exact: true }).fill("yanlis-parola-123");
    await page.getByRole("button", { name: /Giriş yap/ }).click();
    await expect(page.locator('form [role="alert"]')).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/giris$/);
  });

  test("klavyeyle: bağlantı odaklanıp Enter ile geçilir, odak yeni başlığa taşınır", async ({ page }) => {
    await mockGateway(page);
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await page.getByRole("main").getByRole("link", { name: "Kayıt ol" }).focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    await expect(page.getByRole("heading", { level: 1, name: "Kayıt ol" })).toBeFocused();
  });
});

test.describe("duyarlı yerleşim ve temalar", () => {
  for (const size of [
    { name: "390×844", width: 390, height: 844 },
    { name: "768×1024", width: 768, height: 1024 },
    { name: "1366×768", width: 1366, height: 768 },
    { name: "1440×900", width: 1440, height: 900 }
  ]) {
    for (const path of ["/tr/giris", "/tr/kayit", "/tr/sifremi-unuttum"]) {
      test(`${size.name} ${path}: yatay taşma yok, navbar ve footer görünür, kart ekran içinde`, async ({ page }) => {
        await mockGateway(page);
        await page.setViewportSize({ width: size.width, height: size.height });
        await page.goto(path);
        await waitForHydration(page);
        expect(await horizontalOverflow(page)).toBe(0);
        const bar = (await page.getByRole("banner", { name: "Üst çubuk" }).boundingBox())!;
        expect(bar.x).toBeGreaterThanOrEqual(0);
        expect(bar.x + bar.width).toBeLessThanOrEqual(size.width);
        const card = (await page.getByRole("heading", { level: 1 }).locator("xpath=../..").boundingBox())!;
        expect(card.x).toBeGreaterThanOrEqual(0);
        expect(card.x + card.width).toBeLessThanOrEqual(size.width);
        await page.getByRole("contentinfo").scrollIntoViewIfNeeded();
        await expect(page.getByRole("contentinfo")).toBeVisible();
        // Footer, kartın ALTINDA kalır (içerikle üst üste binmez).
        const footer = (await page.getByRole("contentinfo").boundingBox())!;
        const cardAfterScroll = (await page.getByRole("heading", { level: 1 }).locator("xpath=../..").boundingBox())!;
        expect(footer.y).toBeGreaterThanOrEqual(cardAfterScroll.y + cardAfterScroll.height - 1);
      });
    }
  }

  test("mobilde alanlar klavye açıkken ulaşılabilir kalır (küçük yükseklik)", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 390, height: 420 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    const submit = page.getByRole("button", { name: /Giriş yap/ });
    await submit.scrollIntoViewIfNeeded();
    await expect(submit).toBeVisible();
    await page.getByLabel("Şifre", { exact: true }).fill("x");
    expect(await horizontalOverflow(page)).toBe(0);
  });

  test("tema anahtarı kart ve navbar yüzeyini açık temaya çevirir", async ({ page }) => {
    await mockGateway(page);
    await page.goto("/tr/giris");
    await waitForHydration(page);
    const card = page.getByRole("heading", { level: 1, name: "Giriş yap" }).locator("xpath=../..");
    const before = await card.evaluate((node) => getComputedStyle(node).backgroundColor);
    const previous = await page.locator("html").getAttribute("data-theme");
    await page.getByRole("button", { name: /^(Açık|Koyu) tema$/ }).click();
    // Tema, ana uygulamadaki geçiş sahnesi bitince uygulanır (birkaç saniye sürer).
    await expect(page.locator("html")).not.toHaveAttribute("data-theme", previous ?? "", { timeout: 15000 });
    const after = await card.evaluate((node) => getComputedStyle(node).backgroundColor);
    expect(after).not.toBe(before);
  });

  test("İngilizce: karşılama metni ve gezinme çevrilmiştir", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/en/login");
    await waitForHydration(page);
    const snapshot = await page.getByRole("region", { name: "Welcome" }).ariaSnapshot();
    expect(snapshot.match(/Welcome to the lobby/g)).toHaveLength(1);
    const bar = page.getByRole("banner");
    await expect(bar.getByRole("link", { name: "Sign in" })).toBeVisible();
    await expect(bar.getByRole("link", { name: "Sign up" })).toBeVisible();
  });

  test("kısa içerikte footer en altta; dar ekranda doğal akışta", async ({ page }) => {
    await mockGateway(page);
    await page.setViewportSize({ width: 1440, height: 1100 });
    await page.goto("/tr/sifremi-unuttum");
    await waitForHydration(page);
    const footer = (await page.getByRole("contentinfo").boundingBox())!;
    expect(footer.y + footer.height).toBeGreaterThan(1100 - 30);
  });
});

test.describe("Google/Steam dönüşü", () => {
  test("MFA gerektiren dönüş giriş ekranına dönmez, ikinci adımı gösterir", async ({ page }) => {
    await mockGateway(page);
    await page.goto("/tr/auth/geri-donus#status=mfa_required&challenge=abc123");
    await waitForHydration(page);
    await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/);
    await expect(page.getByRole("textbox").first()).toBeVisible();
    await expect(page.getByRole("heading", { level: 1 })).not.toHaveText("Giriş yap");
  });

  test("hesap bağlama gereken Google dönüşü açıklayıcı hata ve giriş bağlantısı gösterir", async ({ page }) => {
    await mockGateway(page);
    await page.goto("/tr/auth/geri-donus#error=ACCOUNT_LINK_REQUIRED");
    await waitForHydration(page);
    await expect(page.getByRole("main").getByRole("alert")).toBeVisible();
    await expect(page.getByRole("main").getByRole("link", { name: /Giriş/ })).toHaveAttribute("href", "/tr/giris");
  });
});
