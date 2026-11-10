"use client";

import type { ReactNode } from "react";
import { PlayerLookupContext, type PlayerLookup } from "@/shared/providers/player-lookup-context";
import { normalizeSearch, searchPlayers } from "../services/players-api";

const lookup: PlayerLookup = {
  normalize: normalizeSearch,
  async search(term, signal) {
    const cards = await searchPlayers(term, signal);
    return cards.map((card) => ({
      userId: card.userId,
      name: card.displayName ?? card.username ?? "",
      username: card.username
    }));
  }
};

/** Navbar'ın `@` oyuncu aramasına `searchPlayers`'ı bağlar. Oturum yoksa sunucu 401 verir; navbar bunu giriş mesajına çevirir. */
export function PlayerLookupProvider({ children }: { children: ReactNode }) {
  return <PlayerLookupContext.Provider value={lookup}>{children}</PlayerLookupContext.Provider>;
}
