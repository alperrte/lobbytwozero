import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { PerformanceSession } from "../types/performance";
import { SessionHistory } from "./session-history";

const { sessions, series, report } = vi.hoisted(() => ({ sessions: vi.fn(), series: vi.fn(), report: vi.fn() }));
vi.mock("../services/performance-api", () => ({ performanceApi: { sessions, series, report } }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ children }: { children: React.ReactNode }) => <a href={String("/profile/computers")}>{children}</a> }));
const session: PerformanceSession = { id: "session-one", gameId: "game", gameTitle: "Saved Game", hardwareProfileId: null, deviceLinkId: null, source: "MANUAL", status: "COMPLETED", startedAt: "2026-01-01T10:00:00Z", endedAt: "2026-01-01T11:00:00Z", durationSeconds: 3600, sampleCount: 2 };
const setup = () => render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul"><SessionHistory accountId="owner" /></NextIntlClientProvider>);

describe("Performance session history", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessions.mockResolvedValue({ items: [session], page: 0, size: 20, hasNext: false, totalElements: 1 });
    series.mockResolvedValue({ sessionId: session.id, metric: "FPS", unit: "FPS", resolution: "RAW", downsampled: false, bucketMillis: null, aggregation: "NONE", totalSamples: 2, points: [{ at: session.startedAt, value: 60, min: 60, max: 60, count: 1 }, { at: session.endedAt, value: 72, min: 72, max: 72, count: 1 }] });
    report.mockRejectedValue(new ApiHttpError(404));
  });
  it("does not fan out series/report calls until a session is selected", async () => {
    setup();
    await screen.findByText("Saved Game");
    expect(series).not.toHaveBeenCalled();
    expect(report).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Sonraki" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Saved Game oturumunu incele" }));
    expect(await screen.findByRole("table")).toHaveTextContent("72");
    expect(await screen.findByText(tr.Performance.reportEmpty)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "FPS (FPS)" })).toBeInTheDocument();
  });
  it("resets selection and page when filtering and uses server pagination", async () => {
    sessions.mockResolvedValueOnce({ items: [session], page: 0, size: 20, hasNext: true, totalElements: 21 });
    setup();
    await screen.findByText("Saved Game");
    fireEvent.click(screen.getByRole("button", { name: "Sonraki" }));
    await waitFor(() => expect(sessions).toHaveBeenLastCalledWith({ page: 1, status: undefined }, expect.any(AbortSignal)));
    fireEvent.change(screen.getByRole("combobox", { name: "Oturum durumu" }), { target: { value: "ABANDONED" } });
    await waitFor(() => expect(sessions).toHaveBeenLastCalledWith({ page: 0, status: "ABANDONED" }, expect.any(AbortSignal)));
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
  it("keeps a series failure separate from a valid report and preserves null versus zero", async () => {
    series.mockRejectedValue(new ApiHttpError(503));
    report.mockResolvedValue({ id: "report", sessionId: session.id, resolution: "1920x1080", graphicsPreset: "HIGH", avgFps: null, p1LowFps: null, maxCpuTempC: null, maxGpuTempC: null, avgCpuUsagePercent: 0, avgGpuUsagePercent: null, notes: "<img src=x onerror=alert(1)>", source: "MANUAL", createdAt: session.endedAt, updatedAt: session.endedAt });
    setup();
    fireEvent.click(await screen.findByRole("button", { name: "Saved Game oturumunu incele" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Performance.errors["503"]);
    expect(await screen.findByText("1920x1080")).toBeInTheDocument();
    expect(screen.getByText("0 %")).toBeInTheDocument();
    expect(screen.getAllByText(tr.Performance.notRecorded)).toHaveLength(5);
    expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });
  it("requests the chosen metric and time window without manufacturing samples", async () => {
    setup();
    fireEvent.click(await screen.findByRole("button", { name: "Saved Game oturumunu incele" }));
    await screen.findByRole("table");
    series.mockResolvedValue({ sessionId: session.id, metric: "CPU_TEMP_C", unit: "°C", totalSamples: 0, points: [] });
    fireEvent.change(screen.getByRole("combobox", { name: "Ölçüm türü" }), { target: { value: "CPU_TEMP_C" } });
    expect(await screen.findByText(tr.Performance.measurementsEmpty)).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "Zaman aralığı" }), { target: { value: "5" } });
    await waitFor(() => expect(series).toHaveBeenLastCalledWith(session.id, "CPU_TEMP_C", session.startedAt, "2026-01-01T10:05:00.000Z", expect.any(AbortSignal)));
  });
  it.each([401, 403, 404, 409, 429, 500, 503])("does not turn HTTP %i into empty history", async (status) => {
    sessions.mockRejectedValue(new ApiHttpError(status));
    setup();
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Performance.errors[String(status) as keyof typeof tr.Performance.errors]);
    expect(screen.queryByText(tr.Performance.historyEmpty)).not.toBeInTheDocument();
  });
  it("has a real empty response without a selectable session", async () => {
    sessions.mockResolvedValue({ items: [], page: 0, size: 20, hasNext: false, totalElements: 0 });
    setup();
    expect(await screen.findByText(tr.Performance.historyEmpty)).toBeInTheDocument();
    expect(within(screen.getByRole("navigation")).getByRole("button", { name: "Önceki" })).toBeDisabled();
    expect(report).not.toHaveBeenCalled();
  });
});
