import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";

vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated" }) }));
const push = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/baslangic",
  useRouter: () => ({ replace: vi.fn(), push, prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const api = { getOnboarding: vi.fn(), completeOnboarding: vi.fn() };
vi.mock("../services/onboarding-api", () => ({
  getOnboarding: (...args: unknown[]) => api.getOnboarding(...args),
  completeOnboarding: (...args: unknown[]) => api.completeOnboarding(...args)
}));
vi.mock("../hooks/use-my-profile", () => ({
  useMyProfile: () => ({
    state: {
      status: "ready",
      profile: { playerTypes: [], favoriteGenres: [], favoritePlatforms: [] },
      steam: null,
      stats: null
    }
  })
}));
vi.mock("../services/profile-events", () => ({ emitProfileChanged: vi.fn() }));

import { OnboardingFlow } from "./onboarding-flow";

function renderIt() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <OnboardingFlow />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  push.mockReset();
  api.getOnboarding.mockReset();
  api.completeOnboarding.mockReset();
});
afterEach(cleanup);

describe("OnboardingFlow", () => {
  it("tamamlanmışsa form yerine bilgi gösterir", async () => {
    api.getOnboarding.mockResolvedValue({ completed: true, completedAt: null, missing: [], version: 1 });
    renderIt();
    expect(await screen.findByText(tr.Onboarding.doneTitle)).toBeInTheDocument();
  });

  it("tür ve platform seçilmeden göndermez", async () => {
    api.getOnboarding.mockResolvedValue({ completed: false, completedAt: null, missing: [], version: 1 });
    renderIt();
    fireEvent.click(await screen.findByRole("button", { name: tr.Onboarding.finish }));
    expect(await screen.findByText(tr.Onboarding.needGenre)).toBeInTheDocument();
    expect(screen.getByText(tr.Onboarding.needPlatform)).toBeInTheDocument();
    expect(api.completeOnboarding).not.toHaveBeenCalled();
  });

  it("seçimle tamamlar ve profile geçer; sunucu hatasında kalır", async () => {
    api.getOnboarding.mockResolvedValue({ completed: false, completedAt: null, missing: [], version: 7 });
    renderIt();
    const catalog = tr.Profile.catalog;
    fireEvent.click(await screen.findByRole("button", { name: new RegExp(`^${catalog.genre.rpg}`) }));
    fireEvent.click(screen.getByRole("button", { name: new RegExp(`^${catalog.platform["epic-games"]}$`) }));

    api.completeOnboarding.mockRejectedValueOnce(new ApiHttpError(503, {}));
    fireEvent.click(screen.getByRole("button", { name: tr.Onboarding.finish }));
    expect(await screen.findByText(tr.Onboarding.errors.busy)).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();

    api.completeOnboarding.mockResolvedValueOnce({ completed: true, completedAt: null, missing: [], version: 8 });
    fireEvent.click(screen.getByRole("button", { name: tr.Onboarding.finish }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/tr/profil"));
    expect(api.completeOnboarding).toHaveBeenLastCalledWith(expect.objectContaining({ version: 7 }));
  });
});
