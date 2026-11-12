import { cleanup, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Remote } from "../hooks/use-remote";
import type { OnboardingState } from "../services/onboarding-api";

vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/profil",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

import { OnboardingPrompt } from "./onboarding-prompt";

function renderIt(state: unknown) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <OnboardingPrompt onboarding={{ state, retry: vi.fn() } as unknown as Remote<OnboardingState>} />
    </NextIntlClientProvider>
  );
}

afterEach(cleanup);

describe("OnboardingPrompt", () => {
  it("yalnız sunucu tamamlanmadı derse kartı gösterir", () => {
    renderIt({ status: "ready", data: { completed: false, completedAt: null, missing: [], version: 1 } });
    expect(screen.getByRole("link", { name: tr.Onboarding.prompt.action })).toBeInTheDocument();
  });

  it("yükleniyor, hata ya da tamamlandıysa hiçbir şey göstermez", () => {
    for (const state of [
      { status: "loading" },
      { status: "error", code: null },
      { status: "ready", data: { completed: true, completedAt: null, missing: [], version: 1 } }
    ]) {
      const { container } = renderIt(state);
      expect(container).toBeEmptyDOMElement();
      cleanup();
    }
  });
});
