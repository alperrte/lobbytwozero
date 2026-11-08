import { expect, test } from "@playwright/test";

// next-intl yerelleştirilmiş rewrite'ları Next dev'in localhost origin'iyle aynı host'ta sınanır.
test.use({ baseURL: "http://localhost:5101" });

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const contentId = "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66";
const game = { gameId, title: "GAME-11 Test Oyunu", origin: "INTERNAL", metadataCompleteness: "PARTIAL", coverUrl: null };

for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
  test(`Game → Community liste/detay ve kesinti akışı (${viewport.width})`, async ({ page }) => {
    await page.setViewportSize(viewport);
    let unavailable = false;
    await page.route("http://localhost:5080/api/v1/**", async (route) => {
      const url = new URL(route.request().url());
      const headers = { "Access-Control-Allow-Origin": "http://localhost:5101", "Access-Control-Allow-Credentials": "true", "Cache-Control": "no-store" };
      const fulfill = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
      if (url.pathname === "/api/v1/auth/me") return fulfill({ status: 401 }, 401);
      if (url.pathname === "/api/v1/game/games/prices") return fulfill({ market: { country: url.searchParams.get("country"), currency: url.searchParams.get("currency") }, items: [{ gameId, status: "UNAVAILABLE", offers: [] }] });
      if (url.pathname.endsWith("/developer-content")) return fulfill({ gameId, description: null, media: [], updateNotes: [] });
      if (url.pathname === "/api/v1/game/games") return fulfill({ items: [{ game, releaseDate: "2026-01-01" }], page: 0, size: 20, totalElements: 1 });
      if (url.pathname.endsWith("/modules/ACHIEVEMENTS")) return fulfill({ gameId, type: "ACHIEVEMENTS", state: "EMPTY", items: [], page: 0, size: 20, totalElements: 0 });
      if (url.pathname.includes("/modules/")) {
        const type = url.pathname.split("/").at(-1);
        return fulfill({ gameId, type, state: "EXTERNAL", items: [], page: 0, size: 20, totalElements: null,
          contentSource: { service: "COMMUNITY", listPath: `/api/v1/community/games/${gameId}/content?type=${type}&page=0&size=20` } });
      }
      if (url.pathname === `/api/v1/game/games/${gameId}`) return fulfill({ game, description: "Katalogdan alınan oyun açıklaması.", releaseDate: "2026-01-01", freshness: { status: "FRESH", fetchedAt: null }, metadata: { developers: ["Test stüdyosu"], publishers: [], languages: ["tr"], genres: [], platforms: [] } });
      if (url.pathname === `/api/v1/community/games/${gameId}/content/${contentId}`) return fulfill({ id: contentId, gameId, type: "GUIDES", title: "Başlangıç rehberi", body: "<script>window.ugcExecuted=true</script>", publishedAt: "2026-11-05T10:00:00Z", likeCount: 1, commentCount: 0, likedByMe: false });
      if (unavailable) return fulfill({ status: 503 }, 503);
      return fulfill({ gameId, type: url.searchParams.get("type"), items: [{ id: contentId, gameId, type: "GUIDES", title: "Başlangıç rehberi", excerpt: "İlk adımını keşfet.", publishedAt: "2026-11-05T10:00:00Z" }], page: 0, size: 20, totalElements: 1 });
    });
    await page.goto("/tr/oyunlar");
    await page.getByRole("link", { name: game.title, exact: true }).click();
    await expect(page).toHaveURL(`/tr/oyunlar/${gameId}`);
    await expect(page.getByRole("heading", { name: game.title })).toBeVisible();
    await page.getByRole("button", { name: "Rehberler", exact: true }).click();
    await page.getByRole("button", { name: "Başlangıç rehberi", exact: true }).click();
    await expect(page.getByText("<script>window.ugcExecuted=true</script>", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => "ugcExecuted" in window)).toBe(false);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/game-${viewport.width}.png`, fullPage: true });
    unavailable = true;
    await page.getByRole("button", { name: "Listeye dön", exact: true }).click();
    await expect(page.getByRole("main").getByRole("alert")).toContainText("Servis geçici olarak kullanılamıyor");
    await expect(page.getByText("İlk adımını keşfet.")).toHaveCount(0);
    unavailable = false;
    await page.getByRole("button", { name: "Tekrar dene", exact: true }).click();
    await expect(page.getByRole("button", { name: "Başlangıç rehberi", exact: true })).toBeVisible();
    if (viewport.width > 1000) {
      await page.getByRole("button", { name: "Dil: Türkçe" }).click();
      await page.getByRole("menuitemradio", { name: "English" }).click();
      await expect(page).toHaveURL(`/en/games/${gameId}`);
      await expect(page.getByRole("button", { name: "Guides", exact: true })).toBeVisible();
    }
  });
}
