import { afterEach, describe, expect, it } from "vitest";
import { lockPageScroll } from "./scroll-lock";

describe("lockPageScroll", () => {
  afterEach(() => { document.documentElement.style.overflow = ""; });

  it("kilit alınınca sayfa kaydırması kapanır, bırakılınca eski değere döner", () => {
    document.documentElement.style.overflow = "auto";
    const unlock = lockPageScroll();
    expect(document.documentElement.style.overflow).toBe("hidden");
    unlock();
    expect(document.documentElement.style.overflow).toBe("auto");
  });

  it("iç içe kilitlerde hangi sırayla bırakılırsa bırakılsın sayfa donuk kalmaz", () => {
    for (const order of [[0, 1], [1, 0]]) {
      const unlocks = [lockPageScroll(), lockPageScroll()];
      unlocks[order[0]]();
      expect(document.documentElement.style.overflow).toBe("hidden");
      unlocks[order[1]]();
      expect(document.documentElement.style.overflow).toBe("");
    }
  });

  it("aynı kilit iki kez bırakılsa da sayacı bozmaz", () => {
    const first = lockPageScroll();
    const second = lockPageScroll();
    first(); first();
    expect(document.documentElement.style.overflow).toBe("hidden");
    second();
    expect(document.documentElement.style.overflow).toBe("");
  });
});
