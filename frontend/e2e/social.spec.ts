import { expect, test, type Page, type Route } from "@playwright/test";

/**
 * Sosyal ekranlar — gerçek social/user servisleri gerekmez: Gateway (`http://localhost:5080`) durum tutan bir taklitle
 * yanıtlanır. Taklit, yazma isteklerinde çift gönderimli CSRF'i (çerez = `X-XSRF-TOKEN`) gerçek Gateway gibi denetler.
 * Gerçek yığınla uçtan uca doğrulama ayrıca yapılır; burada arayüzün sözleşmeye uygun konuştuğu kilitlenir.
 */
const GATEWAY = "http://localhost:5080";
const CSRF = "e2e-csrf-token";
const ME = { accountId: "00000000-0000-4000-8000-000000000001", roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" };

const id = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const daysAgo = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

type Person = { userId: string; displayName: string; username: string };

function initialState() {
  const people: Person[] = [
    { userId: id(2), displayName: "Deniz", username: "denizplays" },
    { userId: id(3), displayName: "Ece", username: "ece" },
    { userId: id(30), displayName: "Nova", username: "novafps" },
    { userId: id(40), displayName: "Barış", username: "bariscoop" }
  ];
  return {
    people,
    friends: [
      { friendId: id(2), since: daysAgo(3) },
      { friendId: id(3), since: daysAgo(9) }
    ],
    requests: [
      { id: "req-in-1", senderId: id(30), receiverId: ME.accountId, status: "PENDING", createdAt: daysAgo(0.01), respondedAt: null },
      { id: "req-out-1", senderId: ME.accountId, receiverId: id(40), status: "PENDING", createdAt: daysAgo(3), respondedAt: null }
    ],
    blocks: [{ blockedUserId: id(50), createdAt: daysAgo(14) }],
    inviteCode: { code: "7KX4M9QBTR", createdAt: daysAgo(2), expiresAt: daysAgo(-28), maxUses: 50, usedCount: 3 },
    csrfHeaders: [] as Array<string | undefined>
  };
}

type State = ReturnType<typeof initialState>;

function cors(route: Route): Record<string, string> {
  return {
    "access-control-allow-origin": route.request().headers()["origin"] ?? "*",
    "access-control-allow-credentials": "true",
    "access-control-allow-headers": "content-type, x-xsrf-token, accept",
    "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
    vary: "Origin"
  };
}

function page<T>(items: T[], url: URL) {
  const limit = Number(url.searchParams.get("limit") ?? 20);
  return { items: items.slice(0, limit), nextCursor: items.length > limit ? "next" : null };
}

/** Sahte Gateway: auth (oturum + CSRF), social ve user toplu kart uçları. */
async function fakeGateway(page_: Page, state: State) {
  await page_.route(`${GATEWAY}/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    const reply = (status: number, body?: unknown) =>
      route.fulfill({
        status,
        headers: { ...cors(route), "content-type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body)
      });

    if (method === "OPTIONS") {
      return route.fulfill({ status: 204, headers: cors(route) });
    }
    if (method !== "GET") {
      const header = request.headers()["x-xsrf-token"];
      state.csrfHeaders.push(header);
      if (header !== CSRF) {
        return reply(403, { code: "CSRF_TOKEN_INVALID" });
      }
    }

    const path = url.pathname;
    const key = `${method} ${path}`;
    const pending = (side: "in" | "out") =>
      state.requests.filter(
        (r) => r.status === "PENDING" && (side === "in" ? r.receiverId === ME.accountId : r.senderId === ME.accountId)
      );

    if (key === "GET /api/v1/auth/me") return reply(200, ME);
    if (key === "GET /api/v1/auth/csrf") return reply(204);
    if (key === "GET /api/v1/social/friends") return reply(200, page(state.friends, url));
    if (key === "GET /api/v1/social/friend-requests/incoming") return reply(200, page(pending("in"), url));
    if (key === "GET /api/v1/social/friend-requests/outgoing") return reply(200, page(pending("out"), url));
    if (key === "GET /api/v1/social/blocks") return reply(200, page(state.blocks, url));
    if (key === "GET /api/v1/social/invite-code") return reply(200, state.inviteCode);
    if (key === "POST /api/v1/user/profiles/batch") {
      const { userIds } = request.postDataJSON() as { userIds: string[] };
      // Gerçek user-service gibi: engelli kimlikler atlanır.
      const items = userIds
        .map((userId) => state.people.find((person) => person.userId === userId))
        .filter((person): person is Person => person !== undefined)
        .map((person) => ({ ...person, avatar: null }));
      return reply(200, { items });
    }
    const accept = /^POST \/api\/v1\/social\/friend-requests\/([^/]+)\/accept$/.exec(key);
    if (accept) {
      const target = state.requests.find((r) => r.id === accept[1]);
      if (!target || target.status !== "PENDING") return reply(404, { code: "FRIEND_REQUEST_NOT_FOUND" });
      target.status = "ACCEPTED";
      state.friends.unshift({ friendId: target.senderId, since: new Date().toISOString() });
      return reply(200, target);
    }
    const unblock = /^DELETE \/api\/v1\/social\/blocks\/([^/]+)$/.exec(key);
    if (unblock) {
      state.blocks = state.blocks.filter((block) => block.blockedUserId !== unblock[1]);
      return route.fulfill({ status: 204, headers: cors(route) });
    }
    if (key === "POST /api/v1/social/friend-requests/by-code") {
      const { code } = request.postDataJSON() as { code: string };
      if (code !== "SARP7Q2M4K") return reply(404, { code: "INVITE_CODE_NOT_FOUND" });
      const created = { id: "req-out-2", senderId: ME.accountId, receiverId: id(60), status: "PENDING", createdAt: new Date().toISOString(), respondedAt: null };
      state.requests.push(created);
      return reply(201, created);
    }
    return reply(404, { code: "NOT_FOUND" });
  });
}

async function signIn(page_: Page, baseURL: string | undefined) {
  const url = baseURL ?? "http://localhost:5101";
  // Auth'un girişte yazdığı oturum ipucu ve CSRF çerezini taklit eder (sahte Gateway çerez yazamaz).
  await page_.context().addCookies([
    { name: "ltz_session", value: "1", url },
    { name: "XSRF-TOKEN", value: CSRF, url }
  ]);
}

const tabs = (page_: Page) => page_.getByRole("navigation", { name: "Arkadaşlık bölümleri" });

test("misafir giriş sayfasına yönlenir ve dönüş adresi korunur", async ({ page }) => {
  await page.route(`${GATEWAY}/**`, (route) => route.fulfill({ status: 401, headers: cors(route) }));
  await page.goto("/tr/arkadaslar/istekler");
  // İlk istekte giriş sayfası derlenir (dev sunucusu); yönlendirme bekleme süresi geniş tutulur.
  await expect(page).toHaveURL(/\/tr\/giris\?next=/, { timeout: 20_000 });
  expect(decodeURIComponent(page.url())).toContain("/tr/arkadaslar/istekler");
});

test("arkadaşlar: gerçek uçlardan gelen kayıtlar user-service adlarıyla gösterilir", async ({ page, baseURL }) => {
  const state = initialState();
  await fakeGateway(page, state);
  await signIn(page, baseURL);
  await page.goto("/tr/arkadaslar");

  await expect(page.getByRole("heading", { level: 3, name: "Deniz" })).toBeVisible();
  await expect(page.getByText("@denizplays")).toBeVisible();
  await expect(tabs(page).getByRole("link", { name: /Arkadaşlar\s*2/ })).toHaveAttribute("aria-current", "page");
  await expect(tabs(page).getByRole("link", { name: /Gelen İstekler\s*1/ })).toBeVisible();
  // Yan panel: gelen istek özeti ve davet kodu.
  await expect(page.getByRole("button", { name: "Nova isteğini kabul et" })).toBeVisible();
  await expect(page.getByLabel("Davet kodun", { exact: true })).toHaveText("7KX4M-9QBTR");
});

test("isteği kabul etmek CSRF başlığıyla POST atar, sayaçları ve listeyi günceller", async ({ page, baseURL }) => {
  const state = initialState();
  await fakeGateway(page, state);
  await signIn(page, baseURL);
  await page.goto("/tr/arkadaslar/istekler");

  await page.getByRole("button", { name: "Nova isteğini kabul et" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Nova artık arkadaşın." })).toBeVisible();
  await expect(tabs(page).getByRole("link", { name: /Arkadaşlar\s*3/ })).toBeVisible();
  await expect(page.getByRole("button", { name: "Nova isteğini kabul et" })).toHaveCount(0);
  expect(state.csrfHeaders).not.toContain(undefined);
});

test("davet koduyla istek: bilinmeyen kod alanda hata, geçerli kod Gönderilenler'i artırır", async ({ page, baseURL }) => {
  const state = initialState();
  await fakeGateway(page, state);
  await signIn(page, baseURL);
  await page.goto("/tr/arkadaslar/gonderilenler");

  const input = page.getByRole("textbox", { name: "Arkadaşının davet kodu" });
  await input.fill("ZZZZZ-ZZZZZ");
  await page.getByRole("button", { name: "İstek Gönder" }).click();
  await expect(page.getByText("Bu davet kodu bulunamadı ya da artık kullanılamıyor.")).toBeVisible();
  await expect(input).toHaveValue("ZZZZZ-ZZZZZ");

  await input.fill("sarp7-q2m4k");
  await page.getByRole("button", { name: "İstek Gönder" }).click();
  await expect(input).toHaveValue("");
  await expect(tabs(page).getByRole("link", { name: /Gönderilenler\s*2/ })).toBeVisible();
});

test("engellenen kişi adsız gösterilir ve engeli onayla kaldırılır", async ({ page, baseURL }) => {
  const state = initialState();
  await fakeGateway(page, state);
  await signIn(page, baseURL);
  await page.goto("/tr/arkadaslar/engellenenler");

  const unblock = page.getByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" });
  await unblock.click();
  const dialog = page.getByRole("dialog", { name: "Engeli kaldır?" });
  await expect(dialog.getByRole("button", { name: "Vazgeç" })).toBeFocused();
  await dialog.getByRole("button", { name: "Engeli Kaldır" }).click();
  await expect(unblock).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Engellediğin kimse yok" })).toBeVisible();
});

test("Türkçe ve İngilizce slug'lar aynı sayfayı açar", async ({ page, baseURL }) => {
  await fakeGateway(page, initialState());
  await signIn(page, baseURL);
  await page.goto("/en/friends/blocked");
  await expect(page.getByRole("heading", { level: 1, name: "Blocked Users" })).toBeVisible();
  await page.goto("/tr/friends/sent");
  await expect(page).toHaveURL(/\/tr\/arkadaslar\/gonderilenler$/);
});

for (const width of [375, 768, 1440] as const) {
  test(`sosyal sayfalar ${width}px genişlikte yatay taşma yapmaz`, async ({ page, baseURL }) => {
    await fakeGateway(page, initialState());
    await signIn(page, baseURL);
    await page.setViewportSize({ width, height: 900 });
    for (const path of ["/tr/arkadaslar", "/tr/arkadaslar/istekler", "/tr/arkadaslar/engellenenler"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await page.locator("main ul li").first().waitFor();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow, path).toBeLessThanOrEqual(0);
    }
  });
}
