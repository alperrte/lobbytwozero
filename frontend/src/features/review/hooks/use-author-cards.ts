"use client";

import { isReviewUuid } from "../schemas/review-schema";
import type { AuthorCard } from "../types/review";
import { useReviewResource } from "./use-review-resource";

/**
 * Sayfadaki yazarların User kartlarını TEK istekle çözer (en çok 50 benzersiz kimlik). `resolved` yanıt (ya da yazarsız
 * düşüş) netleşince true olur; o zamana kadar arayüz yazar satırını boş bırakır. Kartı olmayan yazar için sonuç haritasında
 * giriş yoktur: arayüz "bir oyuncu" gösterir. Kimlikler sıralanır, böylece aynı küme yeniden istek üretmez.
 */
export function useAuthorCards(authorIds: readonly string[]) {
  const userIds = [...new Set(authorIds.filter(isReviewUuid).map((id) => id.toLowerCase()))].sort().slice(0, 50);
  const result = useReviewResource<AuthorCard[]>(userIds.length > 0 ? { kind: "authorCards", userIds } : null);
  const cards = new Map<string, AuthorCard>((result.data ?? []).map((card) => [card.userId.toLowerCase(), card]));
  return { cards, resolved: userIds.length === 0 || result.data !== undefined || result.error !== undefined };
}
