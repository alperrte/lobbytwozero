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

/** Auth'ın girişte yazdığı oturum ipucu çerezini taklit eder (sahte Gateway çerez yazamadığı için). */
async function setSessionHint(page: Page, baseURL: string | undefined) {
  await page.context().addCookies([{ name: "ltz_session", value: "1", url: baseURL ?? "http://localhost:5101" }]);
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

  test("başarılı girişte ana sayfaya gider; kabuk giriş yapmış kullanıcıyı gösterir", async ({ page, baseURL }) => {
    let signedIn = false;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": async (route) => {
        signedIn = true;
        await setSessionHint(page, baseURL);
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

  test("oturum ipucu çerezi yoksa Gateway'e hiç istek atılmaz (misafir)", async ({ page }) => {
    const calls: string[] = [];
    await page.route(`${GATEWAY}/**`, (route) => {
      calls.push(route.request().url());
      return route.fulfill({ status: 500 });
    });

    await page.goto("/tr");
    await waitForHydration(page);
    await expect(profileButton(page, /Misafir/)).toBeVisible();
    await page.waitForTimeout(500);

    expect(calls).toEqual([]);
  });

  test("access token süresi dolunca /me 401 → yenile → tekrar; kullanıcı atılmaz", async ({ page, baseURL }) => {
    await setSessionHint(page, baseURL);
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

  test("çıkış kabuğu anında misafire çevirir", async ({ page, baseURL }) => {
    await setSessionHint(page, baseURL);
    let loggedOut = false;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "GET /api/v1/auth/me": (route) =>
        route.fulfill({
          status: loggedOut ? 401 : 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(loggedOut ? { code: "SESSION_INVALID" } : ME)
        }),
      "POST /api/v1/auth/session/logout": async (route) => {
        loggedOut = true;
        await page.context().clearCookies({ name: "ltz_session" });
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

test.describe("güvenlik başlıkları", () => {
  test("giriş sayfası iframe'e konamaz ve temel güvenlik başlıklarını gönderir", async ({ request }) => {
    const response = await request.get("/tr/giris");
    const headers = response.headers();

    expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
    expect(headers["x-frame-options"]).toBe("DENY");
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
    expect(headers["permissions-policy"]).toContain("camera=()");
  });
});

test.describe("kayıt ve e-posta doğrulama", () => {
  const registerFill = async (page: Page, overrides: { username?: string } = {}) => {
    await page.getByLabel("Kullanıcı adı").fill(overrides.username ?? "ada_01");
    await page.getByLabel("E-posta", { exact: true }).fill("ada@example.com");
    await page.getByLabel("Şifre", { exact: true }).fill("dogru-parola-123456");
    await page.getByRole("checkbox").check();
  };

  test("TR ve EN adreslerinde açılır; yanlış dildeki adres doğru adrese yönlenir", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/kayit");
    await expect(page.getByRole("heading", { level: 1, name: "Kayıt ol" })).toBeVisible();
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    await page.goto("/en/register");
    await expect(page.getByRole("heading", { level: 1, name: "Sign up" })).toBeVisible();
    await page.goto("/tr/register");
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    await page.goto("/tr/verify-email");
    await expect(page).toHaveURL(/\/tr\/eposta-dogrulama$/);
  });

  test("giriş sayfasından kayıt sayfasına bağlantı vardır", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/giris");
    await expect(page.getByRole("link", { name: "Kayıt ol" })).toHaveAttribute("href", "/tr/kayit");
  });

  test("boş gönderimde hatalar gösterilir ve istek atılmaz", async ({ page }) => {
    let calls = 0;
    await mockGateway(page, { "POST /api/v1/auth/register": () => void calls++ });
    await page.goto("/tr/kayit");
    await waitForHydration(page);

    await page.getByRole("button", { name: /Hesap oluştur/ }).click();

    await expect(page.getByText("Bir kullanıcı adı belirle.")).toBeVisible();
    await expect(page.getByText("Devam etmek için koşulları kabul etmelisin.")).toBeVisible();
    await expect(page.getByLabel("Kullanıcı adı")).toBeFocused();
    expect(calls).toBe(0);
  });

  test("USERNAME_TAKEN kullanıcı adı alanında gösterilir", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/register": { status: 409, body: { code: "USERNAME_TAKEN" } }
    });
    await page.goto("/tr/kayit");
    await waitForHydration(page);
    await registerFill(page);

    await page.getByRole("button", { name: /Hesap oluştur/ }).click();

    await expect(page.getByText("Bu kullanıcı adı kullanılıyor.")).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/kayit$/);
  });

  test("kayıt → doğrulama → giriş: e-posta URL'de taşınmaz, bellekten dolar, doğrulama sonrası bilgi kutusu görünür", async ({ page }) => {
    const bodies: Record<string, unknown> = {};
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/register": async (route) => {
        bodies.register = route.request().postDataJSON();
        return route.fulfill({
          status: 202,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ nextStep: "VERIFY_EMAIL" })
        });
      },
      "POST /api/v1/auth/email-verification/confirm": async (route) => {
        bodies.confirm = route.request().postDataJSON();
        return route.fulfill({ status: 204, headers: corsFor(route) });
      }
    });
    await page.goto("/tr/kayit");
    await waitForHydration(page);
    await registerFill(page);

    await page.getByRole("button", { name: /Hesap oluştur/ }).click();

    await expect(page).toHaveURL(/\/tr\/eposta-dogrulama$/);
    expect(page.url()).not.toContain("example.com");
    await expect(page.getByLabel("E-posta", { exact: true })).toHaveValue("ada@example.com");
    expect(bodies.register).toEqual({ email: "ada@example.com", username: "ada_01", password: "dogru-parola-123456" });

    await page.getByLabel("Doğrulama kodu").fill("012 345");
    await expect(page.getByLabel("Doğrulama kodu")).toHaveValue("012345");
    await page.getByLabel("Kayıt olurken belirlediğin şifre").fill("dogru-parola-123456");
    await page.getByRole("button", { name: /E-postayı doğrula/ }).click();

    await expect(page).toHaveURL(/\/tr\/giris\?verified=1$/);
    await expect(page.getByRole("status")).toContainText("E-posta adresin doğrulandı");
    expect(bodies.confirm).toEqual({ email: "ada@example.com", code: "012345", password: "dogru-parola-123456" });
  });

  test("yanlış kodda tek ve ayrım yapmayan mesaj; yeniden gönderme bilgi ve bekleme gösterir", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/email-verification/confirm": { status: 400, body: { code: "VERIFICATION_FAILED" } },
      "POST /api/v1/auth/email-verification/resend": { status: 202, body: { nextStep: "VERIFY_EMAIL" } }
    });
    await page.goto("/tr/eposta-dogrulama");
    await waitForHydration(page);
    await page.getByLabel("E-posta", { exact: true }).fill("ada@example.com");
    await page.getByLabel("Doğrulama kodu").fill("999999");
    await page.getByLabel("Kayıt olurken belirlediğin şifre").fill("dogru-parola-123456");

    await page.getByRole("button", { name: /E-postayı doğrula/ }).click();
    await expect(page.getByText(/Kod geçersiz, süresi dolmuş ya da deneme hakkı bitmiş/)).toBeVisible();
    await expect(page.getByLabel("Kayıt olurken belirlediğin şifre")).toHaveValue("");

    await page.getByRole("button", { name: "Kodu yeniden gönder" }).click();
    await expect(page.getByText(/Yeni kod gönderildi/)).toBeVisible();
    await expect(page.getByRole("button", { name: /Yeniden göndermek için 1 dakika bekle/ })).toBeDisabled();
  });
});
