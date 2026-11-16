import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import {
  ShellSessionContext,
  type ShellSession
} from "@/shared/providers/shell-session-context";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ProfileMenu } from "./profile-menu";

const replace = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  // next-intl/navigation bu dışa aktarımlara ihtiyaç duyar; test yalnız bağlantıyı gözler.
  usePathname: () => "/tr",
  useRouter: () => ({ replace, push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

function renderProfile(user: Parameters<typeof ProfileMenu>[0]["user"]) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <ProfileMenu user={user} />
    </NextIntlClientProvider>
  );
}

describe("ProfileMenu tetikleyicisi", () => {
  it("misafirde 'Misafir' adını ve kullanıcı simgesini gösterir", () => {
    const { container } = renderProfile(null);
    expect(screen.getByRole("button", { name: /Misafir/ })).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("giriş yapmış kullanıcının fotoğrafını ve adını birlikte gösterir; 'Misafir' yazmaz", () => {
    const { container } = renderProfile({
      displayName: "Ada Lovelace",
      avatarUrl: "https://example.test/ada.png"
    });
    const trigger = screen.getByRole("button", { name: /Ada Lovelace/ });
    expect(trigger).toHaveTextContent("Ada Lovelace");
    expect(trigger).not.toHaveTextContent("Misafir");
    const photo = container.querySelector("img");
    expect(photo).toHaveAttribute("src", "https://example.test/ada.png");
    expect(photo?.className).toContain("object-cover");
  });

  it("fotoğrafı olmayan kullanıcıda ad baş harflerini gösterir", () => {
    renderProfile({ displayName: "Ada Lovelace" });
    expect(screen.getByRole("button", { name: /Ada Lovelace/ })).toHaveTextContent("AL");
  });
});

function renderWithSession(session: ShellSession) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <ShellSessionContext.Provider value={session}>
        <ProfileMenu user={session.user} />
      </ShellSessionContext.Provider>
    </NextIntlClientProvider>
  );
}

const openMenu = (name: RegExp) => fireEvent.click(screen.getByRole("button", { name }));

describe("ProfileMenu oturum katmanı", () => {
  it("katman bağlı değilken çıkış satırı eski, eylemsiz 'Yakında' halindedir", () => {
    renderProfile(null);
    openMenu(/Misafir/);
    expect(screen.getByRole("menuitem", { name: /Çıkış yap/ })).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByRole("menuitem", { name: "Giriş yap" })).toBeNull();
  });

  it("misafire giriş sayfasına giden 'Giriş yap' bağlantısı gösterir, çıkış satırı yoktur", () => {
    renderWithSession({ user: null, logout: vi.fn() });
    openMenu(/Misafir/);

    expect(screen.getByRole("menuitem", { name: "Giriş yap" })).toHaveAttribute("href", "/tr/giris");
    expect(screen.queryByRole("menuitem", { name: /Çıkış yap/ })).toBeNull();
  });

  it("oturum yüklenirken ne giriş ne çıkış eylemi gösterir", () => {
    renderWithSession({ user: null, loading: true, logout: vi.fn() });
    openMenu(/Misafir/);

    expect(screen.queryByRole("menuitem", { name: "Giriş yap" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: /Çıkış yap/ })).toBeNull();
  });

  it("çıkış satırı hemen çıkış yapmaz; önce onay penceresi açılır ve menü kapanır", async () => {
    const logout = vi.fn().mockResolvedValue(undefined);
    renderWithSession({ user: { displayName: "Hesabım", hideInitials: true }, logout });
    openMenu(/Hesabım/);

    const item = screen.getByRole("menuitem", { name: /Çıkış yap/ });
    expect(item).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.getAllByText("Yakında").length).toBeGreaterThan(0); // profil/hesap satırları hâlâ "Yakında"
    fireEvent.click(item);

    expect(screen.getByText("Hesabınızdan çıkış yapıyorsunuz. Emin misiniz?")).toBeInTheDocument();
    expect(screen.queryByRole("menu")).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it("onaylayınca oturumu kapatır ve giriş ekranına yönlendirir", async () => {
    replace.mockClear();
    const logout = vi.fn().mockResolvedValue(undefined);
    renderWithSession({ user: { displayName: "Hesabım", hideInitials: true }, logout });
    openMenu(/Hesabım/);
    fireEvent.click(screen.getByRole("menuitem", { name: /Çıkış yap/ }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, çıkış yap" }));

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/tr/giris"));
    expect(screen.queryByText("Hesabınızdan çıkış yapıyorsunuz. Emin misiniz?")).toBeNull();
  });

  it("vazgeçilirse çıkış yapılmaz ve yönlendirme olmaz", () => {
    replace.mockClear();
    const logout = vi.fn().mockResolvedValue(undefined);
    renderWithSession({ user: { displayName: "Hesabım", hideInitials: true }, logout });
    openMenu(/Hesabım/);
    fireEvent.click(screen.getByRole("menuitem", { name: /Çıkış yap/ }));
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));

    expect(logout).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
    expect(screen.queryByText("Hesabınızdan çıkış yapıyorsunuz. Emin misiniz?")).toBeNull();
  });

  it("sunucu çıkışı reddederse giriş ekranına yönlendirilmez", async () => {
    replace.mockClear();
    const logout = vi.fn().mockRejectedValue(new Error("ağ"));
    renderWithSession({ user: { displayName: "Hesabım", hideInitials: true }, logout });
    openMenu(/Hesabım/);
    fireEvent.click(screen.getByRole("menuitem", { name: /Çıkış yap/ }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, çıkış yap" }));

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText("Hesabınızdan çıkış yapıyorsunuz. Emin misiniz?")).toBeNull());
    expect(replace).not.toHaveBeenCalled();
  });

  it("hideInitials açıkken ad baş harfi gösterilmez (kullanıcı simgesi kalır)", () => {
    renderWithSession({ user: { displayName: "Hesabım", hideInitials: true }, logout: vi.fn() });
    const trigger = screen.getByRole("button", { name: /Hesabım/ });
    expect(trigger.querySelector('span[aria-hidden="true"]')).toBeNull(); // baş harf rozeti yok
    expect(trigger.querySelector("svg")).not.toBeNull(); // kullanıcı simgesi var
  });
});
