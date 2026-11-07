import { describe, expect, it } from "vitest";
import { discoveryQuery, safeExternalUrl } from "./discovery-query";

describe("Katalog sorgusu", () => {
  it("boş arama ve filtre göndermez; yerel katalog ilk açılışta geçersiz q= almaz", () => {
    const query = discoveryQuery(new URLSearchParams("q=   &genre=&language=&privateToken=ignored"));
    expect(Object.fromEntries(query)).toEqual({ page: "0", size: "20", sort: "TITLE", direction: "ASC", country: "TR", currency: "TRY" });
  });
  it("birleşik filtreleri ve puan/indirim pazarını tek sayfalı sorguda korur", () => {
    const values = { q: "A & B", genre: "rpg", platform: "pc", language: "tr", minRating: "7.25", sort: "RATING", direction: "DESC", releasedAfter: "2020-01-01", releasedBefore: "2026-01-01", earlyAccess: "false", popularOnly: "true", discountedOnly: "true", country: "DE", currency: "EUR", page: "3" };
    const result = discoveryQuery(new URLSearchParams(values));
    expect(Object.fromEntries(result)).toMatchObject(values);
    expect(result.toString()).toContain("q=A+%26+B");
  });
  it.each(["javascript:alert(1)", "data:text/html,test", "http://example.invalid", "https://user:password@example.invalid", "broken"])("güvenli olmayan dış bağlantıyı reddeder: %s", (url) => {
    expect(safeExternalUrl(url)).toBeUndefined();
  });
  it("mağaza bağlantısının affiliate parametrelerini korur", () => {
    expect(safeExternalUrl("https://store.example/game?affiliate=fixture")).toBe("https://store.example/game?affiliate=fixture");
  });
});
