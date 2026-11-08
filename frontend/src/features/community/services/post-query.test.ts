import { describe, expect, it } from "vitest";
import { emptyFilters, type PostFilters } from "../types/community.types";
import { buildPostQuery, endOfLocalDayExclusive, isInvalidRange, isUuid, normalizedSearch, startOfLocalDay } from "./post-query";

const filters = (patch: Partial<PostFilters> = {}): PostFilters => ({ ...emptyFilters, ...patch });
const GAME = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";

describe("Community akış sorgusu", () => {
  it("varsayılan sorgu yalnız sıralama ve sayfa boyutu içerir", () => {
    expect(buildPostQuery(filters())).toBe("sort=NEWEST&limit=20");
  });

  it("q en az 2 karakter ister; kısa arama gönderilmez, kırpılır", () => {
    expect(normalizedSearch("a")).toBeUndefined();
    expect(normalizedSearch("  ab  ")).toBe("ab");
    expect(buildPostQuery(filters({ q: "x" }))).not.toContain("q=");
    expect(new URLSearchParams(buildPostQuery(filters({ q: " harita " }))).get("q")).toBe("harita");
    expect(normalizedSearch("a".repeat(150))).toHaveLength(100);
  });

  it("yalnız API'nin desteklediği süzgeçleri yazar ve imleci aynı sort ile taşır", () => {
    const query = new URLSearchParams(
      buildPostQuery(filters({ gameId: GAME, category: "GUIDE", sort: "POPULAR" }), { forumId: GAME, cursor: "abc" })
    );
    expect(query.get("gameId")).toBe(GAME);
    expect(query.get("forumId")).toBe(GAME);
    expect(query.get("category")).toBe("GUIDE");
    expect(query.get("sort")).toBe("POPULAR");
    expect(query.get("cursor")).toBe("abc");
  });

  it("UUID olmayan oyun/forum kimliğini sunucuya göndermez", () => {
    const query = new URLSearchParams(buildPostQuery(filters({ gameId: "not-a-uuid" }), { forumId: "x" }));
    expect(query.has("gameId")).toBe(false);
    expect(query.has("forumId")).toBe(false);
    expect(isUuid(GAME)).toBe(true);
  });

  it("bitiş gününü dahil sayar (ertesi gün başı, hariç) ve ters aralığı göndermez", () => {
    const from = startOfLocalDay("2026-10-01");
    const to = endOfLocalDayExclusive("2026-10-01");
    expect(from).toBeDefined();
    expect(to! > from!).toBe(true);
    expect(new Date(to!).getTime() - new Date(from!).getTime()).toBeGreaterThanOrEqual(23 * 3600 * 1000);
    expect(startOfLocalDay("bozuk")).toBeUndefined();

    const reversed = filters({ fromDate: "2026-10-05", toDate: "2026-10-01" });
    expect(isInvalidRange(reversed)).toBe(true);
    const query = new URLSearchParams(buildPostQuery(reversed));
    expect(query.has("from")).toBe(false);
    expect(query.has("to")).toBe(false);

    const valid = new URLSearchParams(buildPostQuery(filters({ fromDate: "2026-10-01", toDate: "2026-10-05" })));
    expect(valid.get("from")! < valid.get("to")!).toBe(true);
  });
});
