import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { LanguageMenu } from "./language-menu";

const route = vi.hoisted(() => ({ pathname: "/library", params: {} as Record<string, unknown> }));
vi.mock("next/navigation", async (importOriginal) => ({
  ...await importOriginal<typeof import("next/navigation")>(),
  useParams: () => route.params
}));
vi.mock("@/shared/lib/i18n/navigation", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/shared/lib/i18n/navigation")>(),
  usePathname: () => route.pathname
}));

const assign = vi.fn();
const id = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const dynamicRoutes = [
  ["/library/users/[userId]", "userId", "/kutuphane/oyuncu", "/library/users"],
  ["/games/[gameId]", "gameId", "/oyunlar", "/games"],
  ["/hardware/[productId]", "productId", "/donanimlar", "/hardware"],
  ["/community/posts/[postId]", "postId", "/topluluk/gonderi", "/community/posts"],
  ["/community/forums/[forumId]", "forumId", "/topluluk/forumlar", "/community/forums"],
  ["/community/users/[userId]", "userId", "/topluluk/oyuncu", "/community/users"],
  ["/players/[userId]", "userId", "/oyuncular", "/players"]
] as const;

beforeEach(() => {
  assign.mockReset();
  route.params = {};
  const browserWindow = window;
  vi.stubGlobal("window", new Proxy(browserWindow, {
    get: (target, key) => key === "location"
      ? { search: "?favorite=false&sort=TITLE&direction=ASC", hash: "#collection", assign }
      : Reflect.get(target, key)
  }));
});
afterEach(() => vi.unstubAllGlobals());

function chooseOtherLocale(locale: "tr" | "en") {
  render(<NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>
    <LanguageMenu />
  </NextIntlClientProvider>);
  fireEvent.click(screen.getByRole("button"));
  fireEvent.click(screen.getByRole("menuitemradio", { name: locale === "tr" ? "English" : "Türkçe" }));
}

describe("LanguageMenu rota eşdeğerliği", () => {
  for (const locale of ["tr", "en"] as const) {
    it.each(dynamicRoutes)(`${locale} dinamik rota parametresini, query ve hash'i korur: %s`, (pathname, name, turkish, english) => {
      route.pathname = pathname;
      route.params = { [name]: id };
      chooseOtherLocale(locale);
      const target = locale === "tr" ? `/en${english}` : `/tr${turkish}`;
      expect(assign).toHaveBeenCalledExactlyOnceWith(`${target}/${id}?favorite=false&sort=TITLE&direction=ASC#collection`);
    });

    it.each([
      ["/library", "/kutuphane"],
      ["/library/favorites", "/kutuphane/favoriler"],
      ["/library/completed", "/kutuphane/bitirilenler"],
      ["/library/add", "/kutuphane/oyun-ekle"],
      ["/library/privacy", "/kutuphane/gizlilik"]
    ])(`${locale} statik Library rotasını korur: %s`, (pathname, turkish) => {
      route.pathname = pathname;
      chooseOtherLocale(locale);
      expect(assign).toHaveBeenCalledExactlyOnceWith(`${locale === "tr" ? `/en${pathname}` : `/tr${turkish}`}?favorite=false&sort=TITLE&direction=ASC#collection`);
    });
  }

  for (const [pathname, name] of dynamicRoutes) {
    it.each([undefined, null, [], [id], "", "   "])(`${pathname} eksik/geçersiz parametreyle yönlendirmez: %j`, (value) => {
      route.pathname = pathname;
      route.params = { [name]: value };
      chooseOtherLocale("tr");
      expect(assign).not.toHaveBeenCalled();
    });
  }
});
