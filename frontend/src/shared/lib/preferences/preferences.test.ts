import { afterEach, describe, expect, it, vi } from "vitest";
import { preferencesInitScript } from "./init-script";
import {
  parseThemePreference,
  readStored,
  resolveTheme,
  SIDEBAR_STORAGE_KEY,
  THEME_STORAGE_KEY,
  writeStored
} from "./preferences";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
  delete document.documentElement.dataset.themePref;
  delete document.documentElement.dataset.sidebar;
});

describe("tema tercihi", () => {
  it("bilinmeyen değeri 'system' yapar", () => {
    expect(parseThemePreference("light")).toBe("light");
    expect(parseThemePreference("dark")).toBe("dark");
    expect(parseThemePreference("neon")).toBe("system");
    expect(parseThemePreference(null)).toBe("system");
  });

  it("system tercihini işletim sistemi tercihine göre çözer", () => {
    expect(resolveTheme("system", true)).toBe("dark");
    expect(resolveTheme("system", false)).toBe("light");
    expect(resolveTheme("light", true)).toBe("light");
  });
});

describe("depolama", () => {
  it("yazar, okur ve null ile siler", () => {
    writeStored(THEME_STORAGE_KEY, "dark");
    expect(readStored(THEME_STORAGE_KEY)).toBe("dark");
    writeStored(THEME_STORAGE_KEY, null);
    expect(readStored(THEME_STORAGE_KEY)).toBeNull();
  });

  it("depolama erişimi hata verirse sessizce null döner", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readStored(THEME_STORAGE_KEY)).toBeNull();
    expect(() => writeStored(THEME_STORAGE_KEY, "dark")).not.toThrow();
  });
});

describe("init betiği", () => {
  function run(prefersDark: boolean) {
    vi.stubGlobal("matchMedia", () => ({ matches: prefersDark }));
    new Function(preferencesInitScript)();
  }

  it("kayıt yokken sistem temasını uygular", () => {
    run(true);
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(document.documentElement.dataset.themePref).toBe("system");
    expect(document.documentElement.dataset.sidebar).toBeUndefined();
  });

  it("kayıtlı tercihi ve daraltılmış kenar çubuğunu uygular", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "light");
    window.localStorage.setItem(SIDEBAR_STORAGE_KEY, "collapsed");
    run(true);
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(document.documentElement.dataset.themePref).toBe("light");
    expect(document.documentElement.dataset.sidebar).toBe("collapsed");
  });

  it("depolama kapalıysa hata fırlatmaz", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(() => run(false)).not.toThrow();
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});
