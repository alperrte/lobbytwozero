import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { useRecommendationScope } from "../../hooks/recommendation-scope";
import { enrichmentApi } from "../../services/enrichment-api";
import { recommendationCache } from "../../services/recommendation-cache";
import { ConsentGate } from "./consent-gate";

const session = vi.hoisted(() => ({ value: { status: "authenticated", me: { accountId: "acc-1" } } as { status: string; me?: { accountId: string } } }));
vi.mock("@/features/auth", () => ({ useSession: () => session.value }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string; children: ReactNode } & Record<string, unknown>) => <a {...rest} href={href}>{children}</a>
}));
vi.mock("../../services/enrichment-api", () => ({ enrichmentApi: { getConsent: vi.fn(), saveConsent: vi.fn() } }));

const api = vi.mocked(enrichmentApi);

function Child() {
  const scope = useRecommendationScope();
  return <p>öneriler: {scope.accountId}<button type="button" onClick={scope.consentRequired}>revoke</button></p>;
}

function renderGate() {
  return render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul"><ConsentGate><Child /></ConsentGate></NextIntlClientProvider>);
}

afterEach(() => {
  vi.clearAllMocks();
  recommendationCache.clear();
  session.value = { status: "authenticated", me: { accountId: "acc-1" } };
});

describe("ConsentGate", () => {
  it("rıza açıksa içeriği kapsamla birlikte gösterir", async () => {
    api.getConsent.mockResolvedValue({ recommendation: true, version: 1 });
    renderGate();
    expect(await screen.findByText("öneriler: acc-1")).toBeVisible();
  });

  it("rıza yoksa içerik ve öneri isteği yoktur; izin verince yalnız sürümle kaydeder", async () => {
    api.getConsent.mockResolvedValue({ recommendation: false, version: 4 });
    api.saveConsent.mockResolvedValue({ recommendation: true, version: 5 });
    recommendationCache.set("acc-1", "runs:1", 1);
    renderGate();
    const grant = await screen.findByRole("button", { name: tr.Recommendation.consent.grant });
    expect(screen.queryByText(/öneriler:/)).toBeNull();
    expect(screen.getByRole("link", { name: tr.Recommendation.consent.settings })).toHaveAttribute("href", "/profile/settings");
    fireEvent.click(grant);
    expect(api.saveConsent).toHaveBeenCalledWith({ recommendation: true, version: 4 });
    expect(await screen.findByText("öneriler: acc-1")).toBeVisible();
    expect(recommendationCache.get("acc-1", "runs:1")).toBeUndefined();
  });

  it("kaydetme hatasında hata gösterilir ve durum yeniden okunur", async () => {
    api.getConsent.mockResolvedValue({ recommendation: false, version: 1 });
    api.saveConsent.mockRejectedValue(new ApiHttpError(409, { code: "CONCURRENT_MODIFICATION" }));
    renderGate();
    fireEvent.click(await screen.findByRole("button", { name: tr.Recommendation.consent.grant }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Recommendation.errors.status["409"].title);
    await waitFor(() => expect(api.getConsent).toHaveBeenCalledTimes(2));
  });

  it("alt bileşen CONSENT_REQUIRED bildirirse kapı yeniden açılır", async () => {
    api.getConsent.mockResolvedValueOnce({ recommendation: true, version: 1 }).mockResolvedValueOnce({ recommendation: false, version: 2 });
    renderGate();
    fireEvent.click(await screen.findByRole("button", { name: "revoke" }));
    expect(await screen.findByRole("button", { name: tr.Recommendation.consent.grant })).toBeVisible();
    expect(screen.queryByText(/öneriler:/)).toBeNull();
  });

  it("rıza okunamazsa tekrar dene sunar", async () => {
    api.getConsent.mockRejectedValueOnce(new ApiHttpError(503)).mockResolvedValueOnce({ recommendation: true, version: 1 });
    renderGate();
    fireEvent.click(await screen.findByRole("button", { name: tr.Recommendation.common.retry }));
    expect(await screen.findByText("öneriler: acc-1")).toBeVisible();
  });

  it("misafire hiçbir şey çizmez ve istek atmaz", () => {
    session.value = { status: "guest" };
    const { container } = renderGate();
    expect(container).toBeEmptyDOMElement();
    expect(api.getConsent).not.toHaveBeenCalled();
  });
});
