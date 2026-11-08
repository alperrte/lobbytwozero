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

  test("iki aşamalı doğrulama: önce kod ekranı (oturum yok), doğru kodla ana sayfa; challenge URL'de görünmez", async ({ page }) => {
    const bodies: unknown[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": {
        status: 200,
        body: { status: "MFA_REQUIRED", challengeId: "c1", challengeExpiresAt: "2030-01-01T00:00:00Z" }
      },
      "POST /api/v1/auth/login/2fa": async (route) => {
        bodies.push(route.request().postDataJSON());
        return route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(AUTHENTICATED)
        });
      },
      "GET /api/v1/auth/me": { status: 200, body: ME }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await expect(page.getByLabel("Doğrulama kodu")).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/giris$/);
    expect(page.url()).not.toContain("c1");

    await page.getByLabel("Doğrulama kodu").fill("123456");
    await page.getByRole("button", { name: /^Doğrula/ }).click();

    await expect(page).toHaveURL(/\/tr$/);
    expect(bodies).toEqual([{ challengeId: "c1", code: "123456" }]);
  });

  test("zorunlu kurulum: anahtar → kod → kurtarma kodları kaydedilince ana sayfa", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": {
        status: 200,
        body: { status: "MFA_ENROLLMENT_REQUIRED", challengeId: "e1", challengeExpiresAt: "2030-01-01T00:00:00Z" }
      },
      "POST /api/v1/auth/login/2fa/enrollment/start": {
        status: 200,
        body: { secret: "JBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/LTZ:ada?secret=JBSWY3DPEHPK3PXP" }
      },
      "POST /api/v1/auth/login/2fa/enrollment/complete": {
        status: 200,
        body: { ...AUTHENTICATED, recoveryCodes: ["AAAAA-BBBBB", "CCCCC-DDDDD"] }
      },
      "GET /api/v1/auth/me": { status: 200, body: ME }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);

    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();
    await page.getByRole("button", { name: "Kurulumu başlat" }).click();
    await expect(page.getByTestId("totp-secret")).toHaveText("JBSWY3DPEHPK3PXP");

    await page.getByLabel("Doğrulama kodu").fill("123456");
    await page.getByRole("button", { name: /Etkinleştir ve giriş yap/ }).click();
    await expect(page.getByRole("list", { name: "Kurtarma kodları" })).toContainText("AAAAA-BBBBB");

    await expect(page.getByRole("button", { name: /Devam et/ })).toBeDisabled();
    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: /Devam et/ }).click();
    await expect(page).toHaveURL(/\/tr$/);
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

