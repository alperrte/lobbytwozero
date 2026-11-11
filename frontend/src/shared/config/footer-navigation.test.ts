import { describe, expect, it } from "vitest";
import { footerGroups } from "./footer-navigation";
import { primaryNavigation } from "./navigation";
import { pages } from "./pages";

describe("footerGroups", () => {
  it("dört grubu belirlenen sırayla sunar, her grupta üç öğe vardır", () => {
    expect(footerGroups.map((group) => group.id)).toEqual([
      "discover",
      "community",
      "performance",
      "support"
    ]);
    for (const group of footerGroups) {
      expect(group.entries).toHaveLength(3);
    }
  });

  it("öğeleri belirlenen sırayla dağıtır", () => {
    expect(footerGroups.map((group) => group.entries.map((entry) => entry.id))).toEqual([
      ["games", "news", "reviews"],
      ["community", "findTeam", "events"],
      ["hardware", "performance", "forYou"],
      ["about", "faq", "contact"]
    ]);
  });

  it("ürün öğeleri sidebar ile aynı kaynaktan gelir (href dahil)", () => {
    for (const group of footerGroups) {
      for (const entry of group.entries) {
        if (entry.source === "navigation") {
          const item = primaryNavigation.find((candidate) => candidate.id === entry.id);
          expect(item).toBeDefined();
          expect(entry.href).toBe(item?.href);
        }
      }
    }
  });

  it("hazır olmayan ürün öğeleri href taşımaz, destek öğeleri gerçek sayfalara gider", () => {
    for (const group of footerGroups.slice(0, 3)) {
      for (const entry of group.entries) {
        if (entry.id === "games") expect(entry.href).toBe("/games");
        else if (entry.id === "hardware") expect(entry.href).toBe("/hardware");
        else if (entry.id === "community") expect(entry.href).toBe("/community");
        else if (entry.id === "reviews") expect(entry.href).toBe("/reviews");
        else expect(entry.href).toBeUndefined();
      }
    }
    const known = new Set<string>(pages.map((page) => page.href));
    for (const entry of footerGroups[3].entries) {
      expect(known.has(entry.href as string)).toBe(true);
    }
  });

  it("Ana Sayfa, Ayarlar, Kütüphanem, Arkadaşlar ve Mesajlar footer'da yer almaz", () => {
    const ids = footerGroups.flatMap((group) => group.entries.map((entry) => entry.id as string));
    for (const excluded of ["home", "settings", "library", "friends", "messages"]) {
      expect(ids).not.toContain(excluded);
    }
  });
});
