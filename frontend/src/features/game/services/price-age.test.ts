import { describe, expect, it } from "vitest";
import { relativeAge } from "./price-age";

const now = Date.parse("2026-11-12T12:00:00Z");
const ago = (seconds: number) => now - seconds * 1000;

describe("Bayat fiyat yaşı", () => {
  it("dakika, saat ve gün birimini yaşa göre seçer (TR)", () => {
    expect(relativeAge(ago(30), now, "tr")).toBe("1 dakika önce");
    expect(relativeAge(ago(5 * 60), now, "tr")).toBe("5 dakika önce");
    expect(relativeAge(ago(59 * 60 + 59), now, "tr")).toBe("59 dakika önce");
    expect(relativeAge(ago(3600), now, "tr")).toBe("1 saat önce");
    expect(relativeAge(ago(23 * 3600), now, "tr")).toBe("23 saat önce");
    expect(relativeAge(ago(3 * 86400), now, "tr")).toBe("3 gün önce");
  });
  it("İngilizce metin üretir ve 1 gün için 'yesterday' der", () => {
    expect(relativeAge(ago(2 * 3600), now, "en")).toBe("2 hours ago");
    expect(relativeAge(ago(86400), now, "en")).toBe("yesterday");
  });
  it("gelecekteki veya aynı zamanı olumsuz göstermez", () => {
    expect(relativeAge(now + 60_000, now, "tr")).toBe("1 dakika önce");
    expect(relativeAge(now, now, "en")).toBe("1 minute ago");
  });
});
