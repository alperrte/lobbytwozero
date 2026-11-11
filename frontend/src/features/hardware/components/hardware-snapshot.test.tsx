import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { HardwareSnapshot } from "./hardware-snapshot";

const { profiles, profile, privacy } = vi.hoisted(() => ({ profiles: vi.fn(), profile: vi.fn(), privacy: vi.fn() }));
vi.mock("../services/hardware-api", () => ({ hardwareApi: { profiles, profile, privacy } }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ children }: { children: React.ReactNode }) => <a href={String("/profile/computers")}>{children}</a> }));
const first = { id: "first", name: "Manual computer", source: "MANUAL" };
const second = { id: "second", name: "Laptop", source: "MANUAL" };
const setup = () => render(<NextIntlClientProvider locale="tr" messages={tr}><HardwareSnapshot accountId="owner" /></NextIntlClientProvider>);
describe("Hardware snapshot", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profiles.mockResolvedValue({ items: [first, second], totalPages: 1 });
    profile.mockImplementation((id: string) => Promise.resolve({ profile: id === "first" ? first : second, components: [{ id: "cpu", category: "CPU", modelName: "Manually entered CPU", brandName: null, quantity: 1, source: "MANUAL", specifications: { coreCount: 8, capacityGb: null, clockMhz: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null } }], totalPages: 1 }));
    privacy.mockResolvedValue({ visibility: "PRIVATE" });
  });
  it("supports multiple computers, optional brands, manual models and unknown fields", async () => {
    setup();
    expect(await screen.findByText(/Manually entered CPU/)).toBeInTheDocument();
    expect(screen.getByText(/Marka belirtilmemiş/)).toBeInTheDocument();
    expect(screen.getByText(/Profil görünürlüğü: Yalnız ben/)).toBeInTheDocument();
    expect(screen.getAllByText(tr.Hardware.common.unknown).length).toBeGreaterThan(0);
    fireEvent.change(screen.getByRole("combobox", { name: "Bilgisayar" }), { target: { value: "second" } });
    await waitFor(() => expect(profile).toHaveBeenLastCalledWith("second", 0, expect.any(AbortSignal)));
  });
  it("shows an empty account without detail requests", async () => {
    profiles.mockResolvedValue({ items: [], totalPages: 0 });
    setup();
    expect(await screen.findByRole("heading", { name: tr.Performance.hardwareEmpty })).toBeInTheDocument();
    expect(profile).not.toHaveBeenCalled();
    expect(privacy).not.toHaveBeenCalled();
  });
  it("keeps privacy errors separate and never assumes public visibility", async () => {
    privacy.mockRejectedValue(new ApiHttpError(503));
    setup();
    await screen.findByRole("alert");
    expect(await screen.findByText(/Manually entered CPU/)).toBeInTheDocument();
    expect(screen.queryByText(/Profil görünürlüğü:/)).not.toBeInTheDocument();
  });
});
