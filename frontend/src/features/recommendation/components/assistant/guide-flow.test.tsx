import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { RecommendationScopeContext } from "../../hooks/recommendation-scope";
import { recommendationApi } from "../../services/recommendation-api";
import type { AssistantResponse } from "../../types/recommendation";
import type { AssessmentTarget } from "../hardware/assessment-dialog";
import { GuideFlow } from "./guide-flow";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string; children: ReactNode } & Record<string, unknown>) => <a {...rest} href={href}>{children}</a>
}));
vi.mock("../../services/recommendation-api", () => ({ recommendationApi: { askAssistant: vi.fn() } }));
vi.mock("../games/game-filters", () => ({
  GameFilters: () => null,
  useTermOptions: (_kind: string, allLabel: string) => ({ options: [{ value: "", label: allLabel }, { value: "rpg", label: "Rol yapma" }], failed: false, loading: false })
}));
vi.mock("../../hooks/use-hardware-recommendation", () => ({
  useHardwareProfiles: () => ({ data: [{ id: "p-1", name: "Masaüstü" }], loading: false, error: undefined })
}));
vi.mock("../../hooks/use-catalog-search", () => ({
  useCatalogSearch: (query: string) => query.trim().length >= 2
    ? { items: [{ gameId: "g-1", title: "Kuzey Yolu" }], failed: false, loading: false, tooShort: false }
    : { items: [], failed: false, loading: false, tooShort: true }
}));
vi.mock("../hardware/assessment-dialog", () => ({
  AssessmentDialog: ({ target }: { target: AssessmentTarget }) => <div role="dialog" aria-label="değerlendirme">{target.gameTitle}:{target.platform}:{target.hardwareProfileId}</div>
}));

const api = vi.mocked(recommendationApi);
const guide = tr.Recommendation.guide;
const response = (patch: Partial<AssistantResponse>): AssistantResponse => ({
  contractVersion: "assistant-v1", algorithmVersion: "v", topic: "GAME", intent: "SELECT_GAME", locale: "tr", status: "GUIDANCE", template: "t", message: "Sunucu mesajı", steps: [], missingFields: [], ...patch
});
const onNavigate = vi.fn();

function renderFlow() {
  render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <RecommendationScopeContext.Provider value={{ accountId: "acc", consentRequired: vi.fn() }}><GuideFlow onNavigate={onNavigate} /></RecommendationScopeContext.Provider>
  </NextIntlClientProvider>);
}

function choose(label: string, option: string) {
  fireEvent.click(screen.getByRole("combobox", { name: label }));
  fireEvent.click(screen.getByRole("option", { name: option }));
}

afterEach(() => vi.clearAllMocks());

