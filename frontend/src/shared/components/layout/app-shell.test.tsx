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
    expect(nav).toHaveTextContent("Bilgisayarlarım");
    expect(nav).toHaveTextContent("Donanımlar");
    expect(nav).toHaveTextContent("Kampanyalar");
    // Tam eşleşme yalnız rozetleri sayar (tooltip metni "Etiket · Yakında" biçimindedir).
    expect(screen.getAllByText("Yakında")).toHaveLength(3);
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
    expect(screen.getByRole("group", { name: "Dil" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Türkçe (TR)" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "İngilizce (EN)" })
    ).toBeInTheDocument();
  });
});
