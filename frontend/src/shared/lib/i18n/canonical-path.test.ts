import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { canonicalPathFor } from "./canonical-path";
import { locales } from "./config";
import { pathnames } from "./routing";
import { getPathname } from "./navigation";

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
  // Sayfalar herkese açık (`(public)`) giriş/kayıt (`(auth)`) ya da panel (`(dashboard)`) rota grubunda olabilir.
  const appDirs = ["(public)", "(auth)", "(dashboard)"].map((group) => join(process.cwd(), "src/app/[locale]", group));

  it.each([
    ["/library", "/kutuphane"],
    ["/library/favorites", "/kutuphane/favoriler"],
    ["/library/completed", "/kutuphane/bitirilenler"],
    ["/library/add", "/kutuphane/oyun-ekle"],
    ["/library/privacy", "/kutuphane/gizlilik"]
  ] as const)("Library rota çifti ve kanonik adres: %s", (pathname, turkish) => {
    expect(pathnames[pathname]).toEqual({ tr: turkish, en: pathname });
    expect(getPathname({ locale: "tr", href: pathname })).toBe(`/tr${turkish}`);
    expect(getPathname({ locale: "en", href: pathname })).toBe(`/en${pathname}`);
    expect(canonicalPathFor(pathname, "tr")).toBe(turkish);
    expect(canonicalPathFor(turkish, "en")).toBe(pathname);
  });

  it("Library profil rotası her iki dilde gerçek kullanıcı kimliğini korur", () => {
    const userId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
    const href = { pathname: "/library/users/[userId]" as const, params: { userId } };
    expect(getPathname({ locale: "tr", href })).toBe(`/tr/kutuphane/oyuncu/${userId}`);
    expect(getPathname({ locale: "en", href })).toBe(`/en/library/users/${userId}`);
  });

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
      const found = appDirs.some((dir) =>
        existsSync(join(internal === "/" ? dir : join(dir, internal), "page.tsx"))
      );
      expect(found, internal).toBe(true);
    }
    for (const locale of locales) {
      const slugs = Object.values(pathnames).map((value) =>
        typeof value === "string" ? value : value[locale]
      );
      expect(new Set(slugs).size).toBe(slugs.length);
    }
  });
});
