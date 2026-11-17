import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { PerformancePrivacy } from "./performance-privacy";

const { consent, setConsent } = vi.hoisted(() => ({ consent: vi.fn(), setConsent: vi.fn() }));
vi.mock("../services/performance-api", () => ({ performanceApi: { consent, setConsent } }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ children }: { children: React.ReactNode }) => <a href={String("/profile/settings")}>{children}</a> }));
const setup = () => render(<NextIntlClientProvider locale="en" messages={en} timeZone="UTC"><PerformancePrivacy accountId="owner" onDataChanged={vi.fn()} /></NextIntlClientProvider>);
describe("Performance privacy", () => {
  beforeEach(() => { vi.clearAllMocks(); consent.mockResolvedValue({ granted: false, grantedAt: null, revokedAt: null }); setConsent.mockResolvedValue({ granted: true, grantedAt: "2026-01-01T10:00:00Z", revokedAt: null }); });
  it("reads actual consent and requires an explicit grant without claiming measurements started", async () => {
    setup();
    await screen.findByText(en.Performance.consentRevoked);
    fireEvent.click(screen.getByRole("button", { name: en.Performance.grantConsent }));
    expect(setConsent).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription(en.Performance.grantConsentDescription);
    consent.mockResolvedValue({ granted: true, grantedAt: "2026-01-01T10:00:00Z", revokedAt: null });
    fireEvent.click(screen.getByRole("button", { name: en.Performance.confirmConsent }));
    expect(await screen.findByText(en.Performance.consentSaved)).toBeInTheDocument();
    expect(await screen.findByText(en.Performance.consentGranted)).toBeInTheDocument();
    expect(setConsent).toHaveBeenCalledWith(true, expect.any(AbortSignal));
  });
  it("revokes actual consent and reloads its server state", async () => {
    consent.mockResolvedValueOnce({ granted: true, grantedAt: "2026-01-01T10:00:00Z", revokedAt: null });
    setup();
    fireEvent.click(await screen.findByRole("button", { name: en.Performance.revokeConsent }));
    expect(await screen.findByText(en.Performance.consentRevoked)).toBeInTheDocument();
    expect(setConsent).toHaveBeenCalledWith(false, expect.any(AbortSignal));
  });
  it("does not assume consent when its read fails", async () => {
    consent.mockRejectedValue(new ApiHttpError(503));
    setup();
    await screen.findByRole("alert");
    expect(screen.queryByText(en.Performance.consentGranted)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: en.Performance.grantConsent })).not.toBeInTheDocument();
  });
  it("retains the old consent and requires a refresh after uncertain mutation failure", async () => {
    setConsent.mockRejectedValue(new ApiHttpError(503));
    setup();
    fireEvent.click(await screen.findByRole("button", { name: en.Performance.grantConsent }));
    fireEvent.click(screen.getByRole("button", { name: en.Performance.confirmConsent }));
    await screen.findByRole("alert");
    expect(screen.queryByText(en.Performance.consentSaved)).not.toBeInTheDocument();
    expect(screen.getByText(en.Performance.consentRevoked)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: en.Performance.reviewData }));
    await waitFor(() => expect(consent).toHaveBeenCalledTimes(2));
  });
});