test.describe("profil özeti", () => {
  test("User profilinden gelen ad kabukta görünür; /user/me hata verirse genel etiket kalır", async ({ page, baseURL }) => {
    await setSessionHint(page, baseURL);
    await mockGateway(page, {
      "GET /api/v1/auth/me": { status: 200, body: ME },
      "GET /api/v1/user/me": { status: 200, body: { displayName: "Ada Lovelace", username: "ada", avatar: null } }
    });
    await page.goto("/tr");
    await waitForHydration(page);
    await expect(profileButton(page, /Ada Lovelace/)).toBeVisible();

    const second = await page.context().newPage();
    await mockGateway(second, {
      "GET /api/v1/auth/me": { status: 200, body: ME },
      "GET /api/v1/user/me": { status: 500, body: { code: "UNKNOWN" } }
    });
    await second.goto("/tr");
    await waitForHydration(second);
    await expect(profileButton(second, /Hesabım/)).toBeVisible();
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
    await expect(page.getByRole("main").getByRole("link", { name: "Kayıt ol" })).toHaveAttribute("href", "/tr/kayit");
    // Üst çubukta da aynı gezinme vardır.
    await expect(page.getByRole("banner").getByRole("link", { name: "Kayıt ol" })).toHaveAttribute("href", "/tr/kayit");
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

test.describe("şifremi unuttum ve şifre sıfırlama", () => {
  const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";

  test("giriş sayfasında 'Şifremi unuttum' bağlantısı sayfaya gider; TR ve EN adresleri açılır", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/giris");
    await expect(page.getByRole("link", { name: "Şifremi unuttum" })).toHaveAttribute("href", "/tr/sifremi-unuttum");
    await page.goto("/en/forgot-password");
    await expect(page.getByRole("heading", { level: 1, name: "Forgot your password?" })).toBeVisible();
    await page.goto("/tr/forgot-password");
    await expect(page).toHaveURL(/\/tr\/sifremi-unuttum$/);
  });

  test("şifremi unuttum: hesap varlığını söylemeden tek bilgi mesajı gösterir", async ({ page }) => {
    const bodies: unknown[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/password/forgot": async (route) => {
        bodies.push(route.request().postDataJSON());
        return route.fulfill({
          status: 202,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ nextStep: "CHECK_EMAIL" })
        });
      }
    });
    await page.goto("/tr/sifremi-unuttum");
    await waitForHydration(page);

    await page.getByLabel("E-posta").fill("ada@example.com");
    await page.getByRole("button", { name: "Bağlantı gönder" }).click();

    await expect(page.getByText(/Bu adres kayıtlıysa bir şifre sıfırlama bağlantısı gönderdik/)).toBeVisible();
    expect(bodies).toEqual([{ email: "ada@example.com" }]);
  });

  test("e-postadaki ÖNEKSİZ bağlantı dil önekli sayfaya yönlenir, token URL parçasında korunur ve adresten silinir", async ({ page }) => {
    const bodies: unknown[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/password/reset": async (route) => {
        bodies.push(route.request().postDataJSON());
        return route.fulfill({ status: 204, headers: corsFor(route) });
      }
    });

    // Auth'ın e-postada ürettiği bağlantı biçimi: dil öneksiz yol + #token=...
    await page.goto(`/auth/reset-password#token=${TOKEN}`);
    await waitForHydration(page);

    await expect(page).toHaveURL(/\/tr\/auth\/sifre-sifirla$/); // önek eklendi, slug yerelleşti, parça silindi
    expect(page.url()).not.toContain(TOKEN);
    await expect(page.getByLabel("Yeni şifre", { exact: true })).toBeVisible();

    await page.getByLabel("Yeni şifre", { exact: true }).fill("yeni-dogru-parola-123");
    await page.getByLabel("Yeni şifre (tekrar)").fill("yeni-dogru-parola-123");
    await page.getByRole("button", { name: "Şifreyi güncelle" }).click();

    await expect(page).toHaveURL(/\/tr\/giris\?reset=1$/);
    await expect(page.getByRole("status")).toContainText("Şifren güncellendi");
    expect(bodies).toEqual([
      { token: TOKEN, newPassword: "yeni-dogru-parola-123", newPasswordConfirmation: "yeni-dogru-parola-123" }
    ]);
  });

  test("token'sız ya da bozuk bağlantıda form yerine yeni bağlantı isteme yönlendirmesi çıkar", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/auth/sifre-sifirla");
    await waitForHydration(page);

    await expect(page.locator('[role="alert"]').filter({ hasText: "geçersiz ya da süresi dolmuş" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Yeni bağlantı iste" })).toHaveAttribute("href", "/tr/sifremi-unuttum");
    await expect(page.getByLabel("Yeni şifre", { exact: true })).toHaveCount(0);
  });

  test("süresi dolmuş token (sunucu reddi) yeni bağlantı yönlendirmesi gösterir", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/password/reset": { status: 400, body: { code: "PASSWORD_RESET_TOKEN_INVALID" } }
    });
    await page.goto(`/tr/auth/sifre-sifirla#token=${TOKEN}`);
    await waitForHydration(page);
    await page.getByLabel("Yeni şifre", { exact: true }).fill("yeni-dogru-parola-123");
    await page.getByLabel("Yeni şifre (tekrar)").fill("yeni-dogru-parola-123");

    await page.getByRole("button", { name: "Şifreyi güncelle" }).click();

    await expect(page.getByRole("link", { name: "Yeni bağlantı iste" })).toBeVisible();
  });
});

