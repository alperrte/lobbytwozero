import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { canonicalPathFor } from "./canonical-path";
import { locales } from "./config";
import { pathnames } from "./routing";

describe("canonicalPathFor", () => {
  it("kanonik adres için null döner", () => {
    expect(canonicalPathFor("/hakkimizda", "tr")).toBeNull();
    expect(canonicalPathFor("/about", "en")).toBeNull();
    expect(canonicalPathFor("/yasal/kullanim-kosullari", "tr")).toBeNull();
    expect(canonicalPathFor("/", "tr")).toBeNull();
  });

  it("dahili klasör adını ve diğer dilin slug'ını dilin kanonik adresine çevirir", () => {
    expect(canonicalPathFor("/about", "tr")).toBe("/hakkimizda");
    expect(canonicalPathFor("/hakkimizda", "en")).toBe("/about");
    expect(canonicalPathFor("/legal/privacy", "tr")).toBe("/yasal/gizlilik");
    expect(canonicalPathFor("/yasal/cerezler", "en")).toBe("/legal/cookies");
    expect(canonicalPathFor("/faq/", "tr")).toBe("/sss");
  });

  it("bilinmeyen adresi olduğu gibi bırakır (404'e düşer)", () => {
    expect(canonicalPathFor("/bilinmeyen", "tr")).toBeNull();
    expect(canonicalPathFor("/about/x", "tr")).toBeNull();
  });
});

describe("pathnames", () => {
  const appDir = join(process.cwd(), "src/app/[locale]/(public)");

  it("Türkçe slug'lar Türkçe, İngilizce slug'lar dahili anahtarla aynıdır", () => {
    for (const [internal, value] of Object.entries(pathnames)) {
      if (typeof value === "string") {
        continue;
      }
      expect(value.en).toBe(internal);
      expect(value.tr).not.toBe(value.en);
    }
  });

  it("her dahili anahtar için gerçek bir sayfa klasörü vardır ve slug'lar benzersizdir", () => {
    for (const internal of Object.keys(pathnames)) {
      const folder = internal === "/" ? appDir : join(appDir, internal);
      const groupFolders = [folder, join(process.cwd(), "src/app/[locale]/(dashboard)", internal)];
      expect(groupFolders.some((target) => existsSync(join(target, "page.tsx"))), internal).toBe(true);
    }
    for (const locale of locales) {
      const slugs = Object.values(pathnames).map((value) =>
        typeof value === "string" ? value : value[locale]
      );
      expect(new Set(slugs).size).toBe(slugs.length);
    }
  });
});
