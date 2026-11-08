import { act, render, screen } from "@testing-library/react";
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

beforeEach(() => {
  currentPath = "/login";
});

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
    const second = screen.getByLabelText("alan") as HTMLInputElement;
    expect(second).not.toBe(first);
    expect(second.value).toBe("");
  });

  it("İngilizce metinleri kullanır", () => {
    render(<Stage locale="en">{page("Sign in")}</Stage>);
    expect(screen.getByText("Welcome to the lobby")).toBeInTheDocument();
  });
});
