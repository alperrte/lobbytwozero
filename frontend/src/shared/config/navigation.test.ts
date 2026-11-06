import { describe, expect, it } from "vitest";
import {
  findActiveItem,
  isActivePath,
  primaryNavigation,
  type NavItem
} from "./navigation";

function flatten(items: readonly NavItem[]): NavItem[] {
  return items.flatMap((item) => [item, ...flatten(item.children ?? [])]);
}

describe("primaryNavigation", () => {
  const all = flatten(primaryNavigation);

  it("Profil (Bilgisayarlarım dahil), Donanımlar ve Kampanyalar öğelerini içerir", () => {
    const profile = primaryNavigation.find((item) => item.id === "profile");
    expect(profile?.children?.map((c) => c.labelKey)).toEqual(["myComputers"]);
    expect(all.map((item) => item.labelKey)).toEqual(
      expect.arrayContaining(["profile", "myComputers", "hardware", "campaigns"])
    );
  });

  it("kırık bağlantı üretmez: href varsa gerçek bir yoldur, '#' değildir", () => {
    for (const item of all) {
      if (item.href !== undefined) {
        expect(item.href).toMatch(/^\/($|[a-z0-9-/]+$)/);
        expect(item.href).not.toContain("#");
      }
    }
  });

  it("Donanım Detayı menü öğesi değildir", () => {
    expect(all.some((item) => /detail/i.test(item.id))).toBe(false);
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
