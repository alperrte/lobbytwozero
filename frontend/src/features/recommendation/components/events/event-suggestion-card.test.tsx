import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { RecommendationScopeContext } from "../../hooks/recommendation-scope";
import { enrichmentApi } from "../../services/enrichment-api";
import type { EventSuggestionView } from "../../services/adapters";
import { EventSuggestionCard } from "./event-suggestion-card";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string | { params: { gameId: string } }; children: ReactNode } & Record<string, unknown>) =>
    <a {...rest} href={typeof href === "string" ? href : `/games/${href.params.gameId}`}>{children}</a>
}));
vi.mock("../../services/enrichment-api", () => ({ enrichmentApi: { joinEvent: vi.fn() } }));

const api = vi.mocked(enrichmentApi);
const events = tr.Recommendation.events;
const view: EventSuggestionView = {
  event: { eventId: "e-1", type: "TOURNAMENT", title: "Kış Kupası", startsAt: "2026-12-05T17:00:00Z", endsAt: "2026-12-05T20:00:00Z", gameId: "g-1", score: 3, sharedGenres: ["action"], sharedPlatforms: ["steam"] },
  game: { gameId: "g-1", title: "Kuzey Yolu", coverUrl: null }
};

function renderCard(value: EventSuggestionView = view) {
  render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <RecommendationScopeContext.Provider value={{ accountId: "acc", consentRequired: vi.fn() }}>
      <EventSuggestionCard view={value} />
    </RecommendationScopeContext.Provider>
  </NextIntlClientProvider>);
}

afterEach(() => vi.clearAllMocks());

describe("EventSuggestionCard", () => {
  it("tür, İstanbul saatiyle tarih aralığı, bağlı oyun ve ortak ilgi alanlarını gösterir", () => {
    renderCard();
    expect(screen.getByText(events.type.TOURNAMENT)).toBeInTheDocument();
    const time = screen.getByText((_, element) => element?.tagName === "TIME");
    expect(time).toHaveAttribute("dateTime", view.event.startsAt);
    expect(time.textContent).toMatch(/20:00/);
    expect(time.textContent).toMatch(/23:00/);
    expect(screen.getByRole("link", { name: "Kuzey Yolu" })).toHaveAttribute("href", "/games/g-1");
    expect(screen.getByRole("list", { name: events.sharedGenres })).toHaveTextContent(tr.Profile.catalog.genre.action);
    expect(screen.getByRole("list", { name: events.sharedPlatforms })).toHaveTextContent(tr.Profile.catalog.platform.steam);
  });

  it("bilinmeyen etkinlik türü kod olarak gösterilir, oyun yoksa oyun linki olmaz", () => {
    renderCard({ event: { ...view.event, type: "LAN_PARTY", gameId: null }, game: null });
    expect(screen.getByText("LAN_PARTY")).toBeInTheDocument();
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("katıl → Event Service'e istek atar ve 'Katıldın' gösterir", async () => {
    api.joinEvent.mockResolvedValue({ id: "p-1", eventId: "e-1", status: "JOINED" });
    renderCard();
    fireEvent.click(screen.getByRole("button", { name: "Kış Kupası etkinliğine katıl" }));
    expect(await screen.findByRole("status")).toHaveTextContent(events.outcome.JOINED);
    expect(api.joinEvent).toHaveBeenCalledWith("e-1");
  });

  it.each([
    ["ALREADY_JOINED", events.outcome.ALREADY_JOINED],
    ["EVENT_FULL", events.outcome.EVENT_FULL],
    ["EVENT_NOT_OPEN", events.outcome.EVENT_NOT_OPEN]
  ])("409 %s durum olarak yazılır", async (code, text) => {
    api.joinEvent.mockRejectedValue(new ApiHttpError(409, { code }));
    renderCard();
    fireEvent.click(screen.getByRole("button", { name: "Kış Kupası etkinliğine katıl" }));
    expect(await screen.findByRole("status")).toHaveTextContent(text);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("429 bekleme süresince düğme pasif kalır", async () => {
    api.joinEvent.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 30 }));
    renderCard();
    fireEvent.click(screen.getByRole("button", { name: "Kış Kupası etkinliğine katıl" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Recommendation.errors.status["429"].title);
    expect(screen.getByRole("button", { name: "Kış Kupası etkinliğine katıl" })).toBeDisabled();
  });
});
