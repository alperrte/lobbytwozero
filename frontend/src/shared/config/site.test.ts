import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  formatPageTitle,
  getSiteUrl,
  isIndexable,
  SITE_NAME,
  SITE_SHORT_NAME,
  THEME_COLORS
} from "./site";

describe("sayfa başlığı biçimi", () => {
  it("varsayılan ad LobbyTwoZero, sayfalar 'LTZ | Başlık' biçimindedir", () => {
    expect(SITE_NAME).toBe("LobbyTwoZero");
    expect(SITE_SHORT_NAME).toBe("LTZ");
    expect(formatPageTitle("Anasayfa")).toBe("LTZ | Anasayfa");
    expect(formatPageTitle("Profil")).toBe("LTZ | Profil");
  });
});

describe("getSiteUrl", () => {
  it("tanımsız veya boş değerde domain uydurmaz", () => {
    expect(getSiteUrl(undefined)).toBeUndefined();
    expect(getSiteUrl("   ")).toBeUndefined();
  });

  it("https site kökünü origin olarak döndürür", () => {
    expect(getSiteUrl("https://ltz.example/yol?x=1")?.toString()).toBe(
      "https://ltz.example/"
    );
  });

  it("http'yi yalnız localhost için kabul eder", () => {
    expect(getSiteUrl("http://localhost:5101")?.origin).toBe(
      "http://localhost:5101"
    );
    expect(getSiteUrl("http://ltz.example")).toBeUndefined();
  });

  it("geçersiz değeri reddeder", () => {
    expect(getSiteUrl("ltz.example")).toBeUndefined();
    expect(getSiteUrl("javascript:alert(1)")).toBeUndefined();
  });
});

describe("isIndexable", () => {
  const prod = new URL("https://ltz.example");

  it("yalnız production + yerel olmayan site kökünde true", () => {
    expect(isIndexable(prod, "production")).toBe(true);
  });

  it("development, kök yok veya localhost durumunda false", () => {
    expect(isIndexable(prod, "development")).toBe(false);
    expect(isIndexable(undefined, "production")).toBe(false);
    expect(isIndexable(new URL("http://localhost:3000"), "production")).toBe(
      false
    );
  });
});

describe("globals.css tokenları", () => {
  const css = readFileSync(join(__dirname, "../../app/globals.css"), "utf8");
  const block = (selector: RegExp) => css.match(selector)?.[1] ?? "";
  const dark = block(/:root,\s*:root\[data-theme="dark"\]\s*\{([^}]*)\}/);
  const light = block(/:root\[data-theme="light"\]\s*\{([^}]*)\}/);

  function expectTokens(source: string, expected: Record<string, string>) {
    for (const [name, hex] of Object.entries(expected)) {
      expect(source).toContain("--ltz-" + name + ": " + hex + ";");
    }
  }

  it("Dark paleti onaylı değerlerle birebir aynıdır", () => {
    expectTokens(dark, {
      background: "#0b0b0f",
      surface: "#141419",
      "surface-elevated": "#1e1f26",
      primary: "#e11d2e",
      secondary: "#db1b60",
      accent: "#ff2e88",
      "text-primary": "#f1f1f1",
      "text-secondary": "#a0a7b4",
      success: "#22c55e",
      warning: "#f59e0b",
      border: "#34343c",
      "control-border": "#737985"
    });
  });

  it("Light paleti belgelenen değerlerle aynıdır", () => {
    expectTokens(light, {
      background: "#f5f5f7",
      surface: "#ffffff",
      "surface-elevated": "#eeeff2",
      primary: "#e11d2e",
      secondary: "#db1b60",
      accent: "#ff2e88",
      "text-primary": "#17171b",
      "text-secondary": "#555b66",
      success: "#167a3e",
      warning: "#915600",
      border: "#d6d7de",
      "control-border": "#8c919b"
    });
  });

  it("meta theme-color değerleri surface tokenlarıyla eşleşir", () => {
    expectTokens(dark, { surface: THEME_COLORS.dark });
    expectTokens(light, { surface: THEME_COLORS.light });
  });

  function resolve(source: string, name: string): string {
    const value = source.match(new RegExp("--ltz-" + name + ":\\s*([^;]+);"))?.[1];
    if (!value) {
      throw new Error("Token yok: " + name);
    }
    const ref = value.trim().match(/^var\(--ltz-([a-z-]+)\)$/);
    return ref ? resolve(source, ref[1]) : value.trim();
  }

  function channel(value: number): number {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  }

  function luminance(hex: string): number {
    const n = Number.parseInt(hex.slice(1), 16);
    return (
      0.2126 * channel((n >> 16) & 255) +
      0.7152 * channel((n >> 8) & 255) +
      0.0722 * channel(n & 255)
    );
  }

  function contrast(a: string, b: string): number {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
  }

  it.each([
    ["Dark", dark, ["text-primary", "text-secondary", "brand-text"]],
    [
      "Light",
      light,
      ["text-primary", "text-secondary", "brand-text", "success", "warning"]
    ]
  ] as const)(
    "%s temada metin tokenları tüm yüzeylerde en az 4,5:1 kontrast verir",
    (_theme, source, textTokens) => {
      for (const surface of ["background", "surface", "surface-elevated"]) {
        for (const text of textTokens) {
          const ratio = contrast(
            resolve(source, text),
            resolve(source, surface)
          );
          expect(
            ratio,
            text + " / " + surface + " = " + ratio.toFixed(2)
          ).toBeGreaterThanOrEqual(4.5);
        }
      }
    }
  );
});
