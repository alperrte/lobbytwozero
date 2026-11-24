import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { RecommendationScopeContext } from "../../hooks/recommendation-scope";
import { recommendationApi } from "../../services/recommendation-api";
import { recommendationCache } from "../../services/recommendation-cache";
import type { RecommendationRun } from "../../types/recommendation";
import type { GameFilterValue } from "./game-filters";
import { GamesScreen } from "./games-screen";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string; children: ReactNode } & Record<string, unknown>) => <a {...rest} href={href}>{children}</a>
}));
vi.mock("../../services/recommendation-api", () => ({ recommendationApi: { listRuns: vi.fn(), getRun: vi.fn(), generateGames: vi.fn() } }));
vi.mock("./game-filters", () => ({
  GameFilters: ({ value, onChange }: { value: GameFilterValue; onChange: (value: GameFilterValue) => void }) =>
    <button type="button" onClick={() => onChange({ ...value, genre: "rpg", platform: "pc", size: 6 })}>filtre-sec</button>
}));
vi.mock("./game-run-view", () => ({
  GameRunView: ({ run, fresh }: { run: RecommendationRun; fresh: boolean }) => <p>liste:{run.runId}:{fresh ? "yeni" : "kayitli"}</p>,
  RunMeta: () => null
}));

const api = vi.mocked(recommendationApi);
const games = tr.Recommendation.games;
const run = (runId: string): RecommendationRun => ({ runId, algorithmVersion: "v", createdAt: "2026-11-20T10:00:00Z", status: "PARTIAL", genre: null, platform: null, items: [], limitations: [] });

function renderScreen() {
  render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <RecommendationScopeContext.Provider value={{ accountId: "acc", consentRequired: vi.fn() }}><GamesScreen /></RecommendationScopeContext.Provider>
  </NextIntlClientProvider>);
}

afterEach(() => {
  vi.clearAllMocks();
  recommendationCache.clear();
});

describe("GamesScreen", () => {
  it("açılışta yalnız son çalışmayı okur; POST /games göndermez", async () => {
    api.listRuns.mockResolvedValue({ items: [{ runId: "r-1", algorithmVersion: "v", createdAt: "t", resultCount: 3 }], nextCursor: null });
    api.getRun.mockResolvedValue(run("r-1"));
    renderScreen();
    expect(await screen.findByText("liste:r-1:kayitli")).toBeInTheDocument();
    expect(api.listRuns).toHaveBeenCalledWith({ size: 1 }, expect.any(AbortSignal));
    expect(api.generateGames).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: games.latestTitle })).toBeInTheDocument();
  });

  it("hiç çalışma yoksa dürüst boş durum gösterir", async () => {
    api.listRuns.mockResolvedValue({ items: [], nextCursor: null });
    renderScreen();
    expect(await screen.findByText(games.noRunTitle)).toBeInTheDocument();
    expect(api.getRun).not.toHaveBeenCalled();
  });

  it("'Öneri oluştur' seçili filtrelerle tek POST atar ve yeni listeyi gösterir", async () => {
    api.listRuns.mockResolvedValue({ items: [], nextCursor: null });
    api.generateGames.mockResolvedValue(run("r-new"));
    renderScreen();
    await screen.findByText(games.noRunTitle);
    fireEvent.click(screen.getByRole("button", { name: "filtre-sec" }));
    const generate = screen.getByRole("button", { name: games.generate });
    fireEvent.click(generate);
    fireEvent.click(generate);
    expect(await screen.findByText("liste:r-new:yeni")).toBeInTheDocument();
    expect(api.generateGames).toHaveBeenCalledTimes(1);
    expect(api.generateGames).toHaveBeenCalledWith({ genre: "rpg", platform: "pc", size: 6 });
    expect(screen.getByRole("heading", { name: games.freshTitle })).toBeInTheDocument();
  });

  it("geçmiş sınırı dolunca açıklayıcı hata gösterir", async () => {
    api.listRuns.mockResolvedValue({ items: [], nextCursor: null });
    api.generateGames.mockRejectedValue(new ApiHttpError(429, { code: "HISTORY_LIMIT_REACHED" }));
    renderScreen();
    await screen.findByText(games.noRunTitle);
    fireEvent.click(screen.getByRole("button", { name: games.generate }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Recommendation.errors.codes.HISTORY_LIMIT_REACHED.title);
  });

  it("son çalışma okunamazsa bölüm hatası ve tekrar dene sunar", async () => {
    api.listRuns.mockRejectedValueOnce(new ApiHttpError(503)).mockResolvedValueOnce({ items: [], nextCursor: null });
    renderScreen();
    fireEvent.click(await screen.findByRole("button", { name: tr.Recommendation.common.retry }));
    await waitFor(() => expect(screen.getByText(games.noRunTitle)).toBeInTheDocument());
    expect(api.generateGames).not.toHaveBeenCalled();
  });
});
