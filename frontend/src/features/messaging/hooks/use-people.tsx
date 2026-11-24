"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { fetchProfileCards, type ProfileCard } from "@/features/social";
import { apiUrl } from "@/shared/lib/api/client";
import { DELETED_ACCOUNT_ID, type UserId } from "../types/messaging.types";

/**
 * Kişi adı/avatar önbelleği. Mesajlaşma kayıtları yalnız kimlik taşır; ad ve avatar user-service toplu kartından
 * (`POST /api/v1/user/profiles/batch`, en çok 50/istek) bir kez alınır. Kart dönmeyen kişi (silinmiş/yasaklı hesap ya da
 * aranızda engel) yedek adla gösterilir; anonim gönderen "Silinmiş hesap" olur.
 */
type PeopleContextValue = {
  cards: ReadonlyMap<UserId, ProfileCard | null>;
  ensure: (ids: readonly UserId[]) => void;
};

const PeopleContext = createContext<PeopleContextValue | null>(null);

const BATCH_DELAY_MS = 20;

export function PeopleProvider({ children }: { children: ReactNode }) {
  const [cards, setCards] = useState<ReadonlyMap<UserId, ProfileCard | null>>(new Map());
  const requested = useRef(new Set<UserId>());
  const queue = useRef(new Set<UserId>());
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flush = useCallback(() => {
    timer.current = null;
    const ids = [...queue.current];
    queue.current.clear();
    if (ids.length === 0) return;
    void fetchProfileCards(ids).then((found) => {
      setCards((previous) => {
        const next = new Map(previous);
        for (const userId of ids) next.set(userId, found.get(userId) ?? null);
        return next;
      });
    });
  }, []);

  const ensure = useCallback(
    (ids: readonly UserId[]) => {
      for (const userId of ids) {
        if (!userId || userId === DELETED_ACCOUNT_ID || requested.current.has(userId)) continue;
        requested.current.add(userId);
        queue.current.add(userId);
      }
      if (queue.current.size > 0 && !timer.current) {
        timer.current = setTimeout(flush, BATCH_DELAY_MS);
      }
    },
    [flush]
  );

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    []
  );

  const value = useMemo(() => ({ cards, ensure }), [cards, ensure]);
  return <PeopleContext.Provider value={value}>{children}</PeopleContext.Provider>;
}

export type PersonView = {
  userId: UserId;
  name: string;
  handle: string | null;
  avatarUrl: string | null;
  deleted: boolean;
};

/** Kimlikleri çözer (eksikleri arka planda ister) ve görünen adı döndüren fonksiyonu verir. */
export function usePeople(ids: readonly UserId[] = []): (userId: UserId) => PersonView {
  const context = useContext(PeopleContext);
  if (!context) throw new Error("usePeople yalnız PeopleProvider içinde kullanılabilir.");
  const t = useTranslations("Messaging.person");
  const { cards, ensure } = context;
  const key = ids.join(",");

  useEffect(() => {
    if (key) ensure(key.split(","));
  }, [key, ensure]);

  return useCallback(
    (userId: UserId): PersonView => {
      if (userId === DELETED_ACCOUNT_ID) {
        return { userId, name: t("deletedAccount"), handle: null, avatarUrl: null, deleted: true };
      }
      const card = cards.get(userId);
      const fallback = `${t("unknown")} #${userId.slice(-6)}`;
      return {
        userId,
        name: card?.displayName ?? card?.username ?? fallback,
        handle: card?.username ? `@${card.username}` : null,
        avatarUrl: card?.avatarPath ? apiUrl(card.avatarPath as `/${string}`) : null,
        deleted: false
      };
    },
    [cards, t]
  );
}