test.describe("Google/Steam dönüşü", () => {
  const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";

  test("giriş sayfasındaki Google ve Steam bağlantıları Gateway start adresine gider", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/giris");
    await expect(page.getByRole("link", { name: /Google ile giriş/ })).toHaveAttribute(
      "href",
      `${GATEWAY}/api/v1/auth/oauth/google/start`
    );
    await expect(page.getByRole("link", { name: /Steam ile giriş/ })).toHaveAttribute(
      "href",
      `${GATEWAY}/api/v1/auth/oauth/steam/start`
    );
  });

  test("ÖNEKSİZ dönüş bağlantısı: #status=authenticated dil önekli sayfaya yönlenir ve ana sayfaya geçer", async ({ page }) => {
    await mockGateway(page, { "GET /api/v1/auth/me": { status: 200, body: ME } });
    await page.goto("/auth/callback#status=authenticated");
    await waitForHydration(page);

    await expect(page).toHaveURL(/\/tr$|\/tr\/?$/);
  });

  test("#error=… çevrilmiş hata ve giriş bağlantısı gösterir; parça adresten silinir", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/auth/callback#error=ACCOUNT_LINK_REQUIRED");
    await waitForHydration(page);

    await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/);
    await expect(page.locator('main [role="alert"]')).toContainText("zaten bir hesap var");
    await expect(page.getByRole("link", { name: "Giriş sayfasına dön" })).toHaveAttribute("href", "/tr/giris");
  });

  for (const provider of ["google", "steam"] as const) {
    test(`${provider} ilk giriş: yalnız kullanıcı adı sorulur → hesap tamamlanır → ana sayfaya geçilir (e-posta yok)`, async ({ page }) => {
      const completeBodies: unknown[] = [];
      await mockGateway(page, {
        "GET /api/v1/auth/csrf": { status: 204 },
        [`POST /api/v1/auth/oauth/${provider}/complete`]: async (route) => {
          completeBodies.push(route.request().postDataJSON());
          return route.fulfill({
            status: 200,
            headers: { ...corsFor(route), "content-type": "application/json" },
            body: JSON.stringify(AUTHENTICATED)
          });
        },
        "GET /api/v1/auth/me": { status: 200, body: ME }
      });

      await page.goto(`/auth/callback#status=registration_required&provider=${provider}&token=${TOKEN}`);
      await waitForHydration(page);
      await expect(page.getByLabel("Kullanıcı adı")).toBeVisible();
      await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/); // parça silindi
      expect(page.url()).not.toContain(TOKEN);
      await expect(page.getByLabel("E-posta")).toHaveCount(0);
      await expect(page.getByLabel("Şifre")).toHaveCount(0);
      await expect(page.getByText(provider === "google" ? "Google hesabınla ilk kez geliyorsun" : "Steam hesabınla ilk kez geliyorsun")).toBeVisible();

      await page.getByLabel("Kullanıcı adı").fill("oyuncu_ada");
      await page.getByRole("button", { name: /Devam et/ }).click();

      await expect(page).toHaveURL(/\/tr$|\/tr\/?$/);
      expect(completeBodies).toEqual([{ registrationToken: TOKEN, username: "oyuncu_ada" }]);
    });
  }

  test("kullanıcı adı çakışmasında form açık kalır, hata gösterilir, başka adla devam edilir", async ({ page }) => {
    let calls = 0;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/oauth/google/complete": async (route) => {
        calls++;
        const taken = calls === 1;
        return route.fulfill({
          status: taken ? 409 : 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify(taken ? { code: "USERNAME_TAKEN" } : AUTHENTICATED)
        });
      },
      "GET /api/v1/auth/me": { status: 200, body: ME }
    });
    await page.goto(`/auth/callback#status=registration_required&provider=google&token=${TOKEN}`);
    await waitForHydration(page);

    await page.getByLabel("Kullanıcı adı").fill("alinmis_ad");
    await page.getByRole("button", { name: /Devam et/ }).click();
    await expect(page.getByText("Bu kullanıcı adı kullanılıyor.")).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/);

    await page.getByLabel("Kullanıcı adı").fill("bos_ad");
    await page.getByRole("button", { name: /Devam et/ }).click();
    await expect(page).toHaveURL(/\/tr$|\/tr\/?$/);
  });

  test("mevcut Google/Steam kullanıcısı: kullanıcı adı formu ya da giriş formu hiç görünmeden ana sayfaya gider", async ({ page }) => {
    await mockGateway(page, { "GET /api/v1/auth/me": { status: 200, body: ME } });
    const seen: string[] = [];
    await page.exposeFunction("report", (label: string) => seen.push(label));
    await page.addInitScript(() => {
      new MutationObserver(() => {
        if (document.querySelector('input[name="username"], input[name="password"], input[name="identifier"]')) {
          (window as unknown as { report: (label: string) => void }).report("form");
        }
      }).observe(document, { childList: true, subtree: true });
    });

    await page.goto("/auth/callback#status=authenticated");
    await expect(page).toHaveURL(/\/tr$|\/tr\/?$/);

    expect(seen).toEqual([]);
  });

  test("MFA gereken sosyal giriş ikinci adımı gösterir; atlanmaz, doğrulanmadan ana sayfaya gidilmez", async ({ page }) => {
    await mockGateway(page, { "GET /api/v1/auth/csrf": { status: 204 } });
    await page.goto("/auth/callback#status=mfa_required&challenge=abc123");
    await waitForHydration(page);

    await expect(page.getByLabel("Doğrulama kodu")).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/);
  });

  test("ilk girişte MFA kurulumu zorunluysa kullanıcı adı sonrası oturum açılmaz, kurulum adımı çıkar", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/oauth/steam/complete": async (route) =>
        route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ status: "MFA_ENROLLMENT_REQUIRED", challengeId: "kurulum-1", challengeExpiresAt: "2030-01-01T00:00:00Z" })
        })
    });
    await page.goto(`/auth/callback#status=registration_required&provider=steam&token=${TOKEN}`);
    await waitForHydration(page);

    await page.getByLabel("Kullanıcı adı").fill("steam_ada");
    await page.getByRole("button", { name: /Devam et/ }).click();

    await expect(page.getByLabel("Kullanıcı adı")).toHaveCount(0);
    await expect(page).toHaveURL(/\/tr\/auth\/geri-donus$/);
    await expect(page.getByRole("button", { name: "Kurulumu başlat" })).toBeVisible();
  });

  test("kayıt token'ı geçersizse form yerine sağlayıcıyı yeniden başlatma yönlendirmesi çıkar", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/oauth/google/complete": { status: 400, body: { code: "REGISTRATION_TOKEN_INVALID" } }
    });
    await page.goto(`/auth/callback#status=registration_required&provider=google&token=${TOKEN}`);
    await waitForHydration(page);

    await page.getByLabel("Kullanıcı adı").fill("google_ada");
    await page.getByRole("button", { name: /Devam et/ }).click();

    await expect(page.locator('main [role="alert"]')).toContainText("Google ile girişi yeniden başlat");
    await expect(page.getByLabel("Kullanıcı adı")).toHaveCount(0);
  });

  test("#status=email_verification_required&proof=… doğrulama ekranına geçer; kanıt adreste kalmaz", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/auth/callback#status=email_verification_required&proof=kanit-abc");
    await waitForHydration(page);

    await expect(page).toHaveURL(/\/tr\/eposta-dogrulama$/);
    expect(page.url()).not.toContain("kanit-abc");
  });
});

