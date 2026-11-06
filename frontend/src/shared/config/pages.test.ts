import { describe, expect, it } from "vitest";
import { legalPages, pages, supportPages } from "./pages";
import { isLiveSocialLink, socialLinks } from "./social";

describe("pages", () => {
  it("Ayarlar sayfası kayıtlıdır ve arama motorlarına açılmaz", () => {
    const settings = pages.find((page) => page.id === "settings");
    expect(settings?.href).toBe("/settings");
    expect(settings?.indexable).toBe(false);
  });

  it("her href benzersiz ve gerçek bir yoldur", () => {
    const hrefs = pages.map((page) => page.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
    for (const href of hrefs) {
      expect(href).toMatch(/^\/($|[a-z0-9-/]+$)/);
    }
  });

  it("footer grupları: destek SSS ve İletişim, yasal dört metin", () => {
    expect(supportPages.map((page) => page.id)).toEqual(["faq", "contact"]);
    expect(legalPages.map((page) => page.id)).toEqual([
      "kvkk",
      "privacy",
      "terms",
      "cookies"
    ]);
  });
});

describe("socialLinks", () => {
  it("dört platform vardır; adres verilmeyen ikon tıklanabilir sayılmaz", () => {
    expect(socialLinks.map((link) => link.id)).toEqual([
      "x",
      "instagram",
      "linkedin",
      "github"
    ]);
    for (const link of socialLinks) {
      expect(isLiveSocialLink(link)).toBe(link.href.startsWith("https://"));
    }
  });

  it("https olmayan adresler etkin sayılmaz", () => {
    expect(isLiveSocialLink({ id: "x", href: "http://example.invalid" })).toBe(false);
    expect(isLiveSocialLink({ id: "x", href: "javascript:void(0)" })).toBe(false);
    expect(isLiveSocialLink({ id: "x", href: "" })).toBe(false);
  });
});
