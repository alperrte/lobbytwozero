import { render, screen, within } from "@testing-library/react";
import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { Footer } from "./footer";

let activeMessages: typeof tr | typeof en = tr;

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: activeMessages === tr ? "tr" : "en",
      messages: activeMessages,
      namespace: namespace as "Footer"
    })
}));

async function renderFooter(messages: typeof tr | typeof en = tr) {
  activeMessages = messages;
  render(await Footer());
  return screen.getByRole("contentinfo");
}

describe("Footer", () => {
  it("yeni marka açıklamasını gösterir, eskisini göstermez", async () => {
    const footer = await renderFooter();

    expect(footer).toHaveTextContent(
      "Oyunları keşfet, oyuncularla bağlantı kur, deneyimlerini paylaş. LobbyTwoZero, oyun dünyasını tek bir toplulukta buluşturur."
    );
    expect(footer).not.toHaveTextContent("donanım ve oyun performansı için");
  });

  it("İngilizce açıklamayı sunar", async () => {
    const footer = await renderFooter(en);

    expect(footer).toHaveTextContent(
      "Discover games, connect with players, and share your experiences. LobbyTwoZero brings the gaming world together in one community."
    );
  });

  it("Yakında sütunu ve rozeti içermez", async () => {
    const footer = await renderFooter();

    expect(footer).not.toHaveTextContent("Yakında");
    expect(within(footer).queryByRole("heading", { name: "Yakında" })).toBeNull();
    expect("soon" in tr.Footer).toBe(false);
    expect("soon" in en.Footer).toBe(false);
  });

  it("dört grubu sırayla sunar; Platform başlığı yoktur", async () => {
    const footer = await renderFooter();

    expect(
      within(footer).getAllByRole("heading").map((heading) => heading.textContent)
    ).toEqual(["Keşfet", "Topluluk", "LTZ Performance", "Destek"]);
    expect(within(footer).queryByRole("heading", { name: "Platform" })).toBeNull();
  });

  it("her grupta üç öğe adı gösterir", async () => {
    const footer = await renderFooter();
    const names = (group: string) =>
      within(within(footer).getByRole("navigation", { name: group }))
        .getAllByRole("listitem")
        .map((item) => item.textContent);

    expect(names("Keşfet")).toEqual(["Oyunlar", "Haberler", "İncelemeler"]);
    expect(names("Topluluk")).toEqual(["Topluluk", "Takım Bul", "Etkinlikler"]);
    expect(names("LTZ Performance")).toEqual(["Donanımlar", "Performans", "Sana Özel"]);
    expect(names("Destek")).toEqual(["Hakkımızda", "SSS", "İletişim"]);
  });

  it("henüz yayınlanmamış öğeler bağlantı değil düz metindir, sahte rota üretilmez", async () => {
    const footer = await renderFooter();

    for (const group of ["Keşfet", "Topluluk", "LTZ Performance"]) {
      const nav = within(footer).getByRole("navigation", { name: group });
      expect(within(nav).queryAllByRole("link")).toHaveLength(0);
    }
    for (const name of ["Oyunlar", "Takım Bul", "Donanımlar", "Sana Özel"]) {
      expect(within(footer).queryByRole("link", { name })).toBeNull();
      expect(within(footer).getByText(name).closest("a")).toBeNull();
    }
  });

  it("gerçek destek sayfalarına bağlanır; Ana Sayfa ve Ayarlar tekrarlanmaz", async () => {
    const footer = await renderFooter();

    const support = within(footer).getByRole("navigation", { name: "Destek" });
    expect(
      within(support).getAllByRole("link").map((link) => link.getAttribute("href"))
    ).toEqual(["/about", "/faq", "/contact"]);
    expect(within(footer).queryByRole("link", { name: "Ana Sayfa" })).toBeNull();
    expect(within(footer).queryByRole("link", { name: "Ayarlar" })).toBeNull();
  });

  it("içerik 1440px ile sınırlı ve ortalanmış, yüksekliği sabit değildir", async () => {
    const footer = await renderFooter();

    const container = footer.firstElementChild as HTMLElement;
    expect(container.className).toContain("max-w-[90rem]");
    expect(container.className).toContain("mx-auto");
    expect(footer.className).not.toMatch(/(^|\s)(h-|min-h-|max-h-)/);
  });

  it("İngilizce grup başlıklarını sunar", async () => {
    const footer = await renderFooter(en);

    expect(
      within(footer).getAllByRole("heading").map((heading) => heading.textContent)
    ).toEqual(["Discover", "Community", "LTZ Performance", "Support"]);
  });

  it("logo, açıklama ve sosyal ikonlar tek dikey sırada yer alır", async () => {
    const footer = await renderFooter();

    const logo = within(footer).getByRole("img", { name: "LobbyTwoZero" });
    const social = within(footer).getByRole("list", { name: "Sosyal medya" });
    const tagline = within(footer).getByText(/Oyunları keşfet/);
    expect(logo.compareDocumentPosition(tagline) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(tagline.compareDocumentPosition(social) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(logo.parentElement).toBe(tagline.parentElement);
  });

  it("telif ve dört yasal bağlantıyı gösterir", async () => {
    const footer = await renderFooter();

    expect(footer).toHaveTextContent(
      `© ${new Date().getFullYear()} LobbyTwoZero. Tüm hakları saklıdır.`
    );
    const legal = within(footer).getByRole("navigation", { name: "Yasal bağlantılar" });
    expect(
      within(legal).getAllByRole("link").map((link) => link.getAttribute("href"))
    ).toEqual(["/legal/kvkk", "/legal/privacy", "/legal/terms", "/legal/cookies"]);
  });

  it("gerçek logoyu kullanır ve yanına ikinci bir başlık eklemez", async () => {
    const footer = await renderFooter();

    const logo = within(footer).getByRole("img", { name: "LobbyTwoZero" });
    expect(logo.getAttribute("src")).toContain("ltz-wordmark");
    expect(within(footer).queryByRole("heading", { name: "LobbyTwoZero" })).toBeNull();
  });

  it("alt kenarda tam genişlikte statik kırmızı vurgu çizgisi taşır", async () => {
    const footer = await renderFooter();

    const accent = footer.querySelector("[data-footer-accent]") as HTMLElement;
    expect(accent).not.toBeNull();
    expect(accent).toHaveAttribute("aria-hidden", "true");
    expect(accent.className).toContain("inset-x-0");
    expect(accent.className).toContain("bottom-0");
    expect(accent.className).toContain("h-0.5");
    expect(accent.className).toContain("from-primary");
    // Statik: animasyon veya glow yok.
    expect(accent.className).not.toMatch(/animate|shadow|blur/);
    expect(footer.className).toContain("relative");
  });

  it("sosyal ikonlar bağlantı yokken uydurma href üretmez ve erişilebilir ad taşır", async () => {
    const footer = await renderFooter();

    const social = within(footer).getByRole("list", { name: "Sosyal medya" });
    expect(social.querySelectorAll("a")).toHaveLength(0);
    expect(within(social).getAllByRole("img")).toHaveLength(4);
    expect(within(social).getByRole("img", { name: "X (bağlantı yakında)" })).toBeInTheDocument();
  });
});
