import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  changeTheme,
  finishThemeTransition,
  getThemeTransition,
  TRANSITION_APPLY_AT_MS,
  TRANSITION_TOTAL_MS
} from "./theme-transition";
import { resolveSceneTiming, sceneFor, sceneTimings } from "./transition-scenes";

function stubMedia({ reduce = false, compact = false }: { reduce?: boolean; compact?: boolean } = {}) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("prefers-reduced-motion")
        ? reduce
        : query.includes("max-width")
          ? compact
          : false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    }))
  );
}

const html = () => document.documentElement;

beforeEach(() => {
  vi.useFakeTimers();
  html().dataset.theme = "dark";
  localStorage.clear();
  stubMedia();
});

afterEach(() => {
  finishThemeTransition();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  html().removeAttribute("data-theme");
  html().removeAttribute("data-theme-pref");
  html().removeAttribute("data-transition");
});

describe("changeTheme · V1 (klasik)", () => {
  beforeEach(() => {
    html().dataset.transition = "v1";
  });

  it("Dark → Light sunrise sahnesini oynatır; temayı örtü kapanırken uygular ve temizler", () => {
    changeTheme("light");
    expect(getThemeTransition()?.scene).toBe("sunrise");
    expect(html().dataset.theme).toBe("dark");

    vi.advanceTimersByTime(TRANSITION_APPLY_AT_MS);
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).not.toBeNull();

    vi.advanceTimersByTime(TRANSITION_TOTAL_MS - TRANSITION_APPLY_AT_MS);
    expect(getThemeTransition()).toBeNull();
  });

  it("Light → Dark storm sahnesini oynatır", () => {
    html().dataset.theme = "light";
    changeTheme("dark");
    expect(getThemeTransition()?.scene).toBe("storm");
    vi.advanceTimersByTime(TRANSITION_TOTAL_MS);
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).toBeNull();
  });

  it("V1 süreleri dar ekranda da değişmez", () => {
    stubMedia({ compact: true });
    changeTheme("light");
    expect(getThemeTransition()?.totalMs).toBe(TRANSITION_TOTAL_MS);
  });
});

describe("changeTheme · V3 (taktik, varsayılan)", () => {
  it("paket işareti yokken varsayılan V3'tür: Light → Dark v3-blackout, tema son lamba söndüğünde değişir", () => {
    html().dataset.theme = "light";
    changeTheme("dark");
    const transition = getThemeTransition();
    const timing = sceneTimings["v3-blackout"];
    expect(transition?.scene).toBe("v3-blackout");
    expect(transition?.totalMs).toBe(timing.totalMs);
    expect(transition?.compact).toBe(false);
    expect(typeof transition?.startedAt).toBe("number");

    vi.advanceTimersByTime(timing.applyAtMs - 1);
    expect(html().dataset.theme).toBe("light");
    vi.advanceTimersByTime(1);
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).not.toBeNull();

    vi.advanceTimersByTime(timing.totalMs - timing.applyAtMs);
    expect(getThemeTransition()).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("Dark → Light v3-daybreak oynar, tema güneş ışığı yayıldığında uygulanır", () => {
    html().dataset.transition = "v3";
    changeTheme("light");
    const timing = sceneTimings["v3-daybreak"];
    expect(getThemeTransition()?.scene).toBe("v3-daybreak");

    vi.advanceTimersByTime(timing.applyAtMs - 1);
    expect(html().dataset.theme).toBe("dark");
    vi.advanceTimersByTime(1);
    expect(html().dataset.theme).toBe("light");

    vi.advanceTimersByTime(timing.totalMs);
    expect(getThemeTransition()).toBeNull();
  });

  it("dar ekranda kısa çizelge seçilir ve sahneye compact bayrağı iletilir", () => {
    stubMedia({ compact: true });
    changeTheme("light");
    const transition = getThemeTransition();
    expect(transition?.compact).toBe(true);
    expect(transition?.totalMs).toBe(sceneTimings["v3-daybreak"].compact?.totalMs);

    vi.advanceTimersByTime((sceneTimings["v3-daybreak"].compact?.applyAtMs ?? 0) - 1);
    expect(html().dataset.theme).toBe("dark");
    vi.advanceTimersByTime(1);
    expect(html().dataset.theme).toBe("light");
  });

  it("geçersiz paket değeri güvenle V3'e düşer", () => {
    html().dataset.transition = "v9-bilinmeyen";
    html().dataset.theme = "light";
    changeTheme("dark");
    expect(getThemeTransition()?.scene).toBe("v3-blackout");
  });

  it("toplam süre 2,6 sn'yi aşmaz, tema örtü kapanmadan ve sahnenin ortasında uygulanır", () => {
    for (const scene of ["v3-blackout", "v3-daybreak"] as const) {
      for (const compact of [false, true]) {
        const timing = resolveSceneTiming(scene, compact);
        expect(timing.totalMs).toBeGreaterThanOrEqual(2000);
        expect(timing.totalMs).toBeLessThanOrEqual(2600);
        expect(timing.applyAtMs).toBeGreaterThan(timing.totalMs * 0.4);
        expect(timing.applyAtMs).toBeLessThan(timing.totalMs * 0.8);
      }
    }
  });
});

describe("changeTheme · V2 (taktik, legacy)", () => {
  beforeEach(() => {
    html().dataset.transition = "v2";
  });

  it("V2 seçiliyken Light → Dark blackout oynar", () => {
    html().dataset.theme = "light";
    changeTheme("dark");
    const transition = getThemeTransition();
    expect(transition?.scene).toBe("blackout");
    expect(transition?.totalMs).toBe(sceneTimings.blackout.totalMs);
    expect(html().dataset.theme).toBe("light");

    vi.advanceTimersByTime(sceneTimings.blackout.applyAtMs);
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).not.toBeNull();

    vi.advanceTimersByTime(sceneTimings.blackout.totalMs - sceneTimings.blackout.applyAtMs);
    expect(getThemeTransition()).toBeNull();
  });

  it("Dark → Light daybreak sahnesini oynatır ve temayı %66 noktasında uygular", () => {
    changeTheme("light");
    expect(getThemeTransition()?.scene).toBe("daybreak");

    vi.advanceTimersByTime(sceneTimings.daybreak.applyAtMs - 1);
    expect(html().dataset.theme).toBe("dark");
    vi.advanceTimersByTime(1);
    expect(html().dataset.theme).toBe("light");

    vi.advanceTimersByTime(sceneTimings.daybreak.totalMs);
    expect(getThemeTransition()).toBeNull();
  });

  it("dar ekranda kısa süreler kullanılır", () => {
    stubMedia({ compact: true });
    changeTheme("light");
    expect(getThemeTransition()?.totalMs).toBe(sceneTimings.daybreak.compact?.totalMs);
  });

  it("toplam süre 2,4 sn'yi aşmaz ve tema örtü kapanmadan uygulanır", () => {
    for (const scene of ["blackout", "daybreak"] as const) {
      for (const compact of [false, true]) {
        const timing = resolveSceneTiming(scene, compact);
        expect(timing.totalMs).toBeLessThanOrEqual(2400);
        expect(timing.applyAtMs).toBeLessThan(timing.totalMs);
      }
    }
  });
});

