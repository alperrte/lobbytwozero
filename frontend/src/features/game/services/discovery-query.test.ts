import { describe, expect, it } from "vitest";
import { discoveryQuery, safeExternalUrl } from "./discovery-query";

describe("Katalog sorgusu", () => {
  it("boş arama ve filtre göndermez; yerel katalog ilk açılışta geçersiz q= almaz", () => {
    const query = discoveryQuery(new URLSearchParams("q=   &genre=&language=&privateToken=ignored"));
    expect(Object.fromEntries(query)).toEqual({ page: "0", size: "20", sort: "TITLE", direction: "ASC", country: "US", currency: "USD" });
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

import { activeFilters, lastPageIndex, sanitizedDiscoveryQuery, validateFilters } from "./discovery-query";

describe("Filtre doğrulaması ve chip listesi", () => {
  it("aktif filtreleri sabit sırada döndürür ve boşları atar", () => {
    expect(activeFilters(new URLSearchParams("genre=rpg&platform=&minRating=7&sort=RATING&earlyAccess=false"))).toEqual([
      { key: "genre", value: "rpg" }, { key: "earlyAccess", value: "false" }, { key: "minRating", value: "7" }
    ]);
  });
  it.each(["0.5", "10.01", "7.255", "abc", "11"])("geçersiz minRating'i reddeder: %s", (value) => {
    expect(validateFilters(new URLSearchParams({ minRating: value }))).toEqual(["minRating"]);
  });
  it.each(["1", "7.5", "10", "9.99"])("geçerli minRating'i kabul eder: %s", (value) => {
    expect(validateFilters(new URLSearchParams({ minRating: value }))).toEqual([]);
  });
  it("ters tarih aralığını sunucuya göndermez", () => {
    const { query, errors } = sanitizedDiscoveryQuery(new URLSearchParams("releasedAfter=2026-02-01&releasedBefore=2026-01-01&genre=rpg"));
    expect(errors).toEqual(["dateRange"]);
    expect(query.has("releasedAfter") || query.has("releasedBefore")).toBe(false);
    expect(query.get("genre")).toBe("rpg");
  });
  it("son sayfa dizinini toplamdan hesaplar", () => {
    expect([lastPageIndex(0), lastPageIndex(20), lastPageIndex(21), lastPageIndex(100000)]).toEqual([0, 0, 1, 4999]);
  });
  it("fiyat pazarı para biriminden belirlenir: USD→US, EUR→DE, GBP→GB; TRY ve bilinmeyen değer USD'ye düşer", () => {
    const market = (currency: string, country = "XX") => Object.fromEntries(discoveryQuery(new URLSearchParams({ currency, country })));
    expect(market("USD")).toMatchObject({ country: "US", currency: "USD" });
    expect(market("EUR")).toMatchObject({ country: "DE", currency: "EUR" });
    expect(market("GBP", "TR")).toMatchObject({ country: "GB", currency: "GBP" });
    expect(market("TRY", "TR")).toMatchObject({ country: "US", currency: "USD" });
    expect(market("???")).toMatchObject({ country: "US", currency: "USD" });
    expect(Object.fromEntries(discoveryQuery(new URLSearchParams()))).toMatchObject({ country: "US", currency: "USD" });
  });
});