describe("GuideFlow", () => {
  it("serbest metin yoktur; eksik alanı işaretler ve sunucu mesajını gösterir", async () => {
    api.askAssistant.mockResolvedValue(response({ status: "NEEDS_INPUT", message: "Bir tür seç.", missingFields: ["GENRE"], steps: ["FILTER_GENRE"] }));
    renderFlow();
    expect(screen.queryByRole("textbox")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: guide.ask }));
    expect(await screen.findByText("Bir tür seç.")).toBeInTheDocument();
    expect(api.askAssistant).toHaveBeenCalledWith({ contractVersion: "assistant-v1", topic: "GAME", intent: "SELECT_GAME", locale: "tr" });
    expect(screen.getByText(guide.status.NEEDS_INPUT)).toBeInTheDocument();
    expect(screen.getByText("Devam etmek için eksik bilgi: Tür")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: guide.fields.GENRE })).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(guide.fieldMissing)).toBeInTheDocument();
  });

  it("adım kodları gerçek rotalara link olur ve tıklanınca rehber kapanır", async () => {
    api.askAssistant.mockResolvedValue(response({ steps: ["OPEN_GAME_DISCOVERY", "FILTER_GENRE", "OPEN_RECOMMENDATION_HISTORY"] }));
    renderFlow();
    choose(guide.fields.GENRE, "Rol yapma");
    fireEvent.click(screen.getByRole("button", { name: guide.ask }));
    const discovery = await screen.findByRole("link", { name: new RegExp(guide.steps.OPEN_GAME_DISCOVERY) });
    expect(discovery).toHaveAttribute("href", "/games");
    expect(screen.getByRole("link", { name: new RegExp(guide.steps.FILTER_GENRE) })).toHaveAttribute("href", "/for-you/games");
    expect(screen.getByRole("link", { name: new RegExp(guide.steps.OPEN_RECOMMENDATION_HISTORY) })).toHaveAttribute("href", "/for-you/history");
    expect(api.askAssistant).toHaveBeenCalledWith(expect.objectContaining({ genre: "rpg" }));
    fireEvent.click(discovery);
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });

  it("donanım işi alanları tamsa değerlendirme adımı pencereyi aynı seçimlerle açar", async () => {
    api.askAssistant.mockResolvedValue(response({ topic: "HARDWARE", intent: "ASSESS_GAME", steps: ["VERIFY_GAME_REQUIREMENTS", "MEASURE_IN_GAME"] }));
    renderFlow();
    fireEvent.click(screen.getByRole("radio", { name: new RegExp(guide.tasks.ASSESS_GAME.label) }));
    choose(guide.fields.HARDWARE_PROFILE_ID, "Masaüstü");
    fireEvent.change(screen.getByLabelText(guide.fields.GAME_ID), { target: { value: "ku" } });
    fireEvent.click(screen.getByRole("button", { name: "Kuzey Yolu" }));
    choose(guide.fields.PLATFORM, tr.Recommendation.platform.windows);
    fireEvent.click(screen.getByRole("button", { name: guide.ask }));
    const step = await screen.findByRole("button", { name: new RegExp(guide.steps.VERIFY_GAME_REQUIREMENTS) });
    expect(api.askAssistant).toHaveBeenCalledWith({ contractVersion: "assistant-v1", topic: "HARDWARE", intent: "ASSESS_GAME", locale: "tr", hardwareProfileId: "p-1", gameId: "g-1", platform: "windows" });
    expect(screen.getByRole("link", { name: new RegExp(guide.steps.MEASURE_IN_GAME) })).toHaveAttribute("href", "/performance");
    fireEvent.click(step);
    expect(screen.getByRole("dialog", { name: "değerlendirme" })).toHaveTextContent("Kuzey Yolu:windows:p-1");
  });

  it("alanlar eksikken değerlendirme adımı donanım bölümüne gider", async () => {
    api.askAssistant.mockResolvedValue(response({ status: "NEEDS_INPUT", topic: "HARDWARE", intent: "ASSESS_GAME", steps: ["VERIFY_GAME_REQUIREMENTS"], missingFields: ["HARDWARE_PROFILE_ID", "GAME_ID", "PLATFORM"] }));
    renderFlow();
    fireEvent.click(screen.getByRole("radio", { name: new RegExp(guide.tasks.ASSESS_GAME.label) }));
    fireEvent.click(screen.getByRole("button", { name: guide.ask }));
    expect(await screen.findByRole("link", { name: new RegExp(guide.steps.VERIFY_GAME_REQUIREMENTS) })).toHaveAttribute("href", "/for-you/hardware");
    expect(screen.getAllByText(guide.fieldMissing)).toHaveLength(3);
  });

  it("geçmiş işi alan istemez; rehber hatası ayrı metinle gösterilir", async () => {
    api.askAssistant.mockRejectedValue(new ApiHttpError(503, { code: "ASSISTANT_UNAVAILABLE" }));
    renderFlow();
    fireEvent.click(screen.getByRole("radio", { name: new RegExp(guide.tasks.VIEW_HISTORY.label) }));
    expect(screen.queryByText(guide.fieldsLegend)).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: guide.ask }));
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Recommendation.errors.codes.ASSISTANT_UNAVAILABLE.title);
    expect(api.askAssistant).toHaveBeenCalledWith({ contractVersion: "assistant-v1", topic: "LTZ_USAGE", intent: "VIEW_HISTORY", locale: "tr" });
  });
});
