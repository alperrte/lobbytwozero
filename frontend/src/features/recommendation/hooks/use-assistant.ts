"use client";

import { useState } from "react";
import { recommendationApi } from "../services/recommendation-api";
import type { AssistantRequest, AssistantResponse, AssistantTask } from "../types/recommendation";
import { useRecommendationMutation } from "./use-recommendation-mutation";

export type AssistantInputs = { genre?: string; hardwareProfileId?: string; gameId?: string; platform?: string };

/** Backend'in kabul ettiği dört konu/amaç kombinasyonu ve her birinin taşıyabileceği alanlar (fazla alan 400'dür). */
const TASKS: Record<AssistantTask, { topic: AssistantRequest["topic"]; intent: AssistantRequest["intent"]; fields: (keyof AssistantInputs)[] }> = {
  SELECT_GAME: { topic: "GAME", intent: "SELECT_GAME", fields: ["genre"] },
  ASSESS_GAME: { topic: "HARDWARE", intent: "ASSESS_GAME", fields: ["hardwareProfileId", "gameId", "platform"] },
  VIEW_HISTORY: { topic: "LTZ_USAGE", intent: "VIEW_HISTORY", fields: [] },
  GIVE_FEEDBACK: { topic: "LTZ_USAGE", intent: "GIVE_FEEDBACK", fields: [] }
};

export function taskFields(task: AssistantTask): (keyof AssistantInputs)[] {
  return TASKS[task].fields;
}

export function buildAssistantRequest(task: AssistantTask, locale: string, inputs: AssistantInputs): AssistantRequest {
  const definition = TASKS[task];
  const request: AssistantRequest = { contractVersion: "assistant-v1", topic: definition.topic, intent: definition.intent, locale: locale === "en" ? "en" : "tr" };
  for (const field of definition.fields) {
    const value = inputs[field]?.trim();
    if (value) request[field] = value;
  }
  return request;
}

/** LTZ Rehber: kural tabanlı yönlendirme (serbest metin yok). Yanıt `steps`/`missingFields` kodlarıyla arayüzü yönetir. */
export function useAssistant() {
  const mutation = useRecommendationMutation();
  const [response, setResponse] = useState<{ task: AssistantTask; value: AssistantResponse }>();
  async function ask(task: AssistantTask, locale: string, inputs: AssistantInputs) {
    const outcome = await mutation.run(() => recommendationApi.askAssistant(buildAssistantRequest(task, locale, inputs)));
    if (outcome?.ok) setResponse({ task, value: outcome.value });
  }
  return { ...mutation, response, ask, reset: () => setResponse(undefined) };
}
