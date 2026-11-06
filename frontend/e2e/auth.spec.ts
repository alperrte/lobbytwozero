import { test, expect, type Page, type Route } from "@playwright/test";

/**
 * Giriş ve oturum akışı. Gerçek Gateway/Auth gerekmez: Gateway (`NEXT_PUBLIC_API_BASE_URL`, varsayılan
 * `http://localhost:5080`) Playwright ile taklit edilir. Gerçek yığınla (CORS, `__Host-` çerezleri, token süresi dolunca
 * yenileme) uçtan uca doğrulama ayrıca yapılır; burada arayüz davranışı kilitlenir.
 */
const GATEWAY = "http://localhost:5080";

/** CORS başlıkları: yanıt, isteğin kendi `Origin` değerini yansıtır (host'tan bağımsız: localhost ya da 127.0.0.1). */
function corsFor(route: Route): Record<string, string> {
  return {
    "access-control-allow-origin": route.request().headers()["origin"] ?? "*",
    "access-control-allow-credentials": "true",
    "access-control-allow-headers": "content-type, x-xsrf-token, accept",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-expose-headers": "retry-after",
    vary: "Origin"
  };
}

type Handler = (route: Route) => Promise<void> | void;
type Mock = { status: number; body?: unknown; headers?: Record<string, string> };

/** Gateway'i taklit eder: ön uçuş (OPTIONS) isteklerini yanıtlar, kalanını `handlers` ile eşler. */
async function mockGateway(page: Page, handlers: Record<string, Mock | Handler>) {
  await page.route(`${GATEWAY}/**`, async (route) => {
    const request = route.request();
    if (request.method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsFor(route) });
      return;
    }
    const path = new URL(request.url()).pathname;
    const key = `${request.method()} ${path}`;
    const handler = handlers[key];
    if (typeof handler === "function") {
      await handler(route);
      return;
    }
    const mock = handler ?? { status: 404 };
    await route.fulfill({
      status: mock.status,
      headers: { ...corsFor(route), "content-type": "application/json", ...mock.headers },
      body: mock.body === undefined ? undefined : JSON.stringify(mock.body)
    });
  });
}

const ME = { accountId: "a1", roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" };
const AUTHENTICATED = { status: "AUTHENTICATED", accessTokenExpiresAt: "2030-01-01T00:00:00Z" };

/** React bağlanana kadar bekler (etkileşim hydration öncesi kaybolmasın). */
async function waitForHydration(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) => key.startsWith("__reactProps"))
  );
}

async function fillLogin(page: Page, identifier: string, password: string) {
  await page.getByLabel("E-posta veya kullanıcı adı").fill(identifier);
  await page.getByLabel("Şifre", { exact: true }).fill(password);
}

const formAlert = (page: Page) => page.locator('form [role="alert"]');
const profileButton = (page: Page, name: RegExp) =>
  page.locator('header button[aria-haspopup="menu"]').filter({ hasText: name });

