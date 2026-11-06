import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  changeTheme,
  finishThemeTransition,
  getThemeTransition,
  TRANSITION_APPLY_AT_MS,
  TRANSITION_TOTAL_MS
} from "./theme-transition";

function stubMotion(reduce: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("prefers-reduced-motion") ? reduce : false,
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
  stubMotion(false);
});

afterEach(() => {
  finishThemeTransition();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  html().removeAttribute("data-theme");
  html().removeAttribute("data-theme-pref");
});

describe("changeTheme", () => {
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

  it("aynı temaya geçişte sahne oynamaz", () => {
    changeTheme("dark");
    expect(getThemeTransition()).toBeNull();
  });

  it("hareket azaltma istenirse tema anında değişir, sahne yok", () => {
    stubMotion(true);
    changeTheme("light");
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();
  });

  it("art arda geçişlerde son istek kazanır ve DOM temizlenir", () => {
    changeTheme("light");
    vi.advanceTimersByTime(200);
    changeTheme("dark");
    vi.advanceTimersByTime(TRANSITION_TOTAL_MS * 2);
    expect(html().dataset.theme).toBe("dark");
    expect(getThemeTransition()).toBeNull();
  });

  it("finishThemeTransition bekleyen tercihi uygular", () => {
    changeTheme("light");
    finishThemeTransition();
    expect(html().dataset.theme).toBe("light");
    expect(getThemeTransition()).toBeNull();
  });
});
