import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { AuthNavbar } from "./auth-navbar";

let currentPath = "/login";

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    usePathname: () => currentPath,
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});
vi.mock("@/shared/components/layout/language-menu", () => ({ LanguageMenu: () => <button type="button">TR</button> }));
vi.mock("@/shared/components/ui/theme-switch", () => ({ ThemeSwitch: () => <button type="button">tema</button> }));

function renderInIntl(node: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      {node}
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  currentPath = "/login";
});

describe("AuthNavbar", () => {
  it("yüzen çubuk: marka, herkese açık bağlantılar, dil, tema ve giriş/kayıt gezinmesi", () => {
    renderInIntl(<AuthNavbar />);
    const bar = screen.getByRole("banner", { name: "Üst çubuk" });
    expect(within(bar).getByRole("link", { name: "LTZ ana sayfa" })).toHaveAttribute("href", "/");
    const nav = within(bar).getByRole("navigation", { name: "Gezinme" });
    expect(within(nav).getByRole("link", { name: "Ana Sayfa" })).toHaveAttribute("href", "/");
    expect(within(nav).getByRole("link", { name: "Oyunlar" })).toHaveAttribute("href", "/games");
    expect(within(nav).getByRole("link", { name: "Hakkımızda" })).toHaveAttribute("href", "/about");
    expect(within(nav).getByRole("link", { name: "SSS" })).toHaveAttribute("href", "/faq");
    expect(within(bar).getByRole("button", { name: "TR" })).toBeInTheDocument();
    expect(within(bar).getByRole("button", { name: "tema" })).toBeInTheDocument();
    expect(within(bar).getByRole("link", { name: "Giriş yap" })).toHaveAttribute("href", "/login");
    expect(within(bar).getByRole("link", { name: "Kayıt ol" })).toHaveAttribute("href", "/register");
  });

  it("logo küçültülmüş boyuttadır (w-[4.5rem], geniş ekranda w-[5.25rem])", () => {
    renderInIntl(<AuthNavbar />);
    const logo = screen.getByRole("link", { name: "LTZ ana sayfa" }).querySelector("img")!;
    expect(logo.className).toContain("w-[4.5rem]");
    expect(logo.className).toContain("sm:w-[5.25rem]");
  });

  it("geçerli ekranın bağlantısı aria-current alır ve mobilde gizlenir", () => {
    currentPath = "/register";
    renderInIntl(<AuthNavbar />);
    const register = screen.getByRole("link", { name: "Kayıt ol" });
    expect(register).toHaveAttribute("aria-current", "page");
    expect(register.className).toContain("hidden");
    expect(screen.getByRole("link", { name: "Giriş yap" })).not.toHaveAttribute("aria-current");
  });
});
