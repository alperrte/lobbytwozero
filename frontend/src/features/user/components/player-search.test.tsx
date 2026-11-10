import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";

const session = { status: "authenticated" as "authenticated" | "guest" };
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: session.status }) }));

const replace = vi.fn();
const query = { q: "" };
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/oyuncular",
  useRouter: () => ({ replace, push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(query.q ? { q: query.q } : {}),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const searchPlayers = vi.fn();
vi.mock("../services/players-api", async () => ({
  ...(await vi.importActual<object>("../services/players-api")),
  searchPlayers: (...args: unknown[]) => searchPlayers(...args)
}));

import { PlayerSearch } from "./player-search";

function renderIt() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <PlayerSearch />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  session.status = "authenticated";
  query.q = "";
  replace.mockReset();
  searchPlayers.mockReset();
});
afterEach(cleanup);

describe("PlayerSearch", () => {
  it("oturum doğrulanmadan ya da sorgu yokken istek atmaz", () => {
    renderIt();
    expect(searchPlayers).not.toHaveBeenCalled();
    cleanup();
    session.status = "guest";
    query.q = "ali";
    renderIt();
    expect(searchPlayers).not.toHaveBeenCalled();
  });

  it("çok kısa metni göndermez ve uyarır; geçerli metni adrese yazar", () => {
    renderIt();
    const input = screen.getByRole("searchbox");
    fireEvent.change(input, { target: { value: "a" } });
    fireEvent.submit(screen.getByRole("search"));
    expect(replace).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toBeInTheDocument();
    fireEvent.change(input, { target: { value: "  ali  " } });
    fireEvent.submit(screen.getByRole("search"));
    expect(replace).toHaveBeenCalledWith("/tr/oyuncular?q=ali");
  });

  it("adresteki sorguyla arar ve sonuçları oyuncu profiline bağlar", async () => {
    query.q = "ali";
    searchPlayers.mockResolvedValue([
      { userId: "0b5d6f0e-1c2d-4e3f-8a9b-0c1d2e3f4a5b", username: "ali", displayName: "Ali Veli", avatar: null }
    ]);
    renderIt();
    const link = await screen.findByRole("link", { name: /Ali Veli/ });
    expect(link.getAttribute("href")).toContain("0b5d6f0e-1c2d-4e3f-8a9b-0c1d2e3f4a5b");
    await waitFor(() => expect(searchPlayers).toHaveBeenCalledWith("ali", expect.anything()));
  });

  it("sonuç yoksa boş durumu gösterir", async () => {
    query.q = "yok";
    searchPlayers.mockResolvedValue([]);
    renderIt();
    expect(await screen.findByText(tr.Players.search.empty)).toBeInTheDocument();
  });
});