test.describe("hesap silme onayı", () => {
  const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";

  test("ÖNEKSİZ e-posta bağlantısı dil önekli sayfaya yönlenir, token adresten silinir, onay gövdede gider", async ({ page }) => {
    const bodies: unknown[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/account/deletion/confirm": async (route) => {
        bodies.push(route.request().postDataJSON());
        return route.fulfill({
          status: 202,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ nextStep: "DELETION_IN_PROGRESS" })
        });
      }
    });
    await page.goto(`/account/delete#token=${TOKEN}`);
    await waitForHydration(page);

    await expect(page.getByLabel("Kullanıcı adın")).toBeVisible();
    await expect(page).toHaveURL(/\/tr\/hesap\/sil$/);
    expect(page.url()).not.toContain(TOKEN);

    await page.getByLabel("Kullanıcı adın").fill("ada");
    await page.getByRole("button", { name: /Hesabımı sil/ }).click();

    await expect(page.getByRole("status")).toContainText("Silme talebin alındı");
    expect(bodies).toEqual([{ token: TOKEN, username: "ada" }]);
  });

  test("token'sız ziyarette form çıkmaz", async ({ page }) => {
    await mockGateway(page, {});
    await page.goto("/tr/hesap/sil");
    await waitForHydration(page);
    await expect(page.locator('main [role="alert"]')).toContainText("geçersiz ya da süresi dolmuş");
    await expect(page.getByLabel("Kullanıcı adın")).toHaveCount(0);
  });
});