describe("changeTheme · minimal ve ortak davranış", () => {
  it("minimal pakette sahne oynamaz, tema anında değişir", () => {
    html().dataset.transition = "minimal";
    changeTheme("light");
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();
  });

  it("aynı temaya geçişte sahne oynamaz", () => {
    changeTheme("dark");
    expect(getThemeTransition()).toBeNull();
  });

  it("hareket azaltma istenirse tema anında değişir, sahne yok (V3, V2 ve V1)", () => {
    stubMedia({ reduce: true });
    changeTheme("light");
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();

    html().dataset.transition = "v2";
    changeTheme("dark");
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).toBeNull();

    html().dataset.transition = "v1";
    changeTheme("light");
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();

    // Ertelenmiş bir sahne zamanlayıcısı kalmadığını kanıtla: zaman ilerlese de tema geri dönmez.
    vi.advanceTimersByTime(6000);
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("art arda geçişlerde son istek kazanır ve DOM temizlenir", () => {
    changeTheme("light");
    vi.advanceTimersByTime(200);
    changeTheme("dark");
    vi.advanceTimersByTime(5000);
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).toBeNull();
  });

  it("çok hızlı tekrarlı tıklamada hiçbir zamanlayıcı geride kalmaz", () => {
    for (let index = 0; index < 8; index += 1) {
      changeTheme(index % 2 === 0 ? "light" : "dark");
      vi.advanceTimersByTime(50);
    }
    vi.advanceTimersByTime(6000);
    expect(getThemeTransition()).toBeNull();
    expect(html().dataset.theme).toBe("dark");
    expect(vi.getTimerCount()).toBe(0);
  });

  it("finishThemeTransition bekleyen tercihi uygular", () => {
    changeTheme("light");
    finishThemeTransition();
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();
  });
});

describe("sceneFor", () => {
  it("paketi ve hedef temayı sahneye eşler", () => {
    expect(sceneFor("v3", "light")).toBe("v3-daybreak");
    expect(sceneFor("v3", "dark")).toBe("v3-blackout");
    expect(sceneFor("v1", "light")).toBe("sunrise");
    expect(sceneFor("v1", "dark")).toBe("storm");
    expect(sceneFor("v2", "light")).toBe("daybreak");
    expect(sceneFor("v2", "dark")).toBe("blackout");
    expect(sceneFor("minimal", "light")).toBeNull();
    expect(sceneFor("minimal", "dark")).toBeNull();
  });
});
