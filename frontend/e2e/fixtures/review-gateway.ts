import type { Page } from "@playwright/test";

// Review Gateway fixture'ı: yalnız UI sözleşmesini doğrular; canlı Auth/Game/Review entegrasyonu DEĞİLDİR.
// Fixture yalnız testlerdedir; üretim arayüzü sahte veriyle çalışmaz.
// Varsayılan Playwright sunucusu 5101'dedir (playwright.config.ts); kaynak adresi yalnız yerel koşuda geçersiz kılınabilir.
export const origin = process.env.E2E_ORIGIN ?? "http://localhost:5101";
export const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
export const otherReviewId = "0198a3c2-1111-7d44-9a55-3f1c2b4d5e66";
export const ownerAccount = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const game = { gameId, title: "Review Fixture Game", coverUrl: null, origin: "INTERNAL", metadataCompleteness: "PARTIAL", gameType: "GAME" };

export type Review = { status?: string; id: string; gameId: string; body: string; ownedByActor: boolean; version: number; createdAt: string; updatedAt: string; likes: number; liked: boolean };
export type World = {
  rating: { gameId: string; rating: number; version: number; createdAt: string; updatedAt: string } | null;
  recommendation: { gameId: string; recommended: boolean; version: number; createdAt: string; updatedAt: string } | null;
  reviews: Review[];
  requests: string[];
  writes: { method: string; path: string; csrf: string | undefined; body: unknown }[];
  batchCsrf: (string | undefined)[];
  reports: Record<string, { id: string; reason: string; detail: string | null; createdAt: string }>;
  reportStatus: number;
  createStatus: number;
  nextVersionConflict: boolean;
};

export const now = "2026-11-01T12:00:00Z";

