import { afterEach, describe, expect, it } from "vitest";
import { CACHE_TTL_MS, recommendationCache } from "./recommendation-cache";

afterEach(() => recommendationCache.clear());

describe("recommendationCache (yalnız bellek)", () => {
  it("süresi dolan kaydı döndürmez", () => {
    recommendationCache.set("a", "friends", [1], 1000);
    expect(recommendationCache.get("a", "friends", 1000 + CACHE_TTL_MS)).toEqual([1]);
    expect(recommendationCache.get("a", "friends", 1001 + CACHE_TTL_MS)).toBeUndefined();
  });

  it("hesap değişince önceki hesabın kayıtları silinir", () => {
    recommendationCache.set("a", "friends", [1]);
    expect(recommendationCache.get("b", "friends")).toBeUndefined();
    expect(recommendationCache.get("a", "friends")).toBeUndefined();
  });

  it("önek geçersiz kılma yalnız eşleşen anahtarları siler", () => {
    recommendationCache.set("a", "runs:10", 1);
    recommendationCache.set("a", "runs:20", 2);
    recommendationCache.set("a", "events:12", 3);
    recommendationCache.invalidate("a", "runs:");
    expect(recommendationCache.get("a", "runs:10")).toBeUndefined();
    expect(recommendationCache.get("a", "runs:20")).toBeUndefined();
    expect(recommendationCache.get("a", "events:12")).toBe(3);
  });

  it("en fazla 64 kayıt tutar; en eskisi düşer", () => {
    for (let index = 0; index < 65; index += 1) recommendationCache.set("a", `k${index}`, index);
    expect(recommendationCache.get("a", "k0")).toBeUndefined();
    expect(recommendationCache.get("a", "k64")).toBe(64);
  });

  it("tarayıcı depolamasına hiçbir şey yazmaz", () => {
    recommendationCache.set("a", "friends", [1]);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
