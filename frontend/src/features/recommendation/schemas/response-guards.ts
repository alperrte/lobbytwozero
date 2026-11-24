import { ApiHttpError } from "@/shared/lib/api/client";
import {
  assistantIntents,
  assistantMissingFields,
  assistantStatuses,
  assistantSteps,
  assistantTopics,
  checkFields,
  checkStatuses,
  compatibilityReasons,
  compatibilityStatuses,
  feedbackValues,
  fpsConfidences,
  fpsStatuses,
  playerRecommendationKinds,
  requirementLevels,
  runStatuses,
  suitabilities,
  type AssistantResponse,
  type CompatibleGame,
  type EventRecommendations,
  type EventSuggestion,
  type FpsAssessment,
  type FpsEvidence,
  type HardwareAssessment,
  type HardwareCompatibleResult,
  type ItemFeedback,
  type LevelAssessment,
  type PlayerRecommendations,
  type RecommendationRun,
  type RecommendedItem,
  type RunPage
} from "../types/recommendation";

/**
 * Yanıt doğrulayıcıları (el yazımı; projede şema kütüphanesi yoktur). Beklenmeyen biçim UYDURMA varsayılanla
 * doldurulmaz: `INVALID_RESPONSE` kodlu hata fırlatılır ve arayüz bunu sunucu hatası olarak gösterir.
 * Açıklama/sınırlama kodları (serbest dizgi) korunur; arayüz bilinmeyen kodu nötr metinle gösterir.
 */
export const INVALID_RESPONSE = "INVALID_RESPONSE";

function invalid(): never {
  throw new ApiHttpError(502, { code: INVALID_RESPONSE });
}

type Json = Record<string, unknown>;

function obj(value: unknown): Json {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Json) : invalid();
}
function str(value: unknown): string {
  return typeof value === "string" ? value : invalid();
}
function optStr(value: unknown): string | null {
  return value === null || value === undefined ? null : str(value);
}
function int(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : invalid();
}
function optNum(value: unknown): number | null {
  return value === null || value === undefined ? null : int(value);
}
function bool(value: unknown): boolean {
  return typeof value === "boolean" ? value : invalid();
}
function arr<T>(value: unknown, item: (entry: unknown) => T): T[] {
  return Array.isArray(value) ? value.map(item) : invalid();
}
function strings(value: unknown): string[] {
  return value === undefined || value === null ? [] : arr(value, str);
}
function oneOf<T extends string>(allowed: readonly T[], value: unknown): T {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : invalid();
}

function parseItem(value: unknown): RecommendedItem {
  const r = obj(value);
  return {
    itemId: str(r.itemId),
    gameId: str(r.gameId),
    score: int(r.score),
    rank: int(r.rank),
    reasonCode: str(r.reasonCode),
    contribution: int(r.contribution),
    matchedGenres: strings(r.matchedGenres)
  };
}

export function parseRun(value: unknown): RecommendationRun {
  const r = obj(value);
  return {
    runId: str(r.runId),
    algorithmVersion: str(r.algorithmVersion),
    createdAt: str(r.createdAt),
    status: oneOf(runStatuses, r.status),
    genre: optStr(r.genre),
    platform: optStr(r.platform),
    items: arr(r.items, parseItem).sort((left, right) => left.rank - right.rank),
    limitations: strings(r.limitations)
  };
}

export function parseRunPage(value: unknown): RunPage {
  const r = obj(value);
  return {
    items: arr(r.items, (entry) => {
      const item = obj(entry);
      return { runId: str(item.runId), algorithmVersion: str(item.algorithmVersion), createdAt: str(item.createdAt), resultCount: int(item.resultCount) };
    }),
    nextCursor: optStr(r.nextCursor)
  };
}

export function parseFeedback(value: unknown): ItemFeedback {
  const r = obj(value);
  return {
    itemId: str(r.itemId),
    value: r.value === null || r.value === undefined ? null : oneOf(feedbackValues, r.value),
    version: int(r.version)
  };
}

function parseCompatibleGame(value: unknown): CompatibleGame {
  const r = obj(value);
  return {
    gameId: str(r.gameId),
    affinity: int(r.affinity),
    matchedGenres: strings(r.matchedGenres),
    compatibility: oneOf(compatibilityStatuses, r.compatibility),
    reason: oneOf(compatibilityReasons, r.reason)
  };
}

export function parseHardwareCompatible(value: unknown): HardwareCompatibleResult {
  const r = obj(value);
  return {
    algorithmVersion: str(r.algorithmVersion),
    createdAt: str(r.createdAt),
    hardwareProfileId: str(r.hardwareProfileId),
    platform: str(r.platform),
    items: arr(r.items, parseCompatibleGame),
    excludedBelowMinimum: int(r.excludedBelowMinimum),
    limitations: strings(r.limitations)
  };
}

function parseLevel(value: unknown): LevelAssessment {
  const r = obj(value);
  return {
    level: oneOf(requirementLevels, r.level),
    computeStatus: oneOf(checkStatuses, r.computeStatus),
    overallStatus: oneOf(checkStatuses, r.overallStatus),
    checks: arr(r.checks, (entry) => {
      const check = obj(entry);
      return {
        field: oneOf(checkFields, check.field),
        status: oneOf(checkStatuses, check.status),
        actual: optStr(check.actual),
        required: optStr(check.required),
        reason: str(check.reason)
      };
    })
  };
}

