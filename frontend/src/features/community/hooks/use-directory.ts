"use client";

import { useEffect, useMemo, useSyncExternalStore } from "react";
import { fetchAuthorCards, fetchGameCards } from "../services/community-api";
import { DELETED_ACCOUNT_ID, type AuthorCard, type GameCard } from "../types/community.types";

const CHUNK = 50;

/**
 * Kimlik → kart çözümleyici (yazar adı/avatarı, oyun adı). Community yanıtları yalnız kimlik taşır; görünen bilgi
 * User ve Game servislerinin toplu uçlarından gelir. Bellek içi önbellektir (depoya yazılmaz); bulunamayan kimlik
 * `null` olarak kalır ve arayüz dürüst bir yedek etiket gösterir. Hata sonrası aynı kimlik kümesi için yeniden denenmez.
 */
export type Directory<T> = {
  subscribe: (listener: () => void) => () => void;
  getSnapshot: () => number;
  get: (id: string) => T | null | undefined;
  ensure: (ids: readonly string[]) => void;
};

export function createDirectory<T>(load: (ids: string[]) => Promise<Map<string, T>>): Directory<T> {
  const cache = new Map<string, T | null>();
  const pending = new Set<string>();
  const listeners = new Set<() => void>();
  let version = 0;

  function publish() {
    version += 1;
    listeners.forEach((listener) => listener());
  }

  return {
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    getSnapshot: () => version,
    get: (id) => cache.get(id),
    ensure(ids) {
      const missing = [...new Set(ids)].filter((id) => !cache.has(id) && !pending.has(id));
      for (let start = 0; start < missing.length; start += CHUNK) {
        const chunk = missing.slice(start, start + CHUNK);
        chunk.forEach((id) => pending.add(id));
        load(chunk)
          .then((found) => {
            chunk.forEach((id) => cache.set(id, found.get(id) ?? null));
            publish();
          })
          .catch(() => {
            // Önbelleğe yazılmaz: bir sonraki kimlik kümesi değişiminde yeniden denenir.
          })
          .finally(() => chunk.forEach((id) => pending.delete(id)));
      }
    }
  };
}

const authorDirectory = createDirectory<AuthorCard>(async (ids) => {
  const cards = await fetchAuthorCards(ids);
  return new Map(cards.map((card) => [card.userId, card]));
});

const gameDirectory = createDirectory<GameCard>(async (ids) => {
  const cards = await fetchGameCards(ids);
  return new Map(cards.map((card) => [card.gameId, card]));
});

function useDirectory<T>(directory: Directory<T>, ids: ReadonlyArray<string | null | undefined>, enabled: boolean) {
  useSyncExternalStore(directory.subscribe, directory.getSnapshot, directory.getSnapshot);
  const key = useMemo(
    () => [...new Set(ids.filter((id): id is string => typeof id === "string" && id !== DELETED_ACCOUNT_ID))].sort().join(","),
    [ids]
  );
  useEffect(() => {
    if (enabled && key) directory.ensure(key.split(","));
  }, [directory, enabled, key]);
  return (id: string | null | undefined): T | null => (id ? (directory.get(id) ?? null) : null);
}

/** Yazar kartları. Profil toplu ucu oturum ister: misafirde istek atılmaz ve yedek etiket gösterilir. */
export function useAuthors(ids: ReadonlyArray<string | null | undefined>, authenticated: boolean) {
  return useDirectory(authorDirectory, ids, authenticated);
}

/** Oyun kartları (anonim uç). */
export function useGames(ids: ReadonlyArray<string | null | undefined>) {
  return useDirectory(gameDirectory, ids, true);
}
