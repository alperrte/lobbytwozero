import { afterEach, describe, expect, it, vi } from "vitest";
import { preferencesInitScript } from "./init-script";
import {
  applyTransitionPack,
  defaultTransitionPack,
  parseThemePreference,
  parseTransitionPack,
  readStored,
  resolveTheme,
  SIDEBAR_STORAGE_KEY,
  THEME_STORAGE_KEY,
  TRANSITION_STORAGE_KEY,
  writeStored
} from "./preferences";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
  delete document.documentElement.dataset.themePref;
  delete document.documentElement.dataset.sidebar;
  delete document.documentElement.dataset.transition;
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

describe("tema geçişi paketi", () => {
  it("varsayılan V3'tür; V2 legacy, V1 ve minimal tanınır; bilinmeyen değer varsayılana düşer", () => {
    expect(defaultTransitionPack).toBe("v3");
    expect(parseTransitionPack("v3")).toBe("v3");
    expect(parseTransitionPack("v2")).toBe("v2");
    expect(parseTransitionPack("v1")).toBe("v1");
    expect(parseTransitionPack("minimal")).toBe("minimal");
    expect(parseTransitionPack("v9")).toBe("v3");
    expect(parseTransitionPack("__proto__")).toBe("v3");
    expect(parseTransitionPack(undefined)).toBe("v3");
  });

  it("V2, V1 ve minimal işaret ve depolama yazar; varsayılan V3 ikisini de temizler", () => {
    applyTransitionPack("v2");
    expect(document.documentElement.dataset.transition).toBe("v2");
    expect(readStored(TRANSITION_STORAGE_KEY)).toBe("v2");

    applyTransitionPack("v1");
    expect(document.documentElement.dataset.transition).toBe("v1");
    expect(readStored(TRANSITION_STORAGE_KEY)).toBe("v1");

    applyTransitionPack("minimal");
    expect(document.documentElement.dataset.transition).toBe("minimal");
    expect(readStored(TRANSITION_STORAGE_KEY)).toBe("minimal");

    applyTransitionPack("v3");
    expect(document.documentElement.dataset.transition).toBeUndefined();
    expect(readStored(TRANSITION_STORAGE_KEY)).toBeNull();
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

  it("kayıtlı geçiş paketini ilk boyamadan önce uygular; varsayılan işaret bırakmaz", () => {
    window.localStorage.setItem(TRANSITION_STORAGE_KEY, "minimal");
    run(false);
    expect(document.documentElement.dataset.transition).toBe("minimal");

    delete document.documentElement.dataset.transition;
    window.localStorage.setItem(TRANSITION_STORAGE_KEY, "v3");
    run(false);
    expect(document.documentElement.dataset.transition).toBeUndefined();

    window.localStorage.setItem(TRANSITION_STORAGE_KEY, "bozuk-deger");
    run(false);
    expect(document.documentElement.dataset.transition).toBeUndefined();

    window.localStorage.setItem(TRANSITION_STORAGE_KEY, "v2");
    run(false);
    expect(document.documentElement.dataset.transition).toBe("v2");

    window.localStorage.setItem(TRANSITION_STORAGE_KEY, "v1");
    run(false);
    expect(document.documentElement.dataset.transition).toBe("v1");
  });

  it("depolama kapalıysa hata fırlatmaz", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(() => run(false)).not.toThrow();
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});
