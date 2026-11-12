import { describe, expect, it } from "vitest";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import {
  findActiveItem,
  isActivePath,
  isNavItemAvailable,
  navigationGroups,
  primaryNavigation,
  settingsNavigation
} from "./navigation";

describe("settingsNavigation", () => {
  it("Ayarlar diyalog değil gerçek /settings sayfasına bağlanır ve ana listede yer almaz", () => {
    expect(settingsNavigation.href).toBe("/settings");
    expect(primaryNavigation.some((item) => item.id === "settings")).toBe(false);
  });
});

describe("navigationGroups", () => {
  it("üç grubu belirlenen sırayla sunar", () => {
    expect(navigationGroups.map((group) => group.id)).toEqual([
      "general",
      "social",
      "hardware"
    ]);
  });

  it("öğeleri gruplara belirlenen sırayla dağıtır (toplam 14)", () => {
    expect(
      navigationGroups.map((group) => group.items.map((item) => item.labelKey))
    ).toEqual([
      ["home", "games", "library", "reviews", "news", "forYou"],
      ["community", "friends", "messages", "findTeam", "events"],
      ["hardware", "campaigns", "performance"]
    ]);
    expect(primaryNavigation).toHaveLength(14);
  });

  it("öğe kimlikleri tekildir", () => {
    const ids = primaryNavigation.map((item) => item.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("her öğe backend servisine eşlenir (Ana Sayfa hariç)", () => {
    const services = Object.fromEntries(
      primaryNavigation.map((item) => [item.id, item.service])
    );
    expect(services).toEqual({
      home: undefined,
      games: "game-service",
      library: "library-service",
      reviews: "review-service",
      news: "content-service",
      forYou: "recommendation-service",
      community: "community-service",
      friends: "social-service",
      messages: "messaging-service",
      findTeam: "lfp-service",
      events: "event-service",
      hardware: "hardware-service",
      campaigns: "hardware-service",
      performance: "performance-service"
    });
  });

  it("auth, user, mail, notification ve admin menüde yer almaz", () => {
    const services = primaryNavigation.map((item) => item.service ?? "");
    for (const excluded of [
      "auth-service",
      "user-service",
      "mail-service",
      "notification-service",
      "admin-service"
    ]) {
      expect(services).not.toContain(excluded);
    }
  });
});

describe("primaryNavigation", () => {
  it("Ana Sayfa, Oyunlar, Kütüphane, İncelemeler, Topluluk, Arkadaşlar, Donanımlar ve Kampanyalar gerçek rotalara bağlanır", () => {
    const available = primaryNavigation.filter(isNavItemAvailable);
    expect(available.map((item) => item.id)).toEqual(["home", "games", "library", "reviews", "community", "friends", "hardware", "campaigns"]);
    expect(available.find((item) => item.id === "library")?.href).toBe("/library");
    expect(available.find((item) => item.id === "reviews")?.href).toBe("/reviews");
    expect(primaryNavigation.filter((item) => !isNavItemAvailable(item))).toHaveLength(6);
  });

  it.each(["/reviews", "/reviews/games/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66", "/reviews/games/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66/write", "/reviews/games/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66/statistics", "/reviews/0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66"])("Review rotasında aktif öğeyi korur: %s", (pathname) => {
    expect(findActiveItem(pathname)?.id).toBe("reviews");
  });

  it("Yakında öğeleri href taşımaz (kırık link ve sahte rota yok)", () => {
    for (const item of primaryNavigation) {
      if (!["home", "games", "library", "reviews", "community", "friends", "hardware", "campaigns"].includes(item.id)) {
        expect(item.href).toBeUndefined();
      }
    }
  });

  it("Profil ve Bilgisayarlarım ana menüde yer almaz", () => {
    const ids = primaryNavigation.map((item) => item.id);
    for (const removed of ["profile", "myComputers"]) {
      expect(ids).not.toContain(removed);
    }
  });

  it("kırık bağlantı üretmez: href varsa gerçek bir yoldur, '#' değildir", () => {
    for (const item of primaryNavigation) {
      if (item.href !== undefined) {
        expect(item.href).toMatch(/^\/($|[a-z0-9-/]+$)/);
        expect(item.href).not.toContain("#");
      }
    }
  });

  it("Donanım Detayı menü öğesi değildir", () => {
    expect(primaryNavigation.some((item) => /detail/i.test(item.id))).toBe(false);
  });
});

describe("isActivePath / findActiveItem", () => {
  it("kök yol yalnız tam eşleşmede aktiftir", () => {
    expect(isActivePath("/", "/")).toBe(true);
    expect(isActivePath("/hardware", "/")).toBe(false);
  });

  it("alt yolları aktif sayar, benzer önekleri saymaz", () => {
    expect(isActivePath("/hardware/12", "/hardware")).toBe(true);
    expect(isActivePath("/hardwares", "/hardware")).toBe(false);
  });

  it("aktif öğeyi bulur, yoksa undefined döner", () => {
    expect(findActiveItem("/")?.id).toBe("home");
    expect(findActiveItem("/hardware/campaigns")?.id).toBe("campaigns");
    expect(findActiveItem("/hardware/123")?.id).toBe("hardware");
    expect(findActiveItem("/bilinmeyen")).toBeUndefined();
  });

  it("href verilen bir Yakında öğesi otomatik olarak aktif olur (bileşen değişmeden)", () => {
    const pending = { id: "library-live", labelKey: "library" as const, icon: "library" as const };
    expect(isNavItemAvailable(pending)).toBe(false);
    const enabled = [
      ...primaryNavigation.filter((item) => item.id !== "library"),
      { ...pending, href: "/library" as AppPathname }
    ];
    expect(findActiveItem("/library/42", enabled)?.id).toBe("library-live");
  });

  it.each(["/library", "/library/favorites", "/library/completed", "/library/add", "/library/privacy", "/library/users/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66"])("Library rotasında aktif öğeyi korur: %s", (pathname) => {
    expect(findActiveItem(pathname)?.id).toBe("library");
  });

  it("Library'ye benzeyen önekler aktif sayılmaz", () => {
    expect(findActiveItem("/library-other")).toBeUndefined();
  });
});
