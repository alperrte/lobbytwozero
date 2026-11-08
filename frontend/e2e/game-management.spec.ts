import { expect, test } from "@playwright/test";
import messages from "../src/shared/lib/i18n/messages/tr.json";

const t = messages.Game;
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const detail = { game: { gameId, title: "Fixture game", gameType: "MAIN_GAME", origin: "INTERNAL", metadataCompleteness: "PARTIAL", coverUrl: null }, description: "Fixture description", releaseDate: null, metadata: { developers: [], publishers: [], languages: [], genres: [], categories: [], tags: [], platforms: [], earlyAccess: true } };

for (const width of [1440, 390]) {
  test(`Game filtre ve admin yazma sözleşmesi (${width})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.context().addCookies([{ name: "XSRF-TOKEN", value: "fixture-csrf", domain: "localhost", path: "/" }, { name: "ltz_session", value: "1", domain: "localhost", path: "/" }]);
    const writes: { method: string; path: string; csrf: string | undefined; body: Record<string, unknown> }[] = [];
    const queries: string[] = [];
    let conflict = true;
    await page.route("http://localhost:5080/api/v1/**", async (route) => {
      const request = route.request(), url = new URL(request.url());
      const headers = { "Access-Control-Allow-Origin": "http://localhost:5101", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "Content-Type,X-XSRF-TOKEN", "Access-Control-Allow-Methods": "GET,POST,PATCH,PUT,DELETE,OPTIONS", "Cache-Control": "no-store" };
      const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
      if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
      if (url.pathname === "/api/v1/auth/me") return reply({ accountId, roles: ["ADMIN"] });
      if (url.pathname === "/api/v1/auth/csrf") return route.fulfill({ status: 204, headers });
      if (request.method() !== "GET") {
        writes.push({ method: request.method(), path: url.pathname, csrf: request.headers()["x-xsrf-token"], body: request.postDataJSON() });
        if (request.method() === "PATCH" && conflict) return reply({ code: "OPTIMISTIC_LOCK", status: 409 }, 409);
        return reply({ gameId }, request.method() === "POST" ? 201 : 200);
      }
      if (url.pathname === "/api/v1/game/games") { queries.push(url.search); return reply({ items: [{ game: detail.game, releaseDate: null, rating: { status: "UNRATED", averageRating: null, ratingCount: 0 } }], totalElements: 1, page: 0, size: 20 }); }
      if (url.pathname === `/api/v1/game/admin/games/${gameId}`) return reply({ gameId, version: 4, status: "ACTIVE", game: detail, manualOverrides: [] });
      if (url.pathname.endsWith("/developers")) return reply([]);
      if (url.pathname.endsWith("/achievements")) return reply({ items: [], totalElements: 0 });
      return reply({ status: 404 }, 404);
    });
    await page.goto("/tr/oyunlar");
    await expect(page.getByRole("link", { name: detail.game.title, exact: true })).toBeVisible();
    expect(new URLSearchParams(queries[0]).has("q")).toBe(false);
    await page.getByRole("button", { name: t.filters, exact: true }).click();
    await page.getByLabel(t.fields.genre, { exact: true }).fill("rpg");
    await page.getByRole("button", { name: t.applyFilters, exact: true }).click();
    await expect.poll(() => queries.some((query) => new URLSearchParams(query).get("genre") === "rpg")).toBe(true);
    await page.getByRole("combobox", { name: t.sort, exact: true }).selectOption("RATING");
    await expect.poll(() => queries.some((query) => new URLSearchParams(query).get("genre") === "rpg" && new URLSearchParams(query).get("sort") === "RATING")).toBe(true);
    await page.goto(`/tr/oyunlar/yonetim?gameId=${gameId}`);
    const editor = page.getByRole("form", { name: t.editGame, exact: true });
    await editor.getByLabel(t.fields.title, { exact: true }).fill("Retained draft");
    await editor.getByRole("button", { name: t.save, exact: true }).click();
    await expect(editor.getByRole("alert")).toContainText(t.errors["409"]);
    await expect(editor.getByLabel(t.fields.title, { exact: true })).toHaveValue("Retained draft");
    expect(writes).toHaveLength(1);
    expect(writes[0]).toMatchObject({ method: "PATCH", csrf: "fixture-csrf", body: { title: "Retained draft", version: 4 } });
    expect(Object.keys(writes[0].body).sort()).toEqual(["title", "version"]);
    conflict = false;
    await page.getByRole("tab", { name: t.lifecycle, exact: true }).click();
    const archive = page.getByRole("form", { name: t.archiveGame, exact: true });
    await archive.getByLabel(t.fields.reason, { exact: true }).fill("Fixture archive reason");
    await expect(archive.getByRole("button", { name: t.save, exact: true })).toBeDisabled();
    await archive.getByLabel(t.confirmAction, { exact: true }).check();
    await archive.getByRole("button", { name: t.save, exact: true }).click();
    await expect.poll(() => writes.length).toBe(2);
    expect(writes[1]).toMatchObject({ method: "POST", path: `/api/v1/game/admin/games/${gameId}/archive`, csrf: "fixture-csrf", body: { reason: "Fixture archive reason" } });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/game-management-${width}.png`, fullPage: true });
  });
}

test("Game geliştirici sürümü ve yetki sınırı", async ({ page }) => {
  await page.context().addCookies([{ name: "XSRF-TOKEN", value: "fixture-csrf", domain: "localhost", path: "/" }, { name: "ltz_session", value: "1", domain: "localhost", path: "/" }]);
  const writes: Record<string, unknown>[] = [];
  let forbidden = false;
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request(), url = new URL(request.url());
    const headers = { "Access-Control-Allow-Origin": "http://localhost:5101", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "Content-Type,X-XSRF-TOKEN", "Access-Control-Allow-Methods": "GET,PUT,OPTIONS" };
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
    if (request.method() === "OPTIONS" || url.pathname.endsWith("/csrf")) return route.fulfill({ status: 204, headers });
    if (url.pathname.endsWith("/auth/me")) return reply({ accountId, roles: ["GAME_DEVELOPER"] });
    if (url.pathname === "/api/v1/game/developer/games") return reply([{ gameId, title: detail.game.title }]);
    if (request.method() === "PUT") { writes.push(request.postDataJSON()); return reply({ status: 409 }, 409); }
    if (url.pathname.endsWith("/content")) return forbidden ? reply({ status: 403 }, 403) : reply({ gameId, description: { text: "Original developer description", version: 3, status: "VISIBLE" }, media: [], updateNotes: [] });
    if (url.pathname.endsWith("/feedback")) return reply({ items: [], totalElements: 0 });
    return reply({ status: 404 }, 404);
  });
  await page.goto("/tr/oyunlar/yonetim");
  const editor = page.getByRole("form", { name: t.editDeveloperDescription });
  await editor.getByLabel(t.fields.description, { exact: true }).fill("New developer description");
  await editor.getByRole("button", { name: t.save, exact: true }).click();
  await expect(editor.getByRole("alert")).toContainText(t.errors["409"]);
  expect(writes).toEqual([{ description: "New developer description", version: 3 }]);
  await expect(page.getByRole("form", { name: t.createGame })).toHaveCount(0);
  forbidden = true;
  await page.getByRole("region", { name: t.developerContent, exact: true }).getByRole("button", { name: t.reload, exact: true }).first().click();
  await expect(editor).toHaveCount(0);
  await expect(page.getByRole("main").getByRole("alert")).toContainText(t.errors["403"]);
});
