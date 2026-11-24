import { describe, expect, it } from "vitest";
import type { EventSuggestion, GameCard, PlayerSuggestion, RecommendedItem } from "../types/recommendation";
import { distinctIds, matchCatalogKey, profileName, termLabel, toEventViews, toGameViews, toPlayerViews } from "./adapters";

const item = (gameId: string, rank: number): RecommendedItem => ({ itemId: `i-${rank}`, gameId, score: 10, rank, reasonCode: "GENRE_RATING_AFFINITY", contribution: 10, matchedGenres: [] });
const card = (gameId: string): GameCard => ({ gameId, title: `Game ${gameId}`, coverUrl: null });

describe("zenginleştirme adaptörleri", () => {
  it("katalogda olmayan oyun düşürülmez; yalnız başarılı yanıtta 'yok' sayılır", () => {
    const items = [item("A", 1), item("B", 2)];
    const loaded = toGameViews(items, { data: new Map([["a", card("A")]]), failed: false });
    expect(loaded.map((view) => [view.item.rank, view.card?.title ?? null, view.available])).toEqual([[1, "Game A", true], [2, null, false]]);
    expect(toGameViews(items, { data: undefined, failed: false }).every((view) => view.available)).toBe(true);
    expect(toGameViews(items, { data: new Map(), failed: true }).every((view) => view.available)).toBe(true);
  });

  it("profili gelmeyen oyuncu sırasını korur", () => {
    const suggestions: PlayerSuggestion[] = ["U1", "U2"].map((userId) => ({ userId, score: 1, sharedGenres: [], sharedPlatforms: [], sharedPlayerTypes: [] }));
    const views = toPlayerViews(suggestions, new Map([["u2", { userId: "U2", username: "two", displayName: null, avatarPath: null }]]));
    expect(views.map((view) => view.profile?.username ?? null)).toEqual([null, "two"]);
  });

  it("etkinlik oyunu yalnız gameId varsa eşlenir", () => {
    const base: EventSuggestion = { eventId: "e", type: "TOURNAMENT", title: "Cup", startsAt: "s", endsAt: "e", gameId: "G", score: 1, sharedGenres: [], sharedPlatforms: [] };
    expect(toEventViews([base, { ...base, eventId: "f", gameId: null }], new Map([["g", card("G")]])).map((view) => view.game?.title ?? null)).toEqual(["Game G", null]);
  });

  it("profil adı: görünen ad → kullanıcı adı → null", () => {
    expect(profileName({ userId: "u", username: "nick", displayName: "  ", avatarPath: null })).toBe("nick");
    expect(profileName({ userId: "u", username: null, displayName: null, avatarPath: null })).toBeNull();
    expect(profileName(null)).toBeNull();
  });

  it("terim etiketi dile göre seçilir, yoksa kod gösterilir", () => {
    const terms = [{ code: "rpg", nameTr: "Rol yapma", nameEn: "Role-playing", gameCount: 3 }];
    expect(termLabel(terms, "rpg", "tr")).toBe("Rol yapma");
    expect(termLabel(terms, "rpg", "en")).toBe("Role-playing");
    expect(termLabel(terms, "fps", "tr")).toBe("fps");
  });

  it("etkinlik kodu katalog anahtarıyla normalize edilerek eşleşir", () => {
    expect(matchCatalogKey("roleplaying", ["action", "role-playing"])).toBe("role-playing");
    expect(matchCatalogKey("action", ["action"])).toBe("action");
    expect(matchCatalogKey("racing", ["action"])).toBeNull();
  });

  it("kimlikler büyük/küçük harf duyarsız tekilleşir, sıra korunur", () => {
    expect(distinctIds(["B", "a", null, "b", "A", undefined, "c"])).toEqual(["B", "a", "c"]);
  });
});
