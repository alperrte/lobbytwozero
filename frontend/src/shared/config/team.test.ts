import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { isLiveHref, teamMemberIds, teamMembers } from "./team";

const publicDir = join(process.cwd(), "public");

describe("team config", () => {
  it("beş üyeyi sırayla ve doğru servislerle sunar", () => {
    expect(teamMembers.map((member) => member.name)).toEqual([
      "Alper Temiz",
      "Nisa Camcı",
      "Umut Yeşilyurt",
      "Hamza Taşbay",
      "Kerem Kalyoncu"
    ]);
    expect(teamMembers.map((member) => member.id)).toEqual([...teamMemberIds]);
    expect(teamMembers.map((member) => member.readmeTitle)).toEqual([
      "Tech Oracle",
      "Guild Master",
      "Party Summoner",
      "Lore Keeper",
      "Gate Paladin"
    ]);
    expect(teamMembers[0].services).toEqual([
      "Hardware",
      "Recommendation",
      "Performance",
      "LTZ Performance Web"
    ]);
  });

  it("GitHub ve LinkedIn bağlantıları doğru ve https'tir", () => {
    expect(teamMembers.map((member) => member.github)).toEqual([
      "https://github.com/alperrte",
      "https://github.com/Camcinisa",
      "https://github.com/Umutys61",
      "https://github.com/HmzT270",
      "https://github.com/KeremKalyoncu"
    ]);
    expect(teamMembers.map((member) => member.linkedin)).toEqual([
      "https://www.linkedin.com/in/alpertemizz/",
      "https://www.linkedin.com/in/nisa-camci/",
      "https://www.linkedin.com/in/umut-ye%C5%9Filyurt",
      "https://www.linkedin.com/in/hamza-tasbay/",
      "https://www.linkedin.com/in/kerem-kalyoncu/"
    ]);
  });

  it("avatar dosyaları public altında gerçekten vardır", () => {
    for (const member of teamMembers) {
      expect(existsSync(join(publicDir, member.avatar)), member.avatar).toBe(true);
    }
  });

  it("her üyenin CV bağlantısı public altındaki gerçek bir PDF'e gider", () => {
    expect(teamMembers.map((member) => member.cvHref)).toEqual([
      "/assets/cv/alper-temiz-cv.pdf",
      "/assets/cv/nisa-camci-cv.pdf",
      "/assets/cv/umut-yesilyurt-cv.pdf",
      "/assets/cv/hamza-tasbay-cv.pdf",
      "/assets/cv/kerem-kalyoncu-cv.pdf"
    ]);
    for (const member of teamMembers) {
      expect(isLiveHref(member.cvHref), member.name).toBe(true);
      const file = join(publicDir, member.cvHref as string);
      expect(existsSync(file), member.cvHref as string).toBe(true);
      expect(readFileSync(file).subarray(0, 5).toString("latin1"), member.name).toBe("%PDF-");
    }
  });

  it("e-posta alanı veya mailto bağlantısı içermez", () => {
    for (const member of teamMembers) {
      expect(Object.keys(member)).not.toContain("email");
      expect(JSON.stringify(member)).not.toMatch(/mailto:|@[a-z0-9-]+\./i);
    }
  });

  it.each([
    ["tr", tr],
    ["en", en]
  ] as const)("%s: her üyenin rol özeti dolu", (_, messages) => {
    for (const id of teamMemberIds) {
      expect(messages.About.team.members[id].role.trim().length).toBeGreaterThan(20);
    }
  });

  it("isLiveHref yalnız https veya site içi yolu etkin sayar", () => {
    expect(isLiveHref(null)).toBe(false);
    expect(isLiveHref("")).toBe(false);
    expect(isLiveHref("http://a.b/cv.pdf")).toBe(false);
    expect(isLiveHref("javascript:alert(1)")).toBe(false);
    expect(isLiveHref("https://a.b/cv.pdf")).toBe(true);
    expect(isLiveHref("/cv/a.pdf")).toBe(true);
  });
});
