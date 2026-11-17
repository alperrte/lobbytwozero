import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { DeviceList } from "./device-list";

const { devices, session } = vi.hoisted(() => ({ devices: vi.fn(), session: { status: "authenticated", me: { accountId: "owner" } } }));
vi.mock("@/features/auth", () => ({ useSession: () => session }));
vi.mock("../services/performance-api", () => ({ performanceApi: { devices } }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ children }: { children: React.ReactNode }) => <a href={String("/login")}>{children}</a> }));
const setup = () => render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul"><DeviceList /></NextIntlClientProvider>);

describe("Performance devices", () => {
  beforeEach(() => { devices.mockReset(); session.status = "authenticated"; session.me.accountId = "owner"; });
  it("does not request private data for guests", () => {
    session.status = "guest";
    setup();
    expect(screen.getByRole("link", { name: "Giriş yap" })).toBeInTheDocument();
    expect(devices).not.toHaveBeenCalled();
  });
  it("shows loading then a real empty response", async () => {
    devices.mockResolvedValue([]);
    setup();
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(await screen.findByText(tr.Performance.devicesEmpty)).toBeInTheDocument();
  });
  it.each([401, 403, 404, 409, 429, 500, 503])("distinguishes HTTP %i from an empty list", async (status) => {
    devices.mockRejectedValue(new ApiHttpError(status));
    setup();
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Performance.errors[String(status) as keyof typeof tr.Performance.errors]);
    expect(screen.queryByText(tr.Performance.devicesEmpty)).not.toBeInTheDocument();
  });
  it("retries network failures without fabricating devices", async () => {
    devices.mockRejectedValueOnce(new TypeError("offline")).mockResolvedValueOnce([]);
    setup();
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));
    expect(await screen.findByText(tr.Performance.devicesEmpty)).toBeInTheDocument();
  });
  it("renders labels as text and hides old data when the account changes", async () => {
    devices.mockResolvedValueOnce([{ id: "link", label: "<script>private</script>", status: "ACTIVE", linkedAt: "2026-01-01T10:00:00Z", lastSyncAt: null }]);
    const view = setup();
    expect(await screen.findByRole("heading", { name: "<script>private</script>" })).toBeInTheDocument();
    expect(document.querySelector("script")).toBeNull();
    devices.mockResolvedValueOnce([]);
    session.me.accountId = "second-owner";
    view.rerender(<NextIntlClientProvider locale="tr" messages={tr}><DeviceList /></NextIntlClientProvider>);
    expect(screen.queryByText("<script>private</script>")).not.toBeInTheDocument();
    await waitFor(() => expect(devices).toHaveBeenCalledTimes(2));
  });
});
