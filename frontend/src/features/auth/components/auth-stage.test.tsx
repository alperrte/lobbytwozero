import { act, render, screen } from "@testing-library/react";
import { afterEach } from "vitest";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { AuthStage } from "./auth-stage";

let currentPath = "/login";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  usePathname: () => currentPath
}));
vi.mock("./auth-navbar", () => ({ AuthNavbar: () => <header data-testid="navbar" /> }));
vi.mock("./auth-footer", () => ({ AuthFooter: () => <footer data-testid="footer" /> }));
vi.mock("./auth-card-snake", () => ({ AuthCardSnake: () => <svg data-testid="snake" /> }));

function Stage({ locale = "tr", children }: { locale?: "tr" | "en"; children: React.ReactNode }) {
  return (
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <AuthStage>{children}</AuthStage>
    </NextIntlClientProvider>
  );
}

function page(title: string) {
  return (
    <>
      <h1>{title}</h1>
      <input aria-label="alan" />
    </>
  );
}

const card = () => screen.getByRole("heading", { level: 1 }).parentElement as HTMLElement;

function mockReducedMotion(reduced: boolean) {
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({ matches: reduced && query.includes("reduce"), media: query, addEventListener: vi.fn(), removeEventListener: vi.fn() }) as unknown as MediaQueryList
  );
}

