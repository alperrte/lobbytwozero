import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { HardwareProfileReference } from "./hardware-profile-reference";

const { profile } = vi.hoisted(() => ({ profile: vi.fn() }));
vi.mock("../services/hardware-api", () => ({ hardwareApi: { profile } }));
const setup = () => render(<NextIntlClientProvider locale="en" messages={en}><HardwareProfileReference accountId="owner" profileId="report-profile" /></NextIntlClientProvider>);
describe("Report hardware reference", () => {
  beforeEach(() => { profile.mockReset(); });
  it("resolves the report profile rather than substituting another computer", async () => {
    profile.mockResolvedValue({ profile: { name: "Report laptop" } });
    setup();
    expect(await screen.findByText("Session computer: Report laptop")).toBeInTheDocument();
    expect(profile).toHaveBeenCalledWith("report-profile", 0, expect.any(AbortSignal));
  });
  it("preserves a missing or inaccessible profile as an error", async () => {
    profile.mockRejectedValue(new ApiHttpError(404));
    setup();
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(/Session computer:/)).not.toBeInTheDocument();
  });
});
