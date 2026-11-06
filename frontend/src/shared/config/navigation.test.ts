import { describe, expect, it } from "vitest";
import {
  findActiveItem,
  isActivePath,
  primaryNavigation
} from "./navigation";

describe("primaryNavigation", () => {
  it("yalnız Ana Sayfa ve Donanımlar öğelerini içerir", () => {
    expect(primaryNavigation.map((item) => item.labelKey)).toEqual([
      "home",
      "hardware"
    ]);
  });

  it("Profil, Bilgisayarlarım ve Kampanyalar menüde yer almaz", () => {
    const ids = primaryNavigation.map((item) => item.id);
    for (const removed of ["profile", "myComputers", "campaigns"]) {
      expect(ids).not.toContain(removed);
    }
  });

  it("Donanımlar rotası hazır olmadığı için href taşımaz (kırık link yok)", () => {
    const hardware = primaryNavigation.find((item) => item.id === "hardware");
    expect(hardware?.href).toBeUndefined();
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
});
