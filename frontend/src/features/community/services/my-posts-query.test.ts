import { afterEach, describe, expect, it } from "vitest";
import { emptyFilters, type PostView } from "../types/community.types";
import { activeFilterCount, belongsToView, filtersFromParams, normalizeSort, paramsFromFilters, supportsFilters } from "./my-posts-query";
import { invalidateMyPosts, loadSnapshot, rememberScroll, saveSnapshot, SNAPSHOT_TTL_MS } from "./my-posts-snapshot";

const GAME = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const params = (record: Record<string, string>) => (key: string) => record[key] ?? null;
const post = (repostOf: PostView["repostOf"], images: unknown[] = [{ id: "i" }]) => ({ id: "x", repostOf, images }) as unknown as PostView;

describe("Gönderilerim sorgu yardımcıları", () => {
  it("URL'den süzgeçleri okur; geçersiz değerleri varsayılana düşürür", () => {
    const filters = filtersFromParams(params({ q: "harita", game: GAME, category: "QUESTION", from: "2026-10-01", to: "2026-10-31", sort: "oldest" }), "posts");
    expect(filters).toEqual({ q: "harita", gameId: GAME, category: "QUESTION", fromDate: "2026-10-01", toDate: "2026-10-31", sort: "OLDEST" });
    const bad = filtersFromParams(params({ game: "x", category: "bilinmeyen", from: "01.10.2026", to: "<script>", sort: "popular" }), "posts");
    expect(bad).toEqual(emptyFilters);
  });

  it("yalnız yeni/eski sıralamasını kabul eder (Popüler yok)", () => {
    expect(normalizeSort("oldest")).toBe("OLDEST");
    expect(normalizeSort("OLDEST")).toBe("OLDEST");
    expect(normalizeSort("POPULAR")).toBe("NEWEST");
    expect(normalizeSort(null)).toBe("NEWEST");
  });

  it("yeniden paylaşımlar görünümü arama/oyun/kategori/tarih süzgeçlerini yok sayar, yalnız sıralamayı korur", () => {
    expect(supportsFilters("reposts")).toBe(false);
    expect(filtersFromParams(params({ q: "abc", category: "GUIDE", sort: "oldest" }), "reposts")).toEqual({ ...emptyFilters, sort: "OLDEST" });
  });

  it("süzgeçleri URL'ye yazar; varsayılanlar yazılmaz ve gidiş-dönüş tutarlıdır", () => {
    expect(paramsFromFilters(emptyFilters)).toEqual({});
    const filters = { q: " harita ", gameId: GAME, category: "GUIDE" as const, fromDate: "2026-10-01", toDate: "", sort: "OLDEST" as const };
    const written = paramsFromFilters(filters);
    expect(written).toEqual({ q: "harita", game: GAME, category: "GUIDE", from: "2026-10-01", sort: "oldest" });
    expect(filtersFromParams(params(written), "posts")).toEqual({ ...filters, q: "harita" });
  });

  it("etkin süzgeç sayısı sıralamayı saymaz", () => {
    expect(activeFilterCount({ ...emptyFilters, sort: "OLDEST" })).toBe(0);
    expect(activeFilterCount({ ...emptyFilters, q: "a ", category: "GUIDE", fromDate: "2026-10-01" })).toBe(3);
  });

  it("karma listeyi üç görünüme böler: görselli özgün → gönderilerim, görselsiz özgün → sözlerim, yeniden paylaşım → yeniden paylaşımlarım", () => {
    const original = post(null);
    const word = post(null, []);
    const repost = post({ postId: "o", available: true }, []);
    expect(belongsToView(original, "posts")).toBe(true);
    expect(belongsToView(original, "words")).toBe(false);
    expect(belongsToView(original, "reposts")).toBe(false);
    expect(belongsToView(word, "words")).toBe(true);
    expect(belongsToView(word, "posts")).toBe(false);
    expect(belongsToView(word, "reposts")).toBe(false);
    // Yeniden paylaşım görselsiz ve açıklamalı olsa bile söz değildir.
    expect(belongsToView(repost, "words")).toBe(false);
    expect(belongsToView(repost, "posts")).toBe(false);
    expect(belongsToView(repost, "reposts")).toBe(true);
  });

  it("sözlerim arama/oyun/kategori/tarih süzgeçlerini destekler (API özgün gönderilerde uygular)", () => {
    expect(supportsFilters("words")).toBe(true);
    expect(filtersFromParams(params({ q: "abc", sort: "oldest" }), "words")).toEqual({ ...emptyFilters, q: "abc", sort: "OLDEST" });
  });
});

describe("Gönderilerim anlık görüntüsü", () => {
  afterEach(() => invalidateMyPosts());

  it("kaydeder, okur ve kaydırma konumunu günceller", () => {
    saveSnapshot("k", [post(null)], "c1", 120);
    expect(loadSnapshot("k")).toMatchObject({ nextCursor: "c1", scrollY: 120 });
    rememberScroll("k", 480);
    expect(loadSnapshot("k")?.scrollY).toBe(480);
    expect(loadSnapshot("başka")).toBeUndefined();
  });

  it("ömrü dolunca geçersizdir; veri değişince (invalidate) tamamen silinir", () => {
    saveSnapshot("k", [], null, 0);
    expect(loadSnapshot("k", Date.now() + SNAPSHOT_TTL_MS + 1000)).toBeUndefined();
    saveSnapshot("k", [], null, 0);
    invalidateMyPosts();
    expect(loadSnapshot("k")).toBeUndefined();
  });
});
