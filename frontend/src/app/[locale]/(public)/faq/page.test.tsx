import { fireEvent, render, screen, within } from "@testing-library/react";
import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { faqItems } from "@/shared/config/faq";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import FaqPage from "./page";

let activeMessages: typeof tr | typeof en = tr;

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: activeMessages === tr ? "tr" : "en",
      messages: activeMessages,
      namespace: namespace as "Faq"
    })
}));
vi.mock("@/shared/lib/seo/page-metadata", () => ({ buildPageMetadata: vi.fn() }));

async function renderFaq(messages: typeof tr | typeof en = tr) {
  activeMessages = messages;
  render(await FaqPage());
}

describe("FaqPage", () => {
  it("başlık ve açıklamayı gösterir", async () => {
    await renderFaq();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Sıkça Sorulan Sorular");
    expect(
      screen.getByText(
        "LobbyTwoZero hakkında merak ettiğin soruların cevaplarını burada bulabilirsin."
      )
    ).toBeInTheDocument();
  });

  it("altı kategori başlığını sırayla gösterir", async () => {
    await renderFaq();

    expect(
      screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent)
    ).toEqual([
      "Genel",
      "Hesap ve Profil",
      "Oyunlar ve Kütüphane",
      "Sosyal ve Topluluk",
      "Donanım ve Performans",
      "Güvenlik ve Gizlilik"
    ]);
  });

  it("26 soru gösterir ve hepsi kapalı başlar", async () => {
    await renderFaq();

    const buttons = screen
      .getAllByRole("button")
      .filter((button) => button.hasAttribute("aria-controls"));
    expect(buttons).toHaveLength(26);
    for (const button of buttons) {
      expect(button).toHaveAttribute("aria-expanded", "false");
    }
  });

  it("her sorunun cevabı sayfada bulunur ve soru açılınca görünür olur", async () => {
    await renderFaq();

    for (const item of faqItems) {
      expect(screen.getByRole("button", { name: tr.Faq.items[item.id].title })).toBeInTheDocument();
      expect(document.body).toHaveTextContent(tr.Faq.items[item.id].answer);
    }

    const button = screen.getByRole("button", { name: "LobbyTwoZero nedir?" });
    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    expect(
      screen.getByText(/sosyal bir oyun platformudur/, { ignore: "[inert] *" })
    ).toBeInTheDocument();
  });

  it("kategori bağlantıları ilgili bölümlere gider", async () => {
    await renderFaq();

    const nav = screen.getByRole("navigation", { name: "Kategoriler" });
    const links = within(nav).getAllByRole("link");
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "#faq-general",
      "#faq-account",
      "#faq-games",
      "#faq-social",
      "#faq-hardware",
      "#faq-security"
    ]);
    for (const link of links) {
      expect(document.querySelector(link.getAttribute("href") as string)).not.toBeNull();
    }
  });

  it("soru alanı 896px ile sınırlıdır ve ortalanmıştır", async () => {
    await renderFaq();

    const column = screen.getByRole("heading", { level: 1 }).closest("[data-page-header]")
      ?.parentElement as HTMLElement;
    expect(column.className).toContain("max-w-[56rem]");
    expect(column.className).toContain("mx-auto");
  });

  it("İngilizce başlıkları ve sorular sunar", async () => {
    await renderFaq(en);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Frequently Asked Questions"
    );
    expect(
      screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent)
    ).toEqual([
      "General",
      "Account and Profile",
      "Games and Library",
      "Social and Community",
      "Hardware and Performance",
      "Security and Privacy"
    ]);
    expect(screen.getByRole("button", { name: "What is LobbyTwoZero?" })).toBeInTheDocument();
  });
});
