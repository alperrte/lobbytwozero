import { render, screen, within } from "@testing-library/react";
import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { teamMembers } from "@/shared/config/team";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import AboutPage from "./page";

let activeMessages: typeof tr | typeof en = tr;

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: activeMessages === tr ? "tr" : "en",
      messages: activeMessages,
      namespace: namespace as "About"
    })
}));
vi.mock("@/shared/lib/seo/page-metadata", () => ({ buildPageMetadata: vi.fn() }));

async function renderAbout(messages: typeof tr | typeof en = tr) {
  activeMessages = messages;
  render(await AboutPage());
}

describe("AboutPage", () => {
  it("hero başlığını ve açıklamasını gösterir", async () => {
    await renderAbout();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Beş oyuncu. Tek bir lobi."
    );
    expect(
      screen.getByText(/oyun oynamayı seven beş geliştiricinin ortak tutkusundan doğdu/)
    ).toBeInTheDocument();
  });

  it("bölümleri sırayla sunar", async () => {
    await renderAbout();

    expect(
      screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent)
    ).toEqual([
      "Nasıl Başladık?",
      "Neyi Hedefliyoruz?",
      "Bu Lobinin Arkasındaki Ekip",
      "Aynı Oyunda, Aynı Lobide."
    ]);
  });

  it("dört hedef sütununu gösterir ve yayında olduklarını iddia etmez", async () => {
    await renderAbout();

    const vision = screen.getByRole("heading", { name: "Neyi Hedefliyoruz?" }).closest("section");
    expect(vision).not.toBeNull();
    expect(
      within(vision as HTMLElement)
        .getAllByRole("heading", { level: 3 })
        .map((heading) => heading.textContent)
    ).toEqual(["Oyun Keşfi", "Sosyal Bağlantılar", "Oyuncu Topluluğu", "Donanım ve Performans"]);
    expect(screen.getByText(/bugün yayında olan özellikler değil/)).toBeInTheDocument();
  });

  it("beş ekip kartı ad, unvan ve servislerle görünür", async () => {
    await renderAbout();

    expect(document.querySelectorAll("[data-team-card]")).toHaveLength(5);
    for (const member of teamMembers) {
      const card = document.querySelector(`[data-team-card="${member.id}"]`) as HTMLElement;
      expect(within(card).getByRole("heading", { level: 3 })).toHaveTextContent(member.name);
      expect(card).toHaveTextContent(member.readmeTitle);
      expect(card).toHaveTextContent(member.services.join(", "));
      const avatar = card.querySelector("img") as HTMLImageElement;
      expect(avatar.getAttribute("src")).toBe(member.avatar);
      expect(avatar.getAttribute("alt")).toBe("");
    }
  });

  it("her kartta doğru GitHub ve LinkedIn bağlantısı erişilebilir adla bulunur", async () => {
    await renderAbout();

    for (const member of teamMembers) {
      const github = screen.getByRole("link", { name: `${member.name} — GitHub` });
      expect(github).toHaveAttribute("href", member.github);
      expect(github).toHaveAttribute("target", "_blank");
      expect(github.getAttribute("rel")).toContain("noopener");
      const linkedin = screen.getByRole("link", { name: `${member.name} — LinkedIn` });
      expect(linkedin).toHaveAttribute("href", member.linkedin);
      expect(linkedin).toHaveAttribute("target", "_blank");
    }
  });

  it("her kartta CV, üyenin kendi PDF'ine yeni sekmede güvenle açılan bağlantıdır", async () => {
    await renderAbout();

    for (const member of teamMembers) {
      const cv = screen.getByRole("link", { name: `${member.name} — CV` });
      expect(cv).toHaveAttribute("href", member.cvHref as string);
      expect(cv).toHaveAttribute("target", "_blank");
      expect(cv.getAttribute("rel")).toContain("noopener");
      expect(screen.queryByRole("button", { name: /CV \(/ })).toBeNull();
    }
  });

  it("e-posta simgesi, mailto bağlantısı veya adresi yoktur", async () => {
    await renderAbout();

    expect(document.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(screen.queryByRole("link", { name: /e-?posta|mail/i })).toBeNull();
    expect(document.body.textContent).not.toMatch(/\S+@\S+\.\S+/);
    // Her kartta yalnız üç eylem vardır: GitHub, LinkedIn, CV.
    for (const card of document.querySelectorAll("[data-team-card]")) {
      expect(card.querySelectorAll("a, button")).toHaveLength(3);
    }
  });

  it("3+2 ortalı düzen için kartlar ortalanan sarmalayıcıdadır", async () => {
    await renderAbout();

    const card = document.querySelector("[data-team-card]") as HTMLElement;
    const list = card.parentElement as HTMLElement;
    expect(list.className).toContain("flex-wrap");
    expect(list.className).toContain("justify-center");
    expect(card.className).toContain("lg:w-[calc((100%-3rem)/3)]");
    expect(card.className).toContain("sm:w-[calc((100%-1.5rem)/2)]");
  });

  it("İngilizce içerik sunar, adlar ve servis adları çevrilmez", async () => {
    await renderAbout(en);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Five players. One lobby.");
    expect(screen.getByRole("heading", { name: "Same Game, Same Lobby." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Alper Temiz — GitHub" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Alper Temiz — CV" })).toHaveAttribute(
      "href",
      "/assets/cv/alper-temiz-cv.pdf"
    );
    expect(document.body).toHaveTextContent(
      "Hardware, Recommendation, Performance, LTZ Performance Web"
    );
  });
});