function parseEvidence(value: unknown): FpsEvidence | null {
  if (value === null || value === undefined) return null;
  const r = obj(value);
  return {
    contractVersion: optStr(r.contractVersion),
    source: optStr(r.source),
    contextVerification: optStr(r.contextVerification),
    method: optStr(r.method),
    renderingMode: optStr(r.renderingMode),
    consideredSessionCount: int(r.consideredSessionCount),
    inlierSampleCount: int(r.inlierSampleCount),
    excludedSampleCount: int(r.excludedSampleCount),
    firstMeasuredAt: optStr(r.firstMeasuredAt),
    windowLimited: bool(r.windowLimited),
    limitations: strings(r.limitations)
  };
}

function parseFps(value: unknown): FpsAssessment {
  const r = obj(value);
  const status = oneOf(fpsStatuses, r.status);
  const lower = optNum(r.estimatedLowerFps);
  const upper = optNum(r.estimatedUpperFps);
  // Tahmin durumu aralıksız gelirse "tahmin" diye gösterilecek bir şey yoktur: sözleşme ihlalidir.
  if (status === "ESTIMATED" && (lower === null || upper === null || lower > upper)) invalid();
  return {
    status,
    estimatedLowerFps: lower,
    estimatedUpperFps: upper,
    confidence: oneOf(fpsConfidences, r.confidence),
    observedSampleCount: int(r.observedSampleCount),
    observedSessionCount: int(r.observedSessionCount),
    producerSampleThresholdMet: bool(r.producerSampleThresholdMet),
    lastObservedAt: optStr(r.lastObservedAt),
    requestedResolution: optStr(r.requestedResolution),
    requestedPreset: optStr(r.requestedPreset),
    reason: str(r.reason),
    evidence: parseEvidence(r.evidence)
  };
}

export function parseAssessment(value: unknown): HardwareAssessment {
  const r = obj(value);
  const graphics = obj(r.graphics);
  const component = obj(r.componentCompatibility);
  return {
    hardwareProfileId: str(r.hardwareProfileId),
    hardwareProfileVersion: int(r.hardwareProfileVersion),
    gameId: str(r.gameId),
    platform: str(r.platform),
    assessedAt: str(r.assessedAt),
    suitability: oneOf(suitabilities, r.suitability),
    minimum: parseLevel(r.minimum),
    recommended: parseLevel(r.recommended),
    fps: parseFps(r.fps),
    graphics: {
      status: oneOf(["CONDITIONAL", "INSUFFICIENT_DATA"] as const, graphics.status),
      suggestedPreset: optStr(graphics.suggestedPreset),
      reason: str(graphics.reason)
    },
    upgrades: arr(r.upgrades, (entry) => {
      const upgrade = obj(entry);
      return { code: str(upgrade.code), field: str(upgrade.field), requirementLevel: str(upgrade.requirementLevel), required: optStr(upgrade.required), reason: str(upgrade.reason) };
    }),
    componentCompatibility: {
      status: oneOf(["INCOMPATIBLE", "UNKNOWN"] as const, component.status),
      checks: arr(component.checks, (entry) => {
        const check = obj(entry);
        return { field: str(check.field), status: oneOf(["DECLARED_MATCH", "INCOMPATIBLE", "UNKNOWN"] as const, check.status), reason: str(check.reason) };
      }),
      limitations: strings(component.limitations)
    },
    limitations: strings(r.limitations)
  };
}

export function parsePlayerRecommendations(value: unknown): PlayerRecommendations {
  const r = obj(value);
  return {
    kind: oneOf(playerRecommendationKinds, r.kind),
    algorithmVersion: str(r.algorithmVersion),
    createdAt: str(r.createdAt),
    items: arr(r.items, (entry) => {
      const item = obj(entry);
      return {
        userId: str(item.userId),
        score: int(item.score),
        sharedGenres: strings(item.sharedGenres),
        sharedPlatforms: strings(item.sharedPlatforms),
        sharedPlayerTypes: strings(item.sharedPlayerTypes)
      };
    }),
    limitations: strings(r.limitations)
  };
}

function parseEvent(value: unknown): EventSuggestion {
  const r = obj(value);
  return {
    eventId: str(r.eventId),
    type: str(r.type),
    title: str(r.title),
    startsAt: str(r.startsAt),
    endsAt: str(r.endsAt),
    gameId: optStr(r.gameId),
    score: int(r.score),
    sharedGenres: strings(r.sharedGenres),
    sharedPlatforms: strings(r.sharedPlatforms)
  };
}

export function parseEventRecommendations(value: unknown): EventRecommendations {
  const r = obj(value);
  return { algorithmVersion: str(r.algorithmVersion), createdAt: str(r.createdAt), items: arr(r.items, parseEvent), limitations: strings(r.limitations) };
}

export function parseAssistantResponse(value: unknown): AssistantResponse {
  const r = obj(value);
  return {
    contractVersion: str(r.contractVersion),
    algorithmVersion: str(r.algorithmVersion),
    topic: oneOf(assistantTopics, r.topic),
    intent: oneOf(assistantIntents, r.intent),
    locale: str(r.locale),
    status: oneOf(assistantStatuses, r.status),
    template: str(r.template),
    message: str(r.message),
    // Tanınmayan adım kodu sessizce atlanır (gezinme hedefi olmayan bir adım gösterilmez); eksik alanlar katıdır.
    steps: strings(r.steps).filter((step): step is AssistantResponse["steps"][number] => (assistantSteps as readonly string[]).includes(step)),
    missingFields: arr(r.missingFields ?? [], (entry) => oneOf(assistantMissingFields, entry))
  };
}
