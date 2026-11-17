import { describe, expect, it } from "vitest";
import { ADMIN_GROUPS, ADMIN_SECTIONS } from "./registry";

describe("admin bölüm kaydı", () => {
  it("bölüm kimlikleri benzersizdir ve <grup>.<ad> biçimindedir", () => {
    const ids = ADMIN_SECTIONS.map((section) => section.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const section of ADMIN_SECTIONS) {
      expect(section.id.startsWith(`${section.group}.`)).toBe(true);
    }
  });

  it("her bölümün grubu tanımlıdır; grup kimlikleri benzersizdir", () => {
    const groupIds = ADMIN_GROUPS.map((group) => group.id);
    expect(new Set(groupIds).size).toBe(groupIds.length);
    for (const section of ADMIN_SECTIONS) {
      expect(groupIds).toContain(section.group);
    }
  });
});
