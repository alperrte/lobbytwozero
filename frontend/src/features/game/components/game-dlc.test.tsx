import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import type { GameDetail } from "../types/game";
import { GameDlcSection, GameParentLink } from "./game-dlc";

vi.mock("../services/game-api", () => ({ loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const mainId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const dlcId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e67";
const wrap = (node: React.ReactNode) => render(<NextIntlClientProvider locale="tr" messages={messages}>{node}</NextIntlClientProvider>);
const detail = (type: string, relation: GameDetail["relation"]) => ({ game: { gameId: dlcId, title: "Fixture", gameType: type }, relation } as unknown as GameDetail);
afterEach(() => { vi.resetAllMocks(); });

describe("DLC ↔ ana oyun ilişkisi", () => {
  it("DLC detayında ana oyuna bağlantı verir; ana oyun bilinmiyorsa bağlantı uydurmaz", () => {
    const { unmount } = wrap(<dl><GameParentLink detail={detail("DLC", { parent: { gameId: mainId, title: "Ana Oyun Adı" }, dlcCount: null })} /></dl>);
    expect(screen.getByText(messages.Game.parentGame)).toBeVisible();
    expect(screen.getByRole("link", { name: "Ana Oyun Adı" }).getAttribute("href")).toContain(mainId);
    unmount();
    wrap(<dl><GameParentLink detail={detail("DLC", { parent: null, dlcCount: null })} /><GameParentLink detail={detail("MAIN_GAME", { parent: { gameId: mainId, title: "X" }, dlcCount: 1 })} /></dl>);
    expect(screen.queryByText(messages.Game.parentGame)).toBeNull();
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("ana oyunun DLC'lerini sayıyla ve sayfalı listeler; her biri kendi detayına bağlanır", async () => {
    const item = (n: number) => ({ game: { gameId: `0198a3c2-dddd-7d44-9a55-3f1c2b4d5e${String(10 + n)}`, title: `DLC ${n}`, gameType: "DLC" }, releaseDate: n % 2 ? "2026-05-01" : null });
    load.mockImplementation(async (resource) => {
      const page = (resource as { page: number }).page;
      return { items: page === 0 ? [item(1), item(2)] : [item(3)], page, size: 12, totalElements: 15 };
    });
    wrap(<GameDlcSection gameId={mainId} count={15} />);
    expect(await screen.findByRole("heading", { name: "DLC'ler (15)" })).toBeVisible();
    const first = await screen.findByRole("link", { name: "DLC 1" });
    expect(first.getAttribute("href")).toContain("0198a3c2-dddd-7d44-9a55-3f1c2b4d5e11");
    expect(screen.getByText(messages.Game.releaseUnknown)).toBeVisible();
    expect(load.mock.calls[0][0]).toEqual({ kind: "dlc", gameId: mainId, page: 0 });
    await act(async () => { fireEvent.click(screen.getByRole("button", { name: messages.Game.next })); });
    expect(await screen.findByRole("link", { name: "DLC 3" })).toBeVisible();
    expect(load.mock.calls.at(-1)?.[0]).toEqual({ kind: "dlc", gameId: mainId, page: 1 });
  });

  it("DLC yoksa bölüm gösterilmez ve istek yapılmaz", () => {
    load.mockClear();
    wrap(<GameDlcSection gameId={mainId} count={0} />);
    expect(screen.queryByRole("heading")).toBeNull();
    expect(load).not.toHaveBeenCalled();
  });
});
