import { clampSize, optionalCode, PLATFORM_PATTERN } from "../schemas/filters";
import {
  parseAssessment,
  parseAssistantResponse,
  parseEventRecommendations,
  parseFeedback,
  parseHardwareCompatible,
  parsePlayerRecommendations,
  parseRun,
  parseRunPage
} from "../schemas/response-guards";
import type {
  AssessmentRequest,
  AssistantRequest,
  FeedbackValue,
  GenerateGamesRequest,
  PlayerFilters,
  RequirementPlatform
} from "../types/recommendation";
import { queryString, readJson, writeJson } from "./recommendation-http";

/**
 * Recommendation Service istemcisi (`/api/v1/recommendation/**`, `docs/api.md` → RECOMMENDATION).
 * İstek gövdeleri yalnız sözleşmedeki alanları taşır (bilinmeyen alan backend'de 400'dür); boş filtre hiç gönderilmez.
 */
const base = "/api/v1/recommendation";
const id = encodeURIComponent;

export const recommendationApi = {
  async generateGames(request: GenerateGamesRequest, signal?: AbortSignal) {
    const genre = optionalCode(request.genre);
    const platform = optionalCode(request.platform);
    const body = { size: clampSize("games", request.size), ...(genre ? { genre } : {}), ...(platform ? { platform } : {}) };
    return parseRun(await writeJson("POST", `${base}/games`, body, signal));
  },
  async listRuns(options: { size?: number; beforeRunId?: string } = {}, signal?: AbortSignal) {
    return parseRunPage(await readJson(`${base}/runs${queryString({ size: clampSize("runs", options.size), beforeRunId: options.beforeRunId })}`, signal));
  },
  async getRun(runId: string, signal?: AbortSignal) {
    return parseRun(await readJson(`${base}/runs/${id(runId)}`, signal));
  },
  async getFeedback(runId: string, itemId: string, signal?: AbortSignal) {
    return parseFeedback(await readJson(`${base}/runs/${id(runId)}/items/${id(itemId)}/feedback`, signal));
  },
  async putFeedback(runId: string, itemId: string, feedback: FeedbackValue, version: number, signal?: AbortSignal) {
    return parseFeedback(await writeJson("PUT", `${base}/runs/${id(runId)}/items/${id(itemId)}/feedback`, { feedback, version }, signal));
  },
  async hardwareCompatible(options: { profileId: string; platform: RequirementPlatform; genre?: string; size?: number }, signal?: AbortSignal) {
    const query = queryString({ profileId: options.profileId, platform: options.platform, genre: optionalCode(options.genre), size: clampSize("compatible", options.size) });
    return parseHardwareCompatible(await readJson(`${base}/games/hardware-compatible${query}`, signal));
  },
  async assessHardware(request: AssessmentRequest, signal?: AbortSignal) {
    const body: AssessmentRequest = {
      hardwareProfileId: request.hardwareProfileId,
      gameId: request.gameId,
      platform: optionalCode(request.platform, PLATFORM_PATTERN) ?? request.platform,
      ...(request.resolution ? { resolution: request.resolution } : {}),
      ...(request.graphicsPreset ? { graphicsPreset: request.graphicsPreset } : {}),
      ...(request.renderingMode ? { renderingMode: request.renderingMode } : {})
    };
    return parseAssessment(await writeJson("POST", `${base}/hardware/assessments`, body, signal));
  },
  async similarPlayers(filters: PlayerFilters, signal?: AbortSignal) {
    return parsePlayerRecommendations(await readJson(`${base}/players${peopleQuery(filters)}`, signal));
  },
  async friendSuggestions(filters: PlayerFilters, signal?: AbortSignal) {
    return parsePlayerRecommendations(await readJson(`${base}/friends${peopleQuery(filters)}`, signal));
  },
  async events(size: number, signal?: AbortSignal) {
    return parseEventRecommendations(await readJson(`${base}/events${queryString({ size: clampSize("events", size) })}`, signal));
  },
  async askAssistant(request: AssistantRequest, signal?: AbortSignal) {
    return parseAssistantResponse(await writeJson("POST", `${base}/assistant`, request, signal));
  }
};

function peopleQuery(filters: PlayerFilters): string {
  return queryString({ size: clampSize("people", filters.size), genre: optionalCode(filters.genre), platform: optionalCode(filters.platform) });
}
