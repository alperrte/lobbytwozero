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

  it("öğeleri gruplara belirlenen sırayla dağıtır (toplam 13)", () => {
    expect(
      navigationGroups.map((group) => group.items.map((item) => item.labelKey))
    ).toEqual([
      ["home", "games", "library", "reviews", "news", "forYou"],
      ["community", "friends", "messages", "findTeam", "events"],
      ["hardware", "performance"]
    ]);
    expect(primaryNavigation).toHaveLength(13);
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
  it("yalnız Ana Sayfa kullanılabilir, kalan 12 öğe 'Yakında' durumundadır", () => {
    const available = primaryNavigation.filter(isNavItemAvailable);
    expect(available.map((item) => item.id)).toEqual(["home"]);
    expect(primaryNavigation.filter((item) => !isNavItemAvailable(item))).toHaveLength(12);
  });

  it("Yakında öğeleri href taşımaz (kırık link ve sahte rota yok)", () => {
    for (const item of primaryNavigation) {
      if (item.id !== "home") {
        expect(item.href).toBeUndefined();
      }
    }
  });

  it("Profil, Bilgisayarlarım ve Kampanyalar menüde yer almaz", () => {
    const ids = primaryNavigation.map((item) => item.id);
    for (const removed of ["profile", "myComputers", "campaigns"]) {
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
    expect(findActiveItem("/bilinmeyen")).toBeUndefined();
  });

  it("href verilen bir Yakında öğesi otomatik olarak aktif olur (bileşen değişmeden)", () => {
    const enabled = [
      ...primaryNavigation,
      { id: "games-live", labelKey: "games" as const, icon: "gamepad" as const, href: "/games" as AppPathname }
    ];
    expect(findActiveItem("/games/42", enabled)?.id).toBe("games-live");
  });
});
