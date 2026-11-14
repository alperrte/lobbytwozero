import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import { PerformanceDashboard } from "./performance-dashboard";

const { requireSession, hardware, history } = vi.hoisted(() => ({ requireSession: vi.fn(), hardware: vi.fn(), history: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: requireSession }));
vi.mock("@/features/hardware", () => ({ HardwareSnapshot: ({ accountId }: { accountId: string }) => { hardware(accountId); return <p>{accountId}</p>; } }));
vi.mock("./session-history", () => ({ SessionHistory: ({ accountId }: { accountId: string }) => { history(accountId); return null; } }));
vi.mock("./performance-privacy", () => ({ PerformancePrivacy: () => null }));
const setup = () => render(<NextIntlClientProvider locale="en" messages={en}><PerformanceDashboard /></NextIntlClientProvider>);
describe("Performance dashboard access", () => {
  beforeEach(() => vi.clearAllMocks());
  it.each(["loading", "guest"])("does not mount any private consumers while %s", (status) => {
    requireSession.mockReturnValue({ status, me: null });
    setup();
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(hardware).not.toHaveBeenCalled();
    expect(history).not.toHaveBeenCalled();
  });
  it("keys private consumers to the verified session account", () => {
    requireSession.mockReturnValue({ status: "authenticated", me: { accountId: "first-owner" } });
    const view = setup();
    expect(hardware).toHaveBeenLastCalledWith("first-owner");
    requireSession.mockReturnValue({ status: "authenticated", me: { accountId: "second-owner" } });
    view.rerender(<NextIntlClientProvider locale="en" messages={en}><PerformanceDashboard /></NextIntlClientProvider>);
    expect(screen.queryByText("first-owner")).not.toBeInTheDocument();
    expect(history).toHaveBeenLastCalledWith("second-owner");
  });
});
