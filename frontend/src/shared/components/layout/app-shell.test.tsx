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
    expect(screen.getByRole("group", { name: "Tema" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Açık tema" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Koyu tema" })).toBeInTheDocument();
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

  it("profil menüsü misafir durumunu dürüstçe gösterir ve sahte rota üretmez", () => {
    renderShell();
    fireEvent.click(screen.getByRole("button", { name: /Misafir/ }));
    const menu = screen.getByRole("menu");
    expect(menu).toHaveTextContent("Giriş yapılmadı");
    expect(menu.querySelectorAll("a")).toHaveLength(0);
    expect(menu).toHaveTextContent("Profilim");
    expect(menu).toHaveTextContent("Hesap ayarları");
    expect(menu).not.toHaveTextContent("Çıkış yap");
  });

  it("Ayarlar paneli kenar çubuğundan açılır ve tercihleri sunar", () => {
    renderShell();
    fireEvent.click(screen.getByRole("button", { name: /^Ayarlar/ }));
    const dialog = screen.getByRole("dialog", { name: "Ayarlar", hidden: true });
    expect(dialog).toHaveAttribute("open");
    for (const legend of ["Görünüm", "Üst çubuk", "Kenar çubuğu", "Dil"]) {
      expect(dialog).toHaveTextContent(legend);
    }
    expect(dialog).not.toHaveTextContent("Güvenlik");
  });

  it("arama iletişim kutusu Ctrl+K ile açılır ve yalnız mevcut sayfaları arar", () => {
    renderShell();
    act(() => {
      fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    });
    const dialog = screen.getByRole("dialog", { name: "Genel arama", hidden: true });
    expect(dialog).toHaveAttribute("open");
    const input = screen.getByRole("combobox");
    fireEvent.change(input, { target: { value: "sss" } });
    expect(dialog).toHaveTextContent("SSS");
    fireEvent.change(input, { target: { value: "zzzzzz" } });
    expect(dialog).toHaveTextContent("Sonuç bulunamadı");
  });
});
