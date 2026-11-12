import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { AppShell } from "./app-shell";

const location = vi.hoisted(() => ({ pathname: "/tr" }));

vi.mock("next/navigation", () => ({
  usePathname: () => location.pathname,
  useParams: () => ({}),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

function renderShell(locale: "tr" | "en" = "tr") {
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <AppShell footer={<footer>alt bilgi</footer>}>
        <h1>İçerik</h1>
      </AppShell>
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  location.pathname = "/tr";
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    }))
  );
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
    cb(0);
    return 0;
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.removeAttribute("style");
});

describe("AppShell", () => {
  it("landmark'ları, skip link'i ve gezinmeyi sunar", () => {
    renderShell();

    expect(
      screen.getByRole("link", { name: "Ana içeriğe geç" })
    ).toHaveAttribute("href", "#main-content");
    expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");
    expect(screen.getByRole("banner")).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
    expect(
      screen.getByRole("navigation", { name: "Ana gezinme" })
    ).toBeInTheDocument();
  });

  it("footer sidebar ve içeriğin altında, bağımsız tam genişlikli satırdır", () => {
    renderShell();
    const footer = screen.getByRole("contentinfo");
    const main = screen.getByRole("main");
    const sidebar = document.getElementById("app-sidebar") as HTMLElement;

    const topRow = sidebar.parentElement as HTMLElement;
    const root = topRow.parentElement as HTMLElement;
    // Sidebar ve içerik aynı üst satırdadır; footer o satırın dışında, kök sütunun son çocuğudur.
    expect(topRow.contains(main)).toBe(true);
    expect(topRow.contains(footer)).toBe(false);
    expect(root.lastElementChild?.contains(footer)).toBe(true);
    expect(root.lastElementChild).not.toBe(topRow);
    expect(sidebar.contains(footer)).toBe(false);
  });

  it("uygulama kabuğu en az viewport yüksekliğindedir ama sabit yükseklik yoktur; footer onun dışındadır", () => {
    renderShell();
    const sidebar = document.getElementById("app-sidebar") as HTMLElement;
    const topRow = sidebar.parentElement as HTMLElement;
    const root = topRow.parentElement as HTMLElement;

    // Bölge A: min-h-dvh (içerik uzayınca büyür); sabit h-dvh yok. Kök sarmalayıcıda yükseklik zorlaması yok.
    expect(topRow.className).toContain("min-h-dvh");
    expect(topRow.className).not.toMatch(/(^|\s)h-(dvh|screen)/);
    expect(root.className).not.toMatch(/min-h-(dvh|screen)|h-(dvh|screen)/);
    // Sidebar paneli sticky ve viewport yüksekliğindedir; footer'ın altına geçmez (aside satır boyunca esner).
    const rail = sidebar.querySelector("[data-rail]") as HTMLElement;
    expect(rail.className).toContain("lg:sticky");
    expect(rail.className).toContain("lg:h-[calc(100dvh-1.5rem)]");
    expect(sidebar.className).not.toContain("lg:h-dvh");
  });

  it("navbar genişliği footer'dan bağımsızdır: üst sınır 60rem ve içerik sütununa göre ortalanır", () => {
    renderShell();
    const header = screen.getByRole("banner");
    const column = header.parentElement?.parentElement as HTMLElement;

    expect(header.className).toContain("max-w-[60rem]");
    expect(header.className).toContain("w-full");
    expect(column.contains(screen.getByRole("contentinfo"))).toBe(false);
  });

  it("yalnız gerçek rotayı bağlantı yapar; hazır olmayanlar 'Yakında' ile işaretlenir", () => {
    renderShell();
    const nav = screen.getByRole("navigation", { name: "Ana gezinme" });

    const links = nav.querySelectorAll("a");
    expect(links).toHaveLength(8);
    expect(links[1]).toHaveAttribute("href", "/tr/oyunlar");
    expect(within(nav).getByRole("link", { name: "İncelemeler" })).toHaveAttribute("href", "/tr/incelemeler");
    expect(within(nav).queryByRole("button", { name: "İncelemeler — Yakında" })).not.toBeInTheDocument();
    expect(nav.querySelector('a[href="/tr/arkadaslar"]')).not.toBeNull();
    expect(within(nav).getByRole("link", { name: "Kütüphanem" })).toHaveAttribute("href", "/tr/kutuphane");
    expect(within(nav).queryByRole("button", { name: "Kütüphanem — Yakında" })).not.toBeInTheDocument();
    expect(links[0]).toHaveAttribute("href", "/tr");
    expect(links[0]).toHaveAttribute("aria-current", "page");
    expect(nav.querySelectorAll('a[href="#"]')).toHaveLength(0);
    expect(nav).toHaveTextContent("Ana Sayfa");
    expect(nav).toHaveTextContent("Donanımlar");
    // Profil, Bilgisayarlarım ve Kampanyalar kenar çubuğunda yer almaz.
    expect(nav).not.toHaveTextContent("Profil");
    expect(nav).not.toHaveTextContent("Bilgisayarlarım");
    expect(nav).toHaveTextContent("Kampanyalar");
    // Yalnız hazır olmayan altı öğe Yakında rozeti taşır; Library ve İncelemeler gerçek bağlantıdır.
    expect(screen.getAllByText("Yakında")).toHaveLength(6);
  });

  it("14 öğeyi üç grupta, belirlenen sırayla sunar", () => {
    renderShell();
    const nav = screen.getByRole("navigation", { name: "Ana gezinme" });

    const groups = within(nav).getAllByRole("list");
    expect(groups).toHaveLength(3);
    expect(
      groups.map((list) => within(list).getAllByRole("listitem").length)
    ).toEqual([6, 5, 3]);
    expect(nav).toHaveTextContent(/Genel/i);
    expect(nav).toHaveTextContent(/Sosyal/i);
    expect(nav).toHaveTextContent(/Donanım & Performans/i);

    const labels = within(nav)
      .getAllByRole("listitem")
      .map((item) => item.querySelector("span.truncate")?.textContent);
    expect(labels).toEqual([
      "Ana Sayfa",
      "Oyunlar",
      "Kütüphanem",
      "İncelemeler",
      "Haberler",
      "Sana Özel",
      "Topluluk",
      "Arkadaşlar",
      "Mesajlar",
      "Takım Bul",
      "Etkinlikler",
      "Donanımlar",
      "Kampanyalar",
      "Performans"
    ]);
  });

  it.each([
    ["tr", "/tr/kutuphane", "/tr/kutuphane", "Kütüphanem"],
    ["tr", "/tr/kutuphane/favoriler", "/tr/kutuphane", "Kütüphanem"],
    ["en", "/en/library", "/en/library", "My Library"],
    ["en", "/en/library/completed", "/en/library", "My Library"]
  ] as const)("Library bağlantısı ve aktif durum: %s %s", (locale, pathname, href, name) => {
    location.pathname = pathname;
    renderShell(locale);
    const nav = screen.getByRole("navigation", { name: locale === "tr" ? "Ana gezinme" : "Main navigation" });
    const library = within(nav).getByRole("link", { name });
    expect(library).toHaveAttribute("href", href);
    expect(library).toHaveAttribute("aria-current", "page");
    expect(library).not.toHaveTextContent(locale === "tr" ? "Yakında" : "Soon");
  });

  it("'Yakında' öğeleri tıklanamaz: bağlantı değil, aria-disabled düğmedir", () => {
    renderShell();
    const news = screen.getByRole("button", { name: "Haberler — Yakında" });
    expect(news).toHaveAttribute("aria-disabled", "true");
    expect(news.closest("a")).toBeNull();
    // Library ve İncelemeler dahil sekiz öğe kullanıma açıktır; diğer bekleyen öğeler pasiftir.
    expect(
      screen.getAllByRole("button", { name: /— Yakında$/ })
    ).toHaveLength(6);
  });

  it("'Yakında' öğesine odaklanınca tooltip çıkar, Escape ile kapanır", () => {
    renderShell();
    const news = screen.getByRole("button", { name: "Haberler — Yakında" });
    fireEvent.focus(news);
    const tooltip = document.body.querySelector("span[aria-hidden='true'].fixed");
    expect(tooltip).toHaveTextContent("Haberler — Yakında");

    act(() => {
      fireEvent.keyDown(document, { key: "Escape" });
    });
    expect(document.body.querySelector("span[aria-hidden='true'].fixed")).toBeNull();
  });

  it("daralt düğmesi durumu ve adını günceller, tercihi html'e yazar", () => {
    renderShell();
    const collapse = screen.getByRole("button", { name: "Kenar çubuğunu daralt" });
    expect(collapse).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(collapse);
    const expand = screen.getByRole("button", { name: "Kenar çubuğunu genişlet" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    expect(document.documentElement.dataset.sidebar).toBe("collapsed");

    fireEvent.click(expand);
    expect(document.documentElement.dataset.sidebar).toBeUndefined();
  });

  it("logo alanında marka görselinden başka yazı yoktur", () => {
    renderShell();
    const brand = screen.getByRole("link", { name: "LobbyTwoZero" });
    expect(brand).toHaveAttribute("href", "/tr");
    expect(brand.textContent).toBe("");
    expect(brand.querySelectorAll("img")).toHaveLength(2);
  });

  it("menü düğmesi çekmeceyi açar, Escape kapatır ve odağı geri verir", () => {
    renderShell();
    const opener = screen.getByRole("button", { name: "Menüyü aç" });
    expect(opener).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(opener);
    expect(opener).toHaveAttribute("aria-expanded", "true");
    expect(document.documentElement.style.overflow).toBe("hidden");

    act(() => {
      fireEvent.keyDown(document, { key: "Escape" });
    });
    expect(opener).toHaveAttribute("aria-expanded", "false");
    expect(document.documentElement.style.overflow).toBe("");
    expect(opener).toHaveFocus();
  });

  it("açıkken sayfa içeriği etkileşime kapatılır (inert)", () => {
    renderShell();
    fireEvent.click(screen.getByRole("button", { name: "Menüyü aç" }));
    expect(screen.getByRole("main").parentElement).toHaveAttribute("inert");
  });

  it("tema ve dil kontrolleri erişilebilir adlara sahiptir", () => {
    renderShell();
    // Tek düğmeli tema kontrolü: adı, tıklayınca geçilecek temayı söyler (varsayılan Dark → Açık).
    expect(screen.getByRole("button", { name: "Açık tema" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Koyu tema" })).not.toBeInTheDocument();
    const language = screen.getByRole("button", { name: "Dil: Türkçe" });
    expect(language).toHaveAttribute("aria-haspopup", "menu");
    expect(language).toHaveAttribute("aria-expanded", "false");
  });

  it("dil menüsü açılır, iki dili sunar ve Escape ile kapanır", () => {
    renderShell();
    const trigger = screen.getByRole("button", { name: "Dil: Türkçe" });
    fireEvent.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("menuitemradio", { name: "Türkçe" })).toHaveAttribute(
      "aria-checked",
      "true"
    );
    expect(screen.getByRole("menuitemradio", { name: "English" })).toHaveAttribute(
      "aria-checked",
      "false"
    );

    act(() => {
      fireEvent.keyDown(document, { key: "Escape" });
    });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("bildirim paneli sahte veri göstermez; dürüst boş durum sunar", () => {
    renderShell();
    const bell = screen.getByRole("button", { name: "Bildirimler" });
    fireEvent.click(bell);
    expect(screen.getByText("Henüz bildirim yok")).toBeInTheDocument();
    const panel = screen.getByRole("region", { name: "Bildirimler" });
    expect(panel.querySelectorAll("li")).toHaveLength(0);
  });

  it("profil menüsü misafir durumunu dürüstçe gösterir; Çıkış yap kırmızı vurgulu ama henüz etkin değildir", () => {
    renderShell();
    fireEvent.click(screen.getByRole("button", { name: /Misafir/ }));
    const menu = screen.getByRole("menu");
    expect(menu).toHaveTextContent("Giriş yapılmadı");
    expect(within(menu).getByRole("menuitem", { name: "Bilgisayarlarım" })).toHaveAttribute("href", "/tr/profil/bilgisayarlarim");
    expect(menu).toHaveTextContent("Profilim");
    expect(menu).toHaveTextContent("Hesap ayarları");
    const logout = screen.getByRole("menuitem", { name: /Çıkış yap/ });
    expect(logout).toHaveAttribute("aria-disabled", "true");
    expect(logout.className).toContain("text-brand-text");
    // Oturum olmadığı için tıklamak hiçbir şey yapmaz (sahte çıkış akışı yok).
    fireEvent.click(logout);
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("Ayarlar, kenar çubuğunda diyalog değil gerçek /tr/ayarlar sayfasına giden bağlantıdır", () => {
    renderShell();
    const link = screen.getByRole("link", { name: "Ayarlar" });
    expect(link).toHaveAttribute("href", "/tr/ayarlar");
    expect(screen.queryByRole("dialog", { hidden: true })).not.toBeInTheDocument();
  });

  it("navbar sayfa adını tekrar etmez; yalnız arama, bildirim, dil, tema ve profil vardır", () => {
    renderShell();
    const banner = screen.getByRole("banner");
    expect(banner).not.toHaveTextContent("Ana Sayfa");
    // Ctrl K ipucu yalnız görsel göstergedir; erişilebilirlik ağacında aria-keyshortcuts taşır.
    expect(banner.querySelector("kbd")).toHaveTextContent("Ctrl K");
    expect(banner.querySelector("kbd")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByRole("combobox", { name: "Sayfalarda ara" })).toHaveAttribute(
      "placeholder",
      "Sayfalarda ara… (@ ile oyuncu)"
    );
    expect(screen.queryByRole("navigation", { name: "Sayfa konumu" })).not.toBeInTheDocument();
  });

  it("arama navbar'a gömülüdür: boşken hiçbir şey listelemez, yazınca yalnız eşleşenleri gösterir", () => {
    renderShell();
    const input = screen.getByRole("combobox", { name: "Sayfalarda ara" });
    expect(screen.queryByRole("dialog", { hidden: true })).not.toBeInTheDocument();

    fireEvent.focus(input);
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    fireEvent.change(input, { target: { value: "ana" } });
    const options = screen.getAllByRole("option");
    expect(options[0]).toHaveTextContent("Anasayfa");
    expect(options.length).toBeLessThan(8);

    fireEvent.change(input, { target: { value: "zzzzzz" } });
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(screen.getByText("Sonuç bulunamadı")).toBeInTheDocument();
  });

  it("Ctrl+K arama kutusuna odaklanır ve Escape sorguyu temizler", () => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn().mockImplementation(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn()
      }))
    );
    renderShell();
    const input = screen.getByRole("combobox", { name: "Sayfalarda ara" });

    act(() => {
      fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    });
    expect(input).toHaveFocus();

    fireEvent.change(input, { target: { value: "sss" } });
    expect(input).toHaveValue("sss");
    fireEvent.keyDown(input, { key: "Escape" });
    expect(input).toHaveValue("");
  });
});