test.describe("silme talebinden vazgeç ve doğrulanmamış e-posta", () => {
  test("silinme sürecindeki hesap girişte vazgeç bağlantısı görür; vazgeçince giriş sayfasına yönlendirir", async ({ page }) => {
    const bodies: unknown[] = [];
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": { status: 403, body: { code: "ACCOUNT_DELETION_PENDING" } },
      "POST /api/v1/auth/account/deletion/cancel": async (route) => {
        bodies.push(route.request().postDataJSON());
        return route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ nextStep: "DELETION_CANCELLED" })
        });
      }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await fillLogin(page, "ada", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await page.getByRole("link", { name: "Silme talebinden vazgeç" }).click();
    await expect(page).toHaveURL(/\/tr\/hesap\/silmeyi-iptal-et$/);
    await waitForHydration(page);

    await page.getByLabel("E-posta veya kullanıcı adı").fill("ada");
    await page.getByLabel("Şifre", { exact: true }).fill("dogru-parola-123456");
    await page.getByRole("button", { name: /Silmeyi iptal et/ }).click();

    await expect(page.getByRole("status")).toContainText("Silme talebin iptal edildi");
    expect(bodies).toEqual([{ identifier: "ada", password: "dogru-parola-123456" }]);
    await expect(page.getByRole("main").getByRole("link", { name: "Giriş yap" })).toHaveAttribute("href", "/tr/giris");
  });

  test("geri alma süresi dolmuşsa açıklayıcı mesaj gösterir", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/account/deletion/cancel": { status: 409, body: { code: "DELETION_CANCEL_WINDOW_CLOSED" } }
    });
    await page.goto("/en/account/cancel-deletion");
    await waitForHydration(page);
    await page.getByLabel("Email or username").fill("ada");
    await page.getByLabel("Password", { exact: true }).fill("dogru-parola-123456");
    await page.getByRole("button", { name: /Cancel deletion/ }).click();

    await expect(formAlert(page)).toContainText("grace period has ended");
  });

  test("e-postası doğrulanmamış girişte doğrulama bağlantısı e-postayı taşır; URL'de görünmez", async ({ page }) => {
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      "POST /api/v1/auth/login": { status: 403, body: { code: "EMAIL_NOT_VERIFIED" } }
    });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await fillLogin(page, "ada@example.com", "dogru-parola-123456");
    await page.getByRole("button", { name: /Giriş yap/ }).click();

    await page.getByRole("link", { name: "E-postamı doğrula" }).click();
    await expect(page).toHaveURL(/\/tr\/eposta-dogrulama$/);
    expect(page.url()).not.toContain("ada");
    await expect(page.getByLabel("E-posta", { exact: true })).toHaveValue("ada@example.com");
  });
});

