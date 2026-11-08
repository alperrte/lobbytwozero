import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { resetSocialMock } from "../services/social-mock-store";
import { FriendsView } from "./friends-view";
import { RequestsView } from "./requests-view";

// Herkes çevrimdışı ve en son bir oyun oynamış: "oynuyor" yazılmamalı, "En son oynadığı" yazılmalı.
vi.mock("../services/social-preview", () => ({
  previewMutualFriends: () => 0,
  previewPresence: () => ({
    status: "offline",
    game: { gameId: "preview-iron-tide", title: "Iron Tide", activity: "coop", visual: { tone: "primary", glyph: "bolt" } },
    lastSeenAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
  })
}));
vi.mock("../services/social-api", async () => ({
  socialApi: (await import("../services/social-mock-store")).socialMockApi
}));
vi.mock("../services/user-profiles", async () => {
  const { mockProfileCards } = await import("../services/social-mock-data");
  return { fetchProfileCards: async (ids: readonly string[]) => mockProfileCards(ids) };
});
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/arkadaslar",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

function renderView(view: ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul" now={new Date()}>
      {view}
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  resetSocialMock({ latencyMs: 0 });
});

describe("Çevrimdışı kişinin oyunu", () => {
  it("arkadaş kartında etkinlik yerine 'En son oynadığı' yazar", async () => {
    renderView(<FriendsView />);
    const card = (await screen.findByRole("heading", { name: "Deniz", level: 3 })).closest("article")!;
    expect(within(card).getByText("Çevrimdışı")).toBeInTheDocument();
    expect(within(card).getByText("Iron Tide")).toBeInTheDocument();
    expect(within(card).getByText("En son oynadığı")).toBeInTheDocument();
    expect(within(card).queryByText("Arkadaşıyla oynuyor")).not.toBeInTheDocument();
  });

  it("istek satırında 'Bu oyunu oynuyor' yerine 'En son oynadığı' yazar", async () => {
    renderView(<RequestsView />);
    await screen.findByRole("button", { name: "Nova isteğini kabul et" });
    expect(screen.getAllByText("En son oynadığı").length).toBeGreaterThan(0);
    expect(screen.queryByText("Bu oyunu oynuyor")).not.toBeInTheDocument();
  });
});