test.describe("giriş sayfası", () => {
  test("TR ve EN adreslerinde açılır; başlık, alanlar ve noindex doğru", async ({ page }) => {
    await mockGateway(page, {});

    await page.goto("/tr/giris");
    await expect(page).toHaveTitle(/Giriş yap/);
    await expect(page.getByRole("heading", { level: 1, name: "Giriş yap" })).toBeVisible();
    await expect(page.getByLabel("E-posta veya kullanıcı adı")).toHaveAttribute("autocomplete", "username");
    await expect(page.getByLabel("Şifre", { exact: true })).toHaveAttribute("autocomplete", "current-password");
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);

    await page.goto("/en/login");
    await expect(page.getByRole("heading", { level: 1, name: "Sign in" })).toBeVisible();
  });

  test("yanlış dildeki adres doğru adrese yönlenir", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/login");
    await expect(page).toHaveURL(/\/tr\/giris$/);
  });

  test("boş gönderimde alan hataları gösterilir ve ilk alana odaklanılır; istek atılmaz", async ({ page }) => {
    let calls = 0;
    await mockGateway(page, { "POST /api/v1/auth/login": () => void calls++ });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(page.getByText("E-posta adresini veya kullanıcı adını gir.")).toBeVisible();
    await expect(page.getByLabel("E-posta veya kullanıcı adı")).toBeFocused();
    expect(calls).toBe(0);
  });

  test("yanlış parolada tek ve ayrım yapmayan mesaj gösterir; parola alanı temizlenir", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": { status: 401, body: { code: "INVALID_CREDENTIALS" } }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "yanlis-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(formAlert(page)).toHaveText("E-posta, kullanıcı adı veya şifre hatalı.");
    await expect(page.getByLabel("Şifre", { exact: true })).toHaveValue("");
  });

  test("429'da geri sayım gösterir ve düğme pasif olur", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": {
        status: 429,
        body: { code: "TOO_MANY_ATTEMPTS" },
        headers: { "retry-after": "900" }
      }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "yanlis-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(formAlert(page)).toContainText("15 dakika sonra tekrar dene");
    await expect(page.getByRole("button", { name: "15 dakika sonra tekrar dene" })).toBeDisabled();
  });

  test("iki aşamalı doğrulama yanıtında oturum açılmış sayılmaz", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": {
        status: 200,
        body: { status: "MFA_REQUIRED", challengeId: "c1", challengeExpiresAt: "2030-01-01T00:00:00Z" }
      }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(formAlert(page)).toContainText("iki aşamalı doğrulama istiyor");
    await expect(page).toHaveURL(/\/tr\/giris$/);
  });

  test("başarılı girişte ana sayfaya gider; kabuk giriş yapmış kullanıcıyı gösterir", async ({ page }) => {
    let signedIn = false;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": (route) => {
        signedIn = true;
        return route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(AUTHENTICATED)
        });
      },
      "GET /api/v1/auth/me": (route) =>
        route.fulfill({
          status: signedIn ? 200 : 401,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(signedIn ? ME : { code: "SESSION_INVALID" })
        }),
      "POST /api/v1/auth/session/refresh": { status: 401, body: { code: "SESSION_INVALID" } }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(page).toHaveURL(/\/tr\/?$/);
    await expect(profileButton(page, /Hesabım/)).toBeVisible();
  });

  test("güvenli next adresine gider; dış adresi yok sayar", async ({ page, baseURL }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": { status: 200, body: AUTHENTICATED },
      "GET /api/v1/auth/me": { status: 200, body: ME }
    });

    await page.goto("/tr/giris?next=/tr/ayarlar");
    await waitForHydration(page);
    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();
    await expect(page).toHaveURL(/\/tr\/ayarlar$/);

    await page.goto("/tr/giris?next=//evil.example");
    await waitForHydration(page);
    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();
    await expect(page).toHaveURL(/\/tr\/?$/);
    expect(new URL(page.url()).host).toBe(new URL(baseURL ?? page.url()).host);
  });
});

test.describe("oturum ve kabuk", () => {
  test("misafirde profil menüsü 'Giriş yap' bağlantısı verir, çıkış yoktur", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "GET /api/v1/auth/me": { status: 401, body: { code: "SESSION_INVALID" } },
      "POST /api/v1/auth/session/refresh": { status: 401, body: { code: "SESSION_INVALID" } }
    });
    await page.goto("/tr");
    await waitForHydration(page);

    await profileButton(page, /Misafir/).click();

    await expect(page.getByRole("menuitem", { name: "Giriş yap" })).toHaveAttribute("href", "/tr/giris");
    await expect(page.getByRole("menuitem", { name: /Çıkış yap/ })).toHaveCount(0);
  });

  test("access token süresi dolunca /me 401 → yenile → tekrar; kullanıcı atılmaz", async ({ page }) => {
    let refreshed = false;
    const sequence: string[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "GET /api/v1/auth/me": (route) => {
        sequence.push("me");
        return route.fulfill({
          status: refreshed ? 200 : 401,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(refreshed ? ME : { code: "SESSION_INVALID" })
        });
      },
      "POST /api/v1/auth/session/refresh": (route) => {
        sequence.push("refresh");
        refreshed = true;
        return route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(AUTHENTICATED)
        });
      }
    });

    await page.goto("/tr");
    await waitForHydration(page);

    await expect(profileButton(page, /Hesabım/)).toBeVisible();
    // Geliştirme modunda React StrictMode efekti iki kez çalıştırır (ilk /me fazladan görünebilir); üretimde tam üç istek olur.
    expect(sequence.filter((step) => step === "refresh")).toHaveLength(1);
    expect(sequence.at(-1)).toBe("me");
    expect(sequence.indexOf("refresh")).toBeLessThan(sequence.length - 1);
  });

  test("çıkış kabuğu anında misafire çevirir", async ({ page }) => {
    let loggedOut = false;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "GET /api/v1/auth/me": (route) =>
        route.fulfill({
          status: loggedOut ? 401 : 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(loggedOut ? { code: "SESSION_INVALID" } : ME)
        }),
      "POST /api/v1/auth/session/logout": (route) => {
        loggedOut = true;
        return route.fulfill({ status: 204, headers: corsFor(route) });
      },
      "POST /api/v1/auth/session/refresh": { status: 401, body: { code: "SESSION_INVALID" } }
    });
    await page.goto("/tr");
    await waitForHydration(page);

    await profileButton(page, /Hesabım/).click();
    await page.getByRole("menuitem", { name: /Çıkış yap/ }).click();

    await expect(profileButton(page, /Misafir/)).toBeVisible();
  });
});
