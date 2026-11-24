import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { parseAssessment } from "../../schemas/response-guards";
import { AssessmentResult } from "./assessment-result";

const a = tr.Recommendation.assessment;
const level = (name: string, computeStatus = "UNKNOWN", checkStatus = "UNKNOWN") => ({
  level: name, computeStatus, overallStatus: "UNKNOWN",
  checks: [{ field: "CPU", status: checkStatus, actual: "8 çekirdek", required: null, reason: "MODEL_ORDER_OR_REQUIREMENT_UNKNOWN" }]
});
const fps = { status: "INSUFFICIENT_DATA", estimatedLowerFps: null, estimatedUpperFps: null, confidence: "NONE", observedSampleCount: 0, observedSessionCount: 0, producerSampleThresholdMet: false, lastObservedAt: null, requestedResolution: null, requestedPreset: null, reason: "NO_FPS_SAMPLES", evidence: null };
const base = {
  hardwareProfileId: "p", hardwareProfileVersion: 2, gameId: "g", platform: "windows", assessedAt: "2026-11-20T10:00:00Z", suitability: "UNKNOWN",
  minimum: level("MINIMUM"), recommended: level("RECOMMENDED"), fps,
  graphics: { status: "INSUFFICIENT_DATA", suggestedPreset: null, reason: "MINIMUM_COMPUTE_OR_COMPONENTS_NOT_CONFIRMED" },
  upgrades: [], componentCompatibility: { status: "UNKNOWN", checks: [], limitations: [] }, limitations: ["UNKNOWN_IS_NOT_KNOWN_INCOMPATIBLE"]
};

function renderResult(patch: Record<string, unknown> = {}) {
  return render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <AssessmentResult assessment={parseAssessment({ ...base, ...patch })} />
  </NextIntlClientProvider>);
}

describe("AssessmentResult", () => {
  it("UNKNOWN uygunluk 'doğrulanamadı' der; uyumsuz ya da uyumlu demez", () => {
    renderResult();
    const summary = screen.getAllByRole("status")[0];
    expect(summary).toHaveTextContent(a.summary.unknown.title);
    expect(summary).toHaveTextContent("uyumsuz olduğu anlamına gelmez");
    expect(screen.getByText(tr.Recommendation.limitations.UNKNOWN_IS_NOT_KNOWN_INCOMPATIBLE)).toBeInTheDocument();
  });

  it("minimum CPU/GPU/RAM karşılansa bile sonuç 'uyumlu' değildir", () => {
    renderResult({ minimum: level("MINIMUM", "MEETS", "MEETS") });
    expect(screen.getAllByRole("status")[0]).toHaveTextContent(a.summary.computeMet.title);
    expect(screen.getByText(a.summary.computeMet.description)).toHaveTextContent("bu bir “uyumlu” sonucu değildir");
  });

  it("BELOW_MINIMUM eksik bileşeni vurgular", () => {
    renderResult({ suitability: "BELOW_MINIMUM", minimum: level("MINIMUM", "BELOW", "BELOW") });
    expect(screen.getAllByRole("status")[0]).toHaveTextContent(a.summary.below.title);
    expect(screen.getAllByText(a.check.BELOW).length).toBeGreaterThan(0);
  });

  it("INSUFFICIENT_DATA aralık uydurmaz, nedenini söyler", () => {
    renderResult();
    expect(screen.getByText(a.fps.noEstimateChip)).toBeInTheDocument();
    expect(screen.getByText(tr.Recommendation.reasons.NO_FPS_SAMPLES)).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\d+–\d+ FPS/);
  });

  it("ESTIMATED geçmiş ölçüm aralığı olarak etiketlenir", () => {
    renderResult({ fps: { ...fps, status: "ESTIMATED", estimatedLowerFps: 55, estimatedUpperFps: 70, confidence: "LOW", observedSampleCount: 240, observedSessionCount: 4, reason: "MATCHED_HISTORICAL_DEVICE_RANGE_NOT_CURRENT_MEASUREMENT" } });
    expect(screen.getByText(a.fps.estimatedChip)).toBeInTheDocument();
    expect(screen.getByText("55–70 FPS")).toBeInTheDocument();
    expect(screen.getByText(/4 oturumdaki 240 ölçüme/)).toHaveTextContent("şu anki performansın ölçümü değildir");
    expect(screen.getByText("Güven: Düşük")).toBeInTheDocument();
  });

  it("beyan edilmemiş gereksinim 'Belirtilmemiş' olarak yazılır, bilinmeyen gerekçe genel metne düşer", () => {
    const minimum = { ...level("MINIMUM"), checks: [{ field: "GPU", status: "UNKNOWN", actual: "RTX 3060", required: null, reason: "SERVER_NEW_REASON" }] };
    renderResult({ minimum });
    const block = screen.getByText(a.level.MINIMUM).closest("section") ?? document.body;
    expect(within(block as HTMLElement).getByText("RTX 3060")).toBeInTheDocument();
    expect(within(block as HTMLElement).getAllByText(a.notDeclared)).toHaveLength(1);
    expect(within(block as HTMLElement).getByText(tr.Recommendation.reasons.UNKNOWN_REASON)).toBeInTheDocument();
  });
});