test.describe("tam ekran arka plan videosu", () => {
  test("geniş ekranda iki video etiketi vardır; sayfa geçişinde AYNI etiketler kalır (video kesilmez)", async ({ page }) => {
    await mockGateway(page, {});
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    const videos = page.locator('[data-testid="auth-backdrop"] video');
    await expect(videos).toHaveCount(2);

    // Etikete işaret koy; istemci tarafı geçişten sonra işaret durursa layout ve video yeniden kurulmamıştır.
    await page.evaluate(() => {
      (document.querySelector('[data-testid="auth-backdrop"] video') as HTMLVideoElement & { __kept?: boolean }).__kept = true;
    });
    await page.getByRole("link", { name: "Kayıt ol" }).first().click();
    await expect(page).toHaveURL(/\/tr\/kayit$/);
    const kept = await page.evaluate(
      () => (document.querySelector('[data-testid="auth-backdrop"] video') as HTMLVideoElement & { __kept?: boolean }).__kept === true
    );
    expect(kept).toBe(true);
    await expect(videos).toHaveCount(2);
  });

  test("telefon genişliğinde video yüklenmez; yalnız poster görünür", async ({ page }) => {
    await mockGateway(page, {});
    const requested: string[] = [];
    page.on("request", (request) => request.url().includes("/auth-videos/") && requested.push(request.url()));
    await page.setViewportSize({ width: 390, height: 800 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await expect(page.locator('[data-testid="auth-backdrop"] video')).toHaveCount(0);
    // Poster bilerek yüklenir (video yerine görünen sabit görsel); hiçbir mp4 istenmez.
    expect(requested.filter((url) => url.endsWith(".mp4"))).toEqual([]);
    expect(requested.every((url) => url.endsWith("/auth-videos/poster.jpg"))).toBe(true);
  });

  test("'hareketi azalt' açıkken video yüklenmez", async ({ page }) => {
    await mockGateway(page, {});
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/tr/giris");
    await waitForHydration(page);
    await expect(page.locator('[data-testid="auth-backdrop"] video')).toHaveCount(0);
  });
});

test.describe("diğer servislerin sayfaları: oturum katmanı ve otomatik yenileme", () => {
  const GAME_ID = "00000000-0000-4000-8000-000000000001";

  test("(dashboard) grubunda da kabuk giriş yapmış kullanıcıyı gösterir (Game yönetim sayfası)", async ({ page, baseURL }) => {
    await setSessionHint(page, baseURL);
    await mockGateway(page, {
      "GET /api/v1/auth/me": { status: 200, body: { ...ME, accountId: GAME_ID, roles: ["GAME_DEVELOPER"] } },
      "GET /api/v1/user/me": { status: 200, body: { displayName: "Ada Lovelace", username: "ada", avatar: null } },
      "GET /api/v1/game/developer/games": { status: 200, body: [] }
    });

    await page.goto("/tr/oyunlar/yonetim");
    await waitForHydration(page);

    await expect(profileButton(page, /Ada Lovelace/)).toBeVisible();
    await expect(profileButton(page, /Misafir/)).toHaveCount(0);
  });

  test("Game'in oturum yoklaması access token süresi dolunca otomatik yenilenir; 'giriş yapmalısın' çıkmaz", async ({ page, baseURL }) => {
    await setSessionHint(page, baseURL);
    let meCalls = 0;
    let refreshCalls = 0;
    await mockGateway(page, {
      "GET /api/v1/auth/csrf": { status: 204 },
      // Game ve kabuk /auth/me'yi ayrı ayrı çağırır: ilk çağrı süresi dolmuş token (401), yenilemeden sonrakiler 200.
      "GET /api/v1/auth/me": async (route) => {
        meCalls += 1;
        if (refreshCalls === 0) {
          return route.fulfill({ status: 401, headers: { ...corsFor(route), "content-type": "application/json" }, body: JSON.stringify({ code: "UNAUTHENTICATED" }) });
        }
        return route.fulfill({
          status: 200,
          headers: { ...corsFor(route), "content-type": "application/json" },
          body: JSON.stringify({ ...ME, accountId: GAME_ID, roles: ["GAME_DEVELOPER"] })
        });
      },
      "POST /api/v1/auth/session/refresh": async (route) => {
        refreshCalls += 1;
        return route.fulfill({ status: 200, headers: { ...corsFor(route), "content-type": "application/json" }, body: JSON.stringify(AUTHENTICATED) });
      },
      "GET /api/v1/user/me": { status: 200, body: { displayName: "Ada", username: "ada", avatar: null } },
      "GET /api/v1/game/developer/games": { status: 200, body: [] }
    });

    await page.goto("/tr/oyunlar/yonetim");
    await waitForHydration(page);

    await expect(page.getByText("Oyun yönetimine erişmek için giriş yapmalısın")).toHaveCount(0);
    await expect(profileButton(page, /Ada/)).toBeVisible();
    expect(refreshCalls).toBeGreaterThanOrEqual(1);
    expect(refreshCalls).toBeLessThanOrEqual(2); // eşzamanlı 401'ler tek yenilemeye iner (StrictMode geliştirmede ikiye çıkabilir)
    expect(meCalls).toBeGreaterThan(1);
  });

  test("misafir (ipucu çerezi yok) Game sayfasında yenileme denemez ve 'giriş yapmalısın' görür", async ({ page }) => {
    let refreshCalls = 0;
    await mockGateway(page, {
      "GET /api/v1/auth/me": { status: 401, body: { code: "UNAUTHENTICATED" } },
      "POST /api/v1/auth/session/refresh": async (route) => {
        refreshCalls += 1;
        return route.fulfill({ status: 401, headers: corsFor(route) });
      }
    });

    await page.goto("/tr/oyunlar/yonetim");
    await waitForHydration(page);

    await expect(page.getByText("Oyun yönetimine erişmek için giriş yapmalısın")).toBeVisible();
    expect(refreshCalls).toBe(0);
  });
});
