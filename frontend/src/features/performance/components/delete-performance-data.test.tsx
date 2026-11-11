import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { DeletePerformanceData } from "./delete-performance-data";

const { deleteSession, deleteAllSessions } = vi.hoisted(() => ({ deleteSession: vi.fn(), deleteAllSessions: vi.fn() }));
vi.mock("../services/performance-api", () => ({ performanceApi: { deleteSession, deleteAllSessions } }));
const onDeleted = vi.fn();
const onReview = vi.fn();
const setup = (all = false) => render(<NextIntlClientProvider locale="tr" messages={tr}><DeletePerformanceData session={all ? undefined : { id: "owned-session", gameTitle: "Saved Game" }} onDeleted={onDeleted} onReview={onReview} /></NextIntlClientProvider>);
const open = () => fireEvent.click(screen.getByRole("button", { name: tr.Performance.deleteSession }));
describe("Performance data deletion", () => {
  beforeEach(() => { vi.clearAllMocks(); deleteSession.mockResolvedValue(undefined); deleteAllSessions.mockResolvedValue({ deletedSessions: 2 }); });
  it("requires explicit confirmation and cancellation never sends a mutation", () => {
    setup(); open();
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription(/Saved Game/);
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.cancel }));
    expect(deleteSession).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });
  it("locks duplicate submits and declares success only after server confirmation", async () => {
    let finish!: () => void;
    deleteSession.mockImplementation(() => new Promise<void>((resolve) => { finish = resolve; }));
    setup(); open();
    const confirm = screen.getByRole("button", { name: tr.Performance.confirmDelete });
    fireEvent.click(confirm); fireEvent.click(confirm);
    expect(deleteSession).toHaveBeenCalledTimes(1);
    expect(deleteSession).toHaveBeenCalledWith("owned-session", expect.any(AbortSignal));
    expect(screen.getByRole("dialog")).toHaveAttribute("aria-busy", "true");
    expect(onDeleted).not.toHaveBeenCalled();
    expect(screen.queryByText(tr.Performance.deleted)).not.toBeInTheDocument();
    await act(async () => finish());
    expect(onDeleted).toHaveBeenCalledOnce();
    expect(screen.getByRole("status")).toHaveTextContent(tr.Performance.deleted);
  });
  it.each([401, 403, 404, 409, 429, 500, 503])("does not remove local records for HTTP %i", async (status) => {
    deleteSession.mockRejectedValue(new ApiHttpError(status));
    setup(); open();
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.confirmDelete }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Performance.errors[String(status) as keyof typeof tr.Performance.errors]);
    expect(onDeleted).not.toHaveBeenCalled();
    expect(screen.queryByText(tr.Performance.deleted)).not.toBeInTheDocument();
  });
  it("requires a fresh read for an uncertain response before enabling another delete", async () => {
    deleteSession.mockRejectedValue(new TypeError("lost response"));
    setup(); open();
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.confirmDelete }));
    await screen.findByText(tr.Performance.uncertainMutation);
    expect(screen.getByRole("button", { name: tr.Performance.reviewRequired })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.reviewData }));
    expect(onReview).toHaveBeenCalledOnce();
    expect(onDeleted).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("uses only the bulk endpoint after bulk confirmation", async () => {
    setup(true);
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.deleteAll }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAccessibleDescription(tr.Performance.deleteAllDescription);
    fireEvent.click(within(dialog).getByRole("button", { name: tr.Performance.confirmDelete }));
    await screen.findByText(tr.Performance.deleted);
    expect(deleteAllSessions).toHaveBeenCalledOnce();
    expect(deleteSession).not.toHaveBeenCalled();
  });
  it("ignores completion after the private consumer unmounts", async () => {
    let finish!: () => void;
    let signal: AbortSignal | undefined;
    deleteSession.mockImplementation((_: string, active: AbortSignal) => { signal = active; return new Promise<void>((resolve) => { finish = resolve; }); });
    const view = setup(); open();
    fireEvent.click(screen.getByRole("button", { name: tr.Performance.confirmDelete }));
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => finish());
    expect(onDeleted).not.toHaveBeenCalled();
  });
});
