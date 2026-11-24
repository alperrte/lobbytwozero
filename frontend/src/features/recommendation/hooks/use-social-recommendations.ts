"use client";

import { useState } from "react";
import { enrichmentApi } from "../services/enrichment-api";
import { recommendationApi } from "../services/recommendation-api";
import { errorCode, errorStatus } from "../services/recommendation-errors";
import type { PlayerFilters, PlayerRecommendationKind } from "../types/recommendation";
import { useRecommendationMutation } from "./use-recommendation-mutation";
import { useRecommendationQuery } from "./use-recommendation-query";

/** Benzer oyuncular ve arkadaş önerileri ayrı kavramlardır (ayrı uç, ayrı açıklama); filtre anahtarı önbelleği ayırır. */
export function usePlayerSuggestions(kind: PlayerRecommendationKind, filters: PlayerFilters) {
  const key = `people:${kind}:${filters.size}:${filters.genre ?? ""}:${filters.platform ?? ""}`;
  return useRecommendationQuery(key, (signal) => (kind === "FRIEND_SUGGESTIONS" ? recommendationApi.friendSuggestions(filters, signal) : recommendationApi.similarPlayers(filters, signal)), { cache: true });
}

export function useEventSuggestions(size: number) {
  return useRecommendationQuery(`events:${size}`, (signal) => recommendationApi.events(size, signal), { cache: true });
}

/**
 * Arkadaşlık isteği durumu. Social Service'te "arkadaşlık durumu" sorgu ucu olmadığından durum, isteğin sonucundan
 * çıkarılır: 201 → gönderildi; 409 kodları → zaten arkadaş / bekleyen istek / gelen istek var / engelli.
 */
export type FriendRequestOutcome = "SENT" | "ALREADY_FRIENDS" | "FRIEND_REQUEST_ALREADY_PENDING" | "INCOMING_FRIEND_REQUEST_PENDING" | "USER_BLOCKED";
const KNOWN_CONFLICTS = new Set<string>(["ALREADY_FRIENDS", "FRIEND_REQUEST_ALREADY_PENDING", "INCOMING_FRIEND_REQUEST_PENDING", "USER_BLOCKED"]);

export function useFriendRequest(receiverId: string) {
  const mutation = useRecommendationMutation();
  const [outcome, setOutcome] = useState<FriendRequestOutcome>();
  async function send() {
    const result = await mutation.run(() => enrichmentApi.sendFriendRequest(receiverId));
    if (!result) return;
    if (result.ok) setOutcome("SENT");
    else if (errorStatus(result.error) === 409 && KNOWN_CONFLICTS.has(errorCode(result.error) ?? "")) {
      setOutcome(errorCode(result.error) as FriendRequestOutcome);
      mutation.clearError();
    }
  }
  return { ...mutation, outcome, send };
}

/** Etkinliğe katılım. 409 `ALREADY_JOINED` başarı sayılır (kullanıcı zaten katılımcı); diğer 409'lar neden olarak gösterilir. */
export type JoinOutcome = "JOINED" | "ALREADY_JOINED" | "EVENT_FULL" | "EVENT_NOT_OPEN" | "ORGANIZER_CANNOT_JOIN";
const JOIN_CONFLICTS = new Set<string>(["ALREADY_JOINED", "EVENT_FULL", "EVENT_NOT_OPEN", "ORGANIZER_CANNOT_JOIN"]);

export function useJoinEvent(eventId: string) {
  const mutation = useRecommendationMutation();
  const [outcome, setOutcome] = useState<JoinOutcome>();
  async function join() {
    const result = await mutation.run(() => enrichmentApi.joinEvent(eventId));
    if (!result) return;
    if (result.ok) setOutcome("JOINED");
    else if (errorStatus(result.error) === 409 && JOIN_CONFLICTS.has(errorCode(result.error) ?? "")) {
      setOutcome(errorCode(result.error) as JoinOutcome);
      mutation.clearError();
    }
  }
  return { ...mutation, outcome, join };
}
