import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { AppShell } from "./app-shell";

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ refresh: vi.fn() })
}));
vi.mock("@/shared/lib/i18n/actions", () => ({ setLocale: vi.fn() }));

function renderShell() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <AppShell footer={<footer>alt bilgi</footer>}>
        <h1>İçerik</h1>
      </AppShell>
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
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

  it("yalnız gerçek rotayı bağlantı yapar; hazır olmayanlar 'Yakında' ile işaretlenir", () => {
    renderShell();
    const nav = screen.getByRole("navigation", { name: "Ana gezinme" });

    const links = nav.querySelectorAll("a");
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("href", "/");
    expect(links[0]).toHaveAttribute("aria-current", "page");
    expect(nav.querySelectorAll('a[href="#"]')).toHaveLength(0);
    expect(nav).toHaveTextContent("Ana Sayfa");
    expect(nav).toHaveTextContent("Donanımlar");
    // Profil, Bilgisayarlarım ve Kampanyalar kenar çubuğunda yer almaz.
    expect(nav).not.toHaveTextContent("Profil");
    expect(nav).not.toHaveTextContent("Bilgisayarlarım");
    expect(nav).not.toHaveTextContent("Kampanyalar");
    // Tam eşleşme yalnız rozetleri sayar (tooltip metni "Etiket · Yakında" biçimindedir).
    expect(screen.getAllByText("Yakında")).toHaveLength(1);
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
    expect(menu.querySelectorAll("a")).toHaveLength(0);
    expect(menu).toHaveTextContent("Profilim");
    expect(menu).toHaveTextContent("Hesap ayarları");
    const logout = screen.getByRole("menuitem", { name: /Çıkış yap/ });
    expect(logout).toHaveAttribute("aria-disabled", "true");
    expect(logout.className).toContain("text-brand-text");
    // Oturum olmadığı için tıklamak hiçbir şey yapmaz (sahte çıkış akışı yok).
    fireEvent.click(logout);
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("Ayarlar, kenar çubuğunda diyalog değil gerçek /settings sayfasına giden bağlantıdır", () => {
    renderShell();
    const link = screen.getByRole("link", { name: "Ayarlar" });
    expect(link).toHaveAttribute("href", "/settings");
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
      "Sayfalarda ara..."
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
