import { describe, expect, it } from "vitest";
import { parseAssessment, parseAssistantResponse, parseFeedback, parseHardwareCompatible, parsePlayerRecommendations, parseRun, parseRunPage } from "./response-guards";

const item = (rank: number) => ({ itemId: `i${rank}`, gameId: `g${rank}`, score: 100 - rank, rank, reasonCode: "GENRE_RATING_AFFINITY", contribution: 100 - rank, matchedGenres: ["rpg"] });
const run = { runId: "r", algorithmVersion: "v", createdAt: "t", status: "PARTIAL", genre: null, platform: "pc", items: [item(2), item(1)], limitations: [] };

const level = (name: string) => ({ level: name, computeStatus: "UNKNOWN", overallStatus: "UNKNOWN", checks: [{ field: "CPU", status: "MEETS", actual: "8C", required: "4C", reason: "DECLARED" }] });
const fps = { status: "INSUFFICIENT_DATA", estimatedLowerFps: null, estimatedUpperFps: null, confidence: "NONE", observedSampleCount: 0, observedSessionCount: 0, producerSampleThresholdMet: false, lastObservedAt: null, requestedResolution: null, requestedPreset: null, reason: "NO_SESSIONS", evidence: null };
const assessment = {
  hardwareProfileId: "p", hardwareProfileVersion: 2, gameId: "g", platform: "windows", assessedAt: "t", suitability: "UNKNOWN",
  minimum: level("MINIMUM"), recommended: level("RECOMMENDED"), fps,
  graphics: { status: "INSUFFICIENT_DATA", suggestedPreset: null, reason: "NO_FPS" },
  upgrades: [], componentCompatibility: { status: "UNKNOWN", checks: [], limitations: [] }, limitations: []
};

const expectInvalid = (action: () => unknown) => expect(action).toThrow(expect.objectContaining({ status: 502, code: "INVALID_RESPONSE" }));

describe("yanıt doğrulayıcıları", () => {
  it("run öğelerini sıraya göre dizer ve eksik listeleri boş kabul eder", () => {
    const parsed = parseRun({ ...run, limitations: undefined });
    expect(parsed.items.map((entry) => entry.rank)).toEqual([1, 2]);
    expect(parsed.limitations).toEqual([]);
  });

  it.each([
    ["bilinmeyen durum", { ...run, status: "DONE" }],
    ["sayı olmayan skor", { ...run, items: [{ ...item(1), score: "9" }] }],
    ["eksik runId", { ...run, runId: undefined }],
    ["dizi yerine nesne", { ...run, items: {} }]
  ])("%s → INVALID_RESPONSE", (_name, body) => expectInvalid(() => parseRun(body)));

  it("geçmiş sayfası ve geri bildirim", () => {
    expect(parseRunPage({ items: [{ runId: "r", algorithmVersion: "v", createdAt: "t", resultCount: 3 }], nextCursor: "c" }).nextCursor).toBe("c");
    expect(parseFeedback({ itemId: "i", value: null, version: 0 })).toEqual({ itemId: "i", value: null, version: 0 });
    expectInvalid(() => parseFeedback({ itemId: "i", value: "DISLIKE", version: 1 }));
  });

  it("uyumluluk durumu yalnız sözleşmedeki değerlerdir", () => {
    const body = { algorithmVersion: "v", createdAt: "t", hardwareProfileId: "p", platform: "windows", excludedBelowMinimum: 2, limitations: [], items: [{ gameId: "g", affinity: 5, matchedGenres: [], compatibility: "UNKNOWN", reason: "REQUIREMENTS_MISSING" }] };
    expect(parseHardwareCompatible(body).items[0].compatibility).toBe("UNKNOWN");
    expectInvalid(() => parseHardwareCompatible({ ...body, items: [{ ...body.items[0], compatibility: "COMPATIBLE" }] }));
  });

  it("değerlendirme: INSUFFICIENT_DATA aralıksız geçerlidir, ESTIMATED aralıksız geçersizdir", () => {
    expect(parseAssessment(assessment).fps.status).toBe("INSUFFICIENT_DATA");
    expectInvalid(() => parseAssessment({ ...assessment, fps: { ...fps, status: "ESTIMATED", confidence: "LOW" } }));
    expectInvalid(() => parseAssessment({ ...assessment, fps: { ...fps, status: "ESTIMATED", confidence: "LOW", estimatedLowerFps: 90, estimatedUpperFps: 60 } }));
    const estimated = parseAssessment({ ...assessment, fps: { ...fps, status: "ESTIMATED", confidence: "LOW", estimatedLowerFps: 55, estimatedUpperFps: 70 } });
    expect(estimated.fps).toMatchObject({ estimatedLowerFps: 55, estimatedUpperFps: 70 });
  });

  it("oyuncu önerisi türü doğrulanır", () => {
    const body = { kind: "SIMILAR_PLAYERS", algorithmVersion: "v", createdAt: "t", items: [{ userId: "u", score: 3, sharedGenres: ["rpg"] }], limitations: ["VIEWER_PREFERENCES_MISSING"] };
    expect(parsePlayerRecommendations(body).items[0]).toEqual({ userId: "u", score: 3, sharedGenres: ["rpg"], sharedPlatforms: [], sharedPlayerTypes: [] });
    expectInvalid(() => parsePlayerRecommendations({ ...body, kind: "ENEMIES" }));
  });

  it("asistan: bilinmeyen adım atlanır, bilinmeyen eksik alan reddedilir", () => {
    const body = { contractVersion: "assistant-v1", algorithmVersion: "v", topic: "GAME", intent: "SELECT_GAME", locale: "tr", status: "NEEDS_INPUT", template: "t", message: "m", steps: ["OPEN_GAME_DISCOVERY", "LAUNCH_ROCKET"], missingFields: ["GENRE"] };
    const parsed = parseAssistantResponse(body);
    expect(parsed.steps).toEqual(["OPEN_GAME_DISCOVERY"]);
    expect(parsed.missingFields).toEqual(["GENRE"]);
    expectInvalid(() => parseAssistantResponse({ ...body, missingFields: ["PASSWORD"] }));
  });
});