beforeEach(() => {
  currentPath = "/login";
  mockReducedMotion(false);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const leavingEntries = (container: HTMLElement) => container.querySelectorAll<HTMLElement>('[data-auth-entry="leaving"]');

describe("AuthStage", () => {
  it("navbar, footer, yılan ve kart içeriğini tek sahnede çizer; tek ana alan vardır", () => {
    render(<Stage>{page("Giriş yap")}</Stage>);
    expect(screen.getByTestId("navbar")).toBeInTheDocument();
    expect(screen.getByTestId("footer")).toBeInTheDocument();
    expect(screen.getByTestId("snake")).toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Giriş yap" })).toBeInTheDocument();
  });

  it("ilk açılışta kart içeriği geçiş sınıfı almaz; karşılama başlığı ekran okuyucuya bir kez okunur", () => {
    render(<Stage>{page("Giriş yap")}</Stage>);
    expect(card().className).not.toMatch(/auth-enter-/);
    expect(screen.getAllByText("Lobine hoş geldin")).toHaveLength(1);
  });

  it("giriş → kayıt: yeni içerik sağdan gelir, kayıt metni görünür, odak yeni başlığa taşınır", () => {
    const view = render(<Stage>{page("Giriş yap")}</Stage>);
    currentPath = "/register";
    view.rerender(<Stage>{page("Kayıt ol")}</Stage>);
    expect(card().className).toContain("auth-enter-from-right");
    expect(screen.getByText("Yeni lobin seni bekliyor")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Kayıt ol" })).toHaveFocus();
  });

  it("kayıt → giriş soldan gelir", () => {
    currentPath = "/register";
    const view = render(<Stage>{page("Kayıt ol")}</Stage>);
    currentPath = "/login";
    view.rerender(<Stage>{page("Giriş yap")}</Stage>);
    expect(card().className).toContain("auth-enter-from-left");
  });

  it("giriş → şifremi unuttum: farklı (çözülme) hareketi; giriş metni korunur", () => {
    const view = render(<Stage>{page("Giriş yap")}</Stage>);
    currentPath = "/forgot-password";
    view.rerender(<Stage>{page("Şifremi unuttum")}</Stage>);
    expect(card().className).toContain("auth-enter-unfold");
    expect(card().className).not.toContain("auth-enter-from-right");
    expect(screen.getByText(/Oyun kaldığı yerden/)).toBeInTheDocument();
  });

  it("sayfa değişince form verisi bir sonraki forma taşınmaz (içerik yeniden kurulur)", () => {
    const view = render(<Stage>{page("Giriş yap")}</Stage>);
    const first = screen.getByLabelText("alan") as HTMLInputElement;
    act(() => {
      first.value = "gizli";
    });
    currentPath = "/register";
    view.rerender(<Stage>{page("Kayıt ol")}</Stage>);
    const second = view.container.querySelector<HTMLInputElement>('[data-auth-entry="current"] input')!;
    expect(second).not.toBe(first);
    expect(second.value).toBe("");
  });

  it("İngilizce metinleri kullanır", () => {
    render(<Stage locale="en">{page("Sign in")}</Stage>);
    expect(screen.getByText("Welcome to the lobby")).toBeInTheDocument();
  });

  describe("çıkış animasyonu", () => {
    function fillAndNavigate(view: ReturnType<typeof render>, to: string, nextTitle: string) {
      currentPath = to;
      view.rerender(<Stage>{page(nextTitle)}</Stage>);
    }

    it("giriş → kayıt: eski içeriğin görüntüsü sola çıkan, etkileşime kapalı katman olarak kalır", () => {
      const view = render(<Stage>{page("Giriş yap")}</Stage>);
      fillAndNavigate(view, "/register", "Kayıt ol");
      const leaving = leavingEntries(view.container);
      expect(leaving).toHaveLength(1);
      expect(leaving[0]!.className).toContain("auth-leaving--left");
      expect(leaving[0]).toHaveAttribute("aria-hidden", "true");
      expect(leaving[0]!.hasAttribute("inert")).toBe(true);
      expect(leaving[0]!.textContent).toContain("Giriş yap");
      // Ekran okuyucu/rol sorgusu yalnız yeni başlığı görür.
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.getByRole("heading", { level: 1, name: "Kayıt ol" })).toBeInTheDocument();
    });

    it("yön eşlemesi: kayıt → giriş sağa, giriş → şifremi unuttum aşağı, şifremi unuttum → giriş kapanarak çıkar", () => {
      const cases: Array<[string, string, string]> = [
        ["/register", "/login", "auth-leaving--right"],
        ["/login", "/forgot-password", "auth-leaving--down"],
        ["/forgot-password", "/login", "auth-leaving--fold"]
      ];
      for (const [from, to, expected] of cases) {
        currentPath = from;
        const view = render(<Stage>{page("A")}</Stage>);
        fillAndNavigate(view, to, "B");
        expect(leavingEntries(view.container)[0]!.className).toContain(expected);
        view.unmount();
      }
    });

    it("eski formda yazılmış metin görüntüde korunur; kimlikler silinir; parola alanı kopyalanmaz", () => {
      const view = render(
        <Stage>
          <h1>Giriş yap</h1>
          <label htmlFor="kimlik">Kimlik</label>
          <input id="kimlik" name="identifier" type="text" aria-label="alan" />
          <input name="password" type="password" aria-label="parola" />
        </Stage>
      );
      const typed = screen.getByLabelText("alan") as HTMLInputElement;
      const secret = screen.getByLabelText("parola") as HTMLInputElement;
      act(() => {
        typed.value = "ada";
        secret.value = "gizli-parola";
        typed.dispatchEvent(new Event("input", { bubbles: true }));
      });
      fillAndNavigate(view, "/register", "Kayıt ol");
      const ghost = leavingEntries(view.container)[0]!;
      const ghostInputs = ghost.querySelectorAll("input");
      expect((ghostInputs[0] as HTMLInputElement).value).toBe("ada");
      expect((ghostInputs[1] as HTMLInputElement).value).toBe("");
      expect(ghost.querySelectorAll("[id], [name], [for]")).toHaveLength(0);
      expect(ghost.closest("[inert]")).not.toBeNull();
    });

    it("süre dolunca çıkan katman DOM'dan kaldırılır", () => {
      vi.useFakeTimers();
      const view = render(<Stage>{page("Giriş yap")}</Stage>);
      fillAndNavigate(view, "/register", "Kayıt ol");
      expect(leavingEntries(view.container)).toHaveLength(1);
      act(() => vi.advanceTimersByTime(400));
      expect(leavingEntries(view.container)).toHaveLength(0);
    });

    it("hızlı gezinmede en fazla iki çıkan katman tutulur; yeni içerik her zaman tek", () => {
      const view = render(<Stage>{page("Giriş yap")}</Stage>);
      for (const [path, title] of [["/register", "Kayıt ol"], ["/forgot-password", "Şifre"], ["/login", "Giriş yap 2"]] as const) {
        fillAndNavigate(view, path, title);
      }
      expect(leavingEntries(view.container).length).toBeLessThanOrEqual(2);
      expect(view.container.querySelectorAll('[data-auth-entry="current"]')).toHaveLength(1);
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    });

    it("'hareketi azalt'ta çıkan katman hiç kurulmaz", () => {
      mockReducedMotion(true);
      const view = render(<Stage>{page("Giriş yap")}</Stage>);
      fillAndNavigate(view, "/register", "Kayıt ol");
      expect(leavingEntries(view.container)).toHaveLength(0);
    });

    it("ilk açılışta çıkan katman yoktur", () => {
      const view = render(<Stage>{page("Giriş yap")}</Stage>);
      expect(leavingEntries(view.container)).toHaveLength(0);
    });

    it("çıkan katmandaki form gönderilemez ve odaklanamaz (inert)", () => {
      const submit = vi.fn((event: React.FormEvent) => event.preventDefault());
      const view = render(
        <Stage>
          <h1>Giriş yap</h1>
          <form onSubmit={submit}>
            <input aria-label="alan" />
            <button type="submit">Gönder</button>
          </form>
        </Stage>
      );
      fillAndNavigate(view, "/register", "Kayıt ol");
      const ghostButton = leavingEntries(view.container)[0]!.querySelector("button")!;
      expect(ghostButton.closest("[inert]")).not.toBeNull();
      // Kopya React ağacının dışındadır: tıklamak canlı form işleyicisini tetikleyemez.
      ghostButton.click();
      expect(submit).not.toHaveBeenCalled();
    });
  });
});
