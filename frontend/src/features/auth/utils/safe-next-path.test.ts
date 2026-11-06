import { describe, expect, it } from "vitest";
import { safeNextPath } from "./safe-next-path";

describe("safeNextPath", () => {
  it("dahili yolları kabul eder", () => {
    expect(safeNextPath("/")).toBe("/");
    expect(safeNextPath("/tr/ayarlar")).toBe("/tr/ayarlar");
    expect(safeNextPath("/en/settings?tab=1#a")).toBe("/en/settings?tab=1#a");
  });

  it.each([
    ["boş", ""],
    ["şemalı adres", "https://evil.com"],
    ["şemasız dış adres", "//evil.com"],
    ["ters eğik çizgi", "/\\evil.com"],
    ["kodlu ters eğik çizgi", "/%5Cevil.com"],
    ["kodlu çift eğik çizgi", "/%2F%2Fevil.com"],
    ["göreli yol", "tr/ayarlar"],
    ["boşluk", "/tr /ayarlar"],
    ["satır sonu", "/tr\n/ayarlar"],
    ["NUL", "/tr\u0000x"],
    ["javascript şeması", "javascript:alert(1)"],
    ["bozuk yüzde kodu", "/%E0%A4%A"],
    ["çok uzun", `/${"a".repeat(600)}`]
  ])("%s reddedilir", (_ad, value) => {
    expect(safeNextPath(value)).toBeNull();
  });

  it("string olmayan girdiyi reddeder", () => {
    expect(safeNextPath(null)).toBeNull();
    expect(safeNextPath(undefined)).toBeNull();
  });
});
