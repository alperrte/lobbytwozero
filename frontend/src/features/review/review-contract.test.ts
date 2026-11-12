import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { canonicalPathFor } from "@/shared/lib/i18n/canonical-path";
import { getPathname } from "@/shared/lib/i18n/navigation";
import { pathnames } from "@/shared/lib/i18n/routing";
import { findPage } from "@/shared/config/pages";

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const reviewId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const root = process.cwd();

describe("Review rota sözleşmesi", () => {
  it("TR ve EN adresleri belirlenen slug'larla üretilir", () => {
    expect(getPathname({ locale: "tr", href: "/reviews" })).toBe("/tr/incelemeler");
    expect(getPathname({ locale: "tr", href: "/reviews/mine" })).toBe("/tr/incelemeler/incelemelerim");
    expect(getPathname({ locale: "en", href: "/reviews/mine" })).toBe("/en/reviews/mine");
    expect(getPathname({ locale: "en", href: "/reviews" })).toBe("/en/reviews");
    expect(getPathname({ locale: "tr", href: { pathname: "/reviews/games/[gameId]", params: { gameId } } })).toBe(`/tr/incelemeler/oyun/${gameId}`);
    expect(getPathname({ locale: "en", href: { pathname: "/reviews/games/[gameId]", params: { gameId } } })).toBe(`/en/reviews/games/${gameId}`);
    expect(getPathname({ locale: "tr", href: { pathname: "/reviews/games/[gameId]/write", params: { gameId } } })).toBe(`/tr/incelemeler/oyun/${gameId}/yaz`);
    expect(getPathname({ locale: "tr", href: { pathname: "/reviews/games/[gameId]/statistics", params: { gameId } } })).toBe(`/tr/incelemeler/oyun/${gameId}/istatistikler`);
    expect(getPathname({ locale: "tr", href: { pathname: "/reviews/[reviewId]", params: { reviewId } } })).toBe(`/tr/incelemeler/${reviewId}`);
    expect(getPathname({ locale: "en", href: { pathname: "/reviews/[reviewId]", params: { reviewId } } })).toBe(`/en/reviews/${reviewId}`);
  });

  it("ikinci adresler tek kanonik adrese yönlenir", () => {
    expect(canonicalPathFor("/reviews", "tr")).toBe("/incelemeler");
    expect(canonicalPathFor("/incelemeler", "en")).toBe("/reviews");
    expect(canonicalPathFor("/incelemeler", "tr")).toBeNull();
  });

  it("her Review rotasının gerçek bir sayfa klasörü vardır", () => {
    const keys = Object.keys(pathnames).filter((key) => key.startsWith("/reviews"));
    expect(keys).toHaveLength(6);
    for (const key of keys) {
      expect(existsSync(join(root, "src/app/[locale]/(dashboard)", key, "page.tsx")), key).toBe(true);
    }
  });

  it("Review sayfaları giriş gerektiren panel grubundadır ve indekslenmez", () => {
    const layout = readFileSync(join(root, "src/app/[locale]/(dashboard)/reviews/layout.tsx"), "utf8");
    expect(layout).toContain("index: false");
    expect(findPage("/reviews")?.indexable).toBe(false);
  });

  it("Review sayfaları metadata'ya inceleme metni, puan veya oyun adı taşımaz", () => {
    const dir = join(root, "src/app/[locale]/(dashboard)/reviews");
    const files = [join(dir, "page.tsx"), join(dir, "mine/page.tsx"), join(dir, "[reviewId]/page.tsx"), join(dir, "games/[gameId]/page.tsx"), join(dir, "games/[gameId]/write/page.tsx"), join(dir, "games/[gameId]/statistics/page.tsx")];
    for (const file of files) {
      const source = readFileSync(file, "utf8");
      expect(source, file).toContain("index: false");
      expect(source, file).not.toMatch(/dangerouslySetInnerHTML/);
    }
  });
});

