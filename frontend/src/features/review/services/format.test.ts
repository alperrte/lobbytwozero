import { describe, expect, it } from "vitest";
import { formatAverage, formatCount, formatDateTime, formatPercentage, safeCoverUrl, wasEdited } from "./format";

describe("ortalama ve yüzde", () => {
  it("null değeri 0 veya %0 olarak çizmez", () => {
    expect(formatAverage(null, "tr")).toBeNull();
    expect(formatPercentage(null, "tr")).toBeNull();
    expect(formatAverage(Number.NaN, "tr")).toBeNull();
  });

  it("gerçek 0 yüzdesini 'veri yok' ile karıştırmaz", () => {
    expect(formatPercentage(0, "en")).toBe("0%");
    expect(formatPercentage(75, "en")).toBe("75%");
    expect(formatPercentage(33.33, "en")).toBe("33.33%");
  });

  it("ortalamayı dile göre biçimlendirir", () => {
    expect(formatAverage(7.25, "tr")).toBe("7,25");
    expect(formatAverage(7.25, "en")).toBe("7.25");
    expect(formatAverage(8, "en")).toBe("8.0");
  });

  it("sayıları dile göre biçimlendirir", () => {
    expect(formatCount(1234567, "en")).toBe("1,234,567");
  });
});

describe("tarih", () => {
  it("geçersiz tarihte null döner", () => {
    expect(formatDateTime("bozuk", "tr")).toBeNull();
    expect(formatDateTime("2026-11-01T12:00:00Z", "tr")).toEqual(expect.any(String));
  });

  it("düzenlenme yalnız güncelleme zamanı oluşturmadan sonraysa sayılır", () => {
    expect(wasEdited("2026-11-01T12:00:00Z", "2026-11-01T12:00:00Z")).toBe(false);
    expect(wasEdited("2026-11-01T12:00:00Z", "2026-11-02T12:00:00Z")).toBe(true);
    expect(wasEdited("bozuk", "2026-11-02T12:00:00Z")).toBe(false);
  });
});

describe("kapak adresi", () => {
  it("yalnız https kapak adresini kabul eder", () => {
    expect(safeCoverUrl({ coverUrl: "https://images.igdb.com/a.jpg" })).toBe("https://images.igdb.com/a.jpg");
    expect(safeCoverUrl({ coverUrl: "http://evil.example/a.jpg" })).toBeNull();
    expect(safeCoverUrl({ coverUrl: "javascript:alert(1)" })).toBeNull();
    expect(safeCoverUrl({ coverUrl: "https://user:pass@host.example/a.jpg" })).toBeNull();
    expect(safeCoverUrl({ coverUrl: null })).toBeNull();
  });

  it("kapak yoksa yalnız rakamlı Steam AppID ile başlık görseline düşer", () => {
    expect(safeCoverUrl({ coverUrl: null, externalReferences: [{ provider: "STEAM", externalId: "730" }] })).toBe("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/730/header.jpg");
    expect(safeCoverUrl({ coverUrl: null, externalReferences: [{ provider: "STEAM", externalId: "73/../0" }] })).toBeNull();
    expect(safeCoverUrl({ coverUrl: null, externalReferences: [{ provider: "IGDB", externalId: "730" }] })).toBeNull();
  });
});
