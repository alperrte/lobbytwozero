import { cleanup, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { PublicProfile } from "../types/players.types";

const session = { value: { status: "guest" } as unknown };
vi.mock("@/features/auth", () => ({ useSession: () => session.value, useRequireSession: () => session.value }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/oyuncular/x",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const getPublicProfile = vi.fn();
vi.mock("../services/players-api", () => ({ getPublicProfile: (...args: unknown[]) => getPublicProfile(...args) }));

import { PlayerProfile } from "./player-profile";

const ID = "0b5d6f0e-1c2d-4e3f-8a9b-0c1d2e3f4a5b";
const section = { status: "UNAVAILABLE", count: null };
function profile(patch: Partial<PublicProfile> = {}): PublicProfile {
  return {
    userId: ID,
    username: "ali",
    displayName: "Ali",
    avatar: null,
    cover: null,
    details: null,
    visible: { friendsList: false, library: false, hardware: false, performance: false },
    summary: { friends: { status: "HIDDEN", count: null }, library: section, reviews: section },
    ...patch
  } as PublicProfile;
}

function renderIt() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <PlayerProfile userKey="ali" />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  getPublicProfile.mockReset();
  session.value = { status: "guest" };
});
afterEach(cleanup);

describe("PlayerProfile", () => {
  it("misafire ayrıntılar yoksa giriş yap önerir; gizli bölümü sayı uydurmadan gösterir", async () => {
    getPublicProfile.mockResolvedValue(profile());
    renderIt();
    expect(await screen.findByText("Ali")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: tr.Players.profile.signIn })).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("Steam bağlantısını güvenli dış bağlantı olarak açar", async () => {
    getPublicProfile.mockResolvedValue(
      profile({
        details: {
          bio: "merhaba",
          country: null,
          playerTypes: [],
          favoriteGenres: [],
          favoritePlatforms: [],
          steam: { steamId: "76561198000000000", profileUrl: "https://steamcommunity.com/profiles/76561198000000000" }
        }
      })
    );
    renderIt();
    const link = await screen.findByRole("link", { name: new RegExp(tr.Players.profile.steam.open) });
    expect(link).toHaveAttribute("href", "https://steamcommunity.com/profiles/76561198000000000");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.getByText("merhaba")).toBeInTheDocument();
  });

  it("404 için bulunamadı ekranı, diğer hatada tekrar dene gösterir", async () => {
    getPublicProfile.mockRejectedValueOnce(new ApiHttpError(404, { code: "PROFILE_NOT_FOUND" }));
    renderIt();
    expect(await screen.findByText(tr.Players.profile.notFoundTitle)).toBeInTheDocument();
    cleanup();
    getPublicProfile.mockRejectedValueOnce(new ApiHttpError(503, {}));
    renderIt();
    expect(await screen.findByText(tr.Players.profile.errorTitle)).toBeInTheDocument();
  });
});