describe("Review güvenlik taraması", () => {
  function sources(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) return sources(path);
      return /\.(ts|tsx)$/.test(entry.name) && !/\.test\./.test(entry.name) ? [path] : [];
    });
  }
  const files = sources(join(root, "src/features/review"));

  it("kullanıcı HTML'i render eden API kullanmaz", () => {
    for (const file of files) {
      expect(readFileSync(file, "utf8"), file).not.toMatch(/dangerouslySetInnerHTML|innerHTML\s*=/);
    }
  });

  it("tarayıcı depolamasında token veya inceleme verisi tutmaz", () => {
    for (const file of files) {
      expect(readFileSync(file, "utf8"), file).not.toMatch(/localStorage|sessionStorage|indexedDB/);
    }
  });

  it("Review servisinin portuna veya iç uca doğrudan istek atmaz", () => {
    for (const file of files) {
      const source = readFileSync(file, "utf8");
      expect(source, file).not.toMatch(/5086|\/internal\/v1|fetch\(/);
    }
  });

  it("başka feature'ların iç dosyalarını import etmez (yalnız açık export)", () => {
    for (const file of files) {
      const imports = [...readFileSync(file, "utf8").matchAll(/from "(@\/features\/[^"]+)"/g)].map((match) => match[1]);
      for (const target of imports) {
        expect(target, file).toBe("@/features/auth");
      }
    }
  });
});

describe("Review çeviri anahtarları", () => {
  function componentSources(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) return componentSources(path);
      return entry.name.endsWith(".tsx") && !entry.name.includes(".test.") ? [path] : [];
    });
  }

  function lookup(messages: unknown, key: string): unknown {
    return key.split(".").reduce<unknown>((value, part) => (typeof value === "object" && value !== null ? (value as Record<string, unknown>)[part] : undefined), messages);
  }

  it("bileşenlerde kullanılan her sabit anahtar TR ve EN'de tanımlıdır", () => {
    const used = new Set<string>();
    for (const file of componentSources(join(root, "src/features/review/components"))) {
      const source = readFileSync(file, "utf8");
      for (const call of source.matchAll(/\bt\(([^)]*)\)/g)) {
        for (const literal of call[1].matchAll(/"([A-Za-z][A-Za-z0-9.]*)"/g)) used.add(literal[1]);
      }
    }
    expect(used.size).toBeGreaterThan(100);
    const missing = [...used].filter((key) => typeof lookup(tr.Review, key) !== "string" || typeof lookup(en.Review, key) !== "string");
    expect(missing).toEqual([]);
  });

  it("dinamik anahtar aileleri TR ve EN'de eksiksizdir", () => {
    for (const messages of [tr.Review, en.Review]) {
      for (const key of ["rating", "recommendation", "review"]) {
        expect(typeof lookup(messages, `hub.concepts.${key}.title`)).toBe("string");
        expect(typeof lookup(messages, `hub.concepts.${key}.text`)).toBe("string");
      }
      for (const key of ["rate", "recommend", "write", "like", "statistics"]) expect(typeof lookup(messages, `hub.capabilities.${key}`)).toBe("string");
      for (const key of ["required", "tooLong", "invalidCharacters"]) expect(typeof lookup(messages, `form.issue.${key}`)).toBe("string");
    }
  });

  it("kullanıcıya görünen Türkçe metin bileşenlere gömülü değildir", () => {
    for (const file of componentSources(join(root, "src/features/review/components"))) {
      const source = readFileSync(file, "utf8");
      expect(source, file).not.toMatch(/>\s*[A-ZÇĞİÖŞÜ][a-zçğıöşü]+(?:\s+[a-zçğıöşü]+)+\s*</);
      expect(source, file).not.toMatch(/(?:aria-label|placeholder|title)="[^"{]*[çğıöşüÇĞİÖŞÜ][^"]*"/);
    }
  });
});