export async function fixture(page: Page, signedIn = true): Promise<World> {
  if (signedIn) {
    await page.context().addCookies([
      { name: "ltz_session", value: "1", url: origin },
      { name: "XSRF-TOKEN", value: "fixture-csrf", url: origin }
    ]);
  }
  const world: World = {
    rating: null,
    recommendation: null,
    reviews: [{ id: otherReviewId, gameId, body: "Başka oyuncunun incelemesi", ownedByActor: false, version: 0, createdAt: now, updatedAt: now, likes: 10, liked: false }],
    requests: [],
    writes: [],
    batchCsrf: [],
    reports: {},
    reportStatus: 201,
    createStatus: 201,
    nextVersionConflict: false
  };

  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const headers = {
      "access-control-allow-origin": origin,
      "access-control-allow-credentials": "true",
      "access-control-allow-headers": "content-type, x-xsrf-token, accept",
      "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      // Gateway'in gerçek CORS ayarı Retry-After'ı tarayıcıya açar (GatewaySecurityConfig); 429 testi buna dayanır.
      "access-control-expose-headers": "Retry-After, X-Request-Id",
      "cache-control": "no-store"
    };
    const reply = (json: unknown, status = 200, extra: Record<string, string> = {}) => route.fulfill({ json, status, headers: { ...headers, ...extra } });
    if (method === "OPTIONS") return route.fulfill({ status: 204, headers });
    world.requests.push(`${method} ${path}${url.search}`);

    if (path === "/api/v1/auth/me") return reply(signedIn ? { accountId: ownerAccount, roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" } : {}, signedIn ? 200 : 401);
    if (path === "/api/v1/auth/csrf") return route.fulfill({ status: 204, headers });
    if (path === "/api/v1/user/me") return reply({ userId: ownerAccount, displayName: "Fixture player", username: "fixture", avatarUrl: null });
    if (path === "/api/v1/game/games/search" || path === "/api/v1/game/games") return reply({ items: [{ game, releaseDate: null }], page: 0, size: 12, totalElements: 1 });
    if (path === "/api/v1/game/games/batch") return reply({ items: [game], unavailable: [] });
    if (path === "/api/v1/user/profiles/batch" && method === "POST") {
      // User toplu kart ucu: yalnız görünür yazar döner; diğer kimlikler yanıttan çıkarılır (ayrım sızmaz).
      const ids = (request.postDataJSON() as { userIds?: string[] }).userIds ?? [];
      return reply({ items: ids.filter((id) => id === "0198a3c2-0000-7d44-9a55-3f1c2b4d5e02").map((userId) => ({ userId, username: "fixture_yazar", displayName: "Fixture Yazar", avatar: { small: null, large: null, animated: false } })) });
    }
    if (!path.startsWith("/api/v1/review/")) return reply({}, 404);

    // Toplu puan özeti POST ile gider ama OKUMADIR; yazma listesine girmez (CSRF başlığı yine de doğrulanır).
    const batchRead = path === "/api/v1/review/games/rating-summaries";
    if (batchRead) {
      world.batchCsrf.push(request.headers()["x-xsrf-token"]);
    } else if (method !== "GET") {
      world.writes.push({ method, path, csrf: request.headers()["x-xsrf-token"], body: request.postDataJSON() ?? null });
    }
    const review = (item: Review) => ({ id: item.id, gameId: item.gameId, body: item.body, authorUserId: item.ownedByActor ? "0198a3c2-0000-7d44-9a55-3f1c2b4d5e01" : "0198a3c2-0000-7d44-9a55-3f1c2b4d5e02", ownedByActor: item.ownedByActor, status: item.status ?? "VISIBLE", version: item.version, createdAt: item.createdAt, updatedAt: item.updatedAt });
    const like = (item: Review) => ({ reviewId: item.id, likeCount: item.likes, likedByActor: item.liked });
    const sub = path.replace("/api/v1/review", "");

    if (sub === `/games/${gameId}/rating`) {
      if (method === "PUT") {
        const { rating } = request.postDataJSON() as { rating: number };
        world.rating = { gameId, rating, version: (world.rating?.version ?? -1) + 1, createdAt: now, updatedAt: now };
        return reply(world.rating);
      }
      return world.rating ? reply(world.rating) : reply({}, 404);
    }
    if (sub === `/games/${gameId}/recommendation`) {
      if (method === "PUT") {
        const { recommended } = request.postDataJSON() as { recommended: boolean };
        world.recommendation = { gameId, recommended, version: (world.recommendation?.version ?? -1) + 1, createdAt: now, updatedAt: now };
        return reply(world.recommendation);
      }
      return world.recommendation ? reply(world.recommendation) : reply({}, 404);
    }
    if (sub === `/games/${gameId}/statistics`) {
      const counts = Array.from({ length: 10 }, (_, index) => ({ rating: index + 1, userCount: world.rating?.rating === index + 1 ? 1 : 0 }));
      const yes = world.recommendation ? (world.recommendation.recommended ? 1 : 0) : 0;
      const no = world.recommendation ? (world.recommendation.recommended ? 0 : 1) : 0;
      const total = yes + no;
      return reply({
        gameId,
        ratings: { ratingCount: world.rating ? 1 : 0, averageRating: world.rating ? world.rating.rating : null, distribution: counts },
        reviews: { activeCount: world.reviews.length },
        recommendations: { recommendedCount: yes, notRecommendedCount: no, totalCount: total, recommendedPercentage: total ? (yes / total) * 100 : null, notRecommendedPercentage: total ? (no / total) * 100 : null }
      });
    }
    if (sub === `/games/${gameId}/rating-summary`) return reply({ gameId, averageRating: world.rating?.rating ?? null, ratingCount: world.rating ? 1 : 0 });
    if (sub === "/games/rating-summaries") return reply({ items: [{ gameId, averageRating: world.rating?.rating ?? null, ratingCount: world.rating ? 1 : 0 }] });
    if (sub === `/games/${gameId}/reviews/featured`) {
      const min = Number(url.searchParams.get("minLikes") ?? 0);
      const items = world.reviews.filter((item) => item.likes >= min).sort((a, b) => b.likes - a.likes);
      return reply({ items: items.map((item) => ({ review: review(item), likeCount: item.likes, likedByActor: item.liked })), page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 });
    }
    if (sub === `/games/${gameId}/reviews` && method === "POST") {
      if (world.createStatus !== 201) {
        return reply({ status: world.createStatus }, world.createStatus, world.createStatus === 429 ? { "retry-after": "30" } : {});
      }
      const { body } = request.postDataJSON() as { body: string };
      const created: Review = { id: "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66", gameId, body, ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 0, liked: false };
      world.reviews.push(created);
      return reply(review(created), 201);
    }
    if (sub === `/games/${gameId}/reviews/mine`) {
      const own = world.reviews.find((item) => item.ownedByActor);
      return own ? reply(review(own)) : reply({}, 404);
    }
    if (sub === "/reviews/mine") {
      const own = world.reviews.filter((item) => item.ownedByActor);
      return reply({ items: own.map(review), page: 0, size: 20, totalElements: own.length, totalPages: own.length ? 1 : 0 });
    }
    const reportMatch = sub.match(/^\/reviews\/([0-9a-f-]{36})\/reports(\/mine)?$/);
    if (reportMatch) {
      const target = world.reviews.find((candidate) => candidate.id === reportMatch[1]);
      if (!target) return reply({}, 404);
      if (reportMatch[2]) {
        const mine = world.reports[target.id];
        return mine ? reply({ id: mine.id, reviewId: target.id, reason: mine.reason, detail: mine.detail, status: "RECEIVED", createdAt: mine.createdAt }) : reply({}, 404);
      }
      if (world.reportStatus !== 201) return reply({ status: world.reportStatus }, world.reportStatus, world.reportStatus === 429 ? { "retry-after": "45" } : {});
      if (target.ownedByActor) return reply({ code: "REPORT_OWN_REVIEW" }, 403);
      if (world.reports[target.id]) return reply({ code: "REPORT_ALREADY_EXISTS" }, 409);
      const { reason, detail } = request.postDataJSON() as { reason: string; detail?: string };
      world.reports[target.id] = { id: "0198a3c2-7777-7d44-9a55-3f1c2b4d5e66", reason, detail: detail ?? null, createdAt: now };
      const saved = world.reports[target.id];
      return reply({ id: saved.id, reviewId: target.id, reason, detail: saved.detail, status: "RECEIVED", createdAt: now }, 201);
    }
    const reviewMatch = sub.match(/^\/reviews\/([0-9a-f-]{36})(\/like)?$/);
    if (reviewMatch) {
      const item = world.reviews.find((candidate) => candidate.id === reviewMatch[1]);
      if (!item) return reply({}, 404);
      if (reviewMatch[2]) {
        if (method === "PUT" || method === "DELETE") {
          if (item.ownedByActor) return reply({}, 403);
          item.liked = method === "PUT";
          item.likes += method === "PUT" ? 1 : -1;
        }
        return reply(like(item));
      }
      if (method === "GET") return reply(review(item));
      if (method === "PATCH") {
        const { version, body } = request.postDataJSON() as { version: number; body: string };
        if (world.nextVersionConflict || version !== item.version) {
          world.nextVersionConflict = false;
          item.version += 1;
          item.body = "Başka sekmede değiştirilen metin";
          return reply({}, 409);
        }
        item.body = body;
        item.version += 1;
        item.updatedAt = "2026-11-02T12:00:00Z";
        return reply(review(item));
      }
      if (method === "DELETE") {
        world.reviews = world.reviews.filter((candidate) => candidate.id !== item.id);
        return route.fulfill({ status: 204, headers });
      }
    }
    return reply({}, 404);
  });
  return world;
}
