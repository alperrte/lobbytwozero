"use client";

import { useEffect, useState } from "react";
import { socialApi } from "@/features/social";
import type { UserId } from "../types/messaging.types";

const FRIEND_PAGE = 50;
const MAX_PAGES = 4;

type FriendsState = { ids: UserId[]; loading: boolean; error?: unknown };

/**
 * Yeni sohbet / gruba üye ekleme seçicisi için arkadaş kimlikleri (social-service, en çok 200). Sohbet için arkadaşlık şartı
 * yoktur; seçici yalnız kolay erişim içindir (ilan sahibiyle ya da profilden açılan sohbet arkadaş gerektirmez).
 */
export function useFriendIds(enabled: boolean): FriendsState {
  const [state, setState] = useState<FriendsState>({ ids: [], loading: true });

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    (async () => {
      const ids: UserId[] = [];
      let cursor: string | undefined;
      for (let page = 0; page < MAX_PAGES; page += 1) {
        const result = await socialApi.listFriends({ limit: FRIEND_PAGE, cursor }, controller.signal);
        ids.push(...result.items.map((friend) => friend.friendId));
        if (!result.nextCursor) break;
        cursor = result.nextCursor;
      }
      return ids;
    })().then(
      (ids) => setState({ ids, loading: false }),
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ ids: [], loading: false, error });
      }
    );
    return () => controller.abort();
  }, [enabled]);

  return state;
}
