"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { fetchProfileCards } from "@/features/social";
import { coverOf } from "@/features/game/components/game-cover";
import { apiUrl } from "@/shared/lib/api/client";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { fetchGameCards, lfpApi } from "../services/lfp-api";
import { lfpErrorKey } from "../services/lfp-errors";
import type { GameVisual, Listing, ListingFilters, ListingStatus, Owner } from "../types/lfp.types";

const PAGE_SIZE = 12;

type PagedState = { key: string; items: Listing[]; nextCursor: string | null; error?: unknown; loadingMore: boolean; moreError?: unknown };

/**
 * Aktif ilan araması (LFP-10, LFP-11). Süzgeç değişince baştan okunur; "daha fazla" imleçle devam eder. Sunucu engelli sahiplerin
 * ilanlarını atladığı için sayfa kısa (hatta boş) olabilir; imleç doluysa devamı vardır.
 */
export function useListingSearch(filters: ListingFilters) {
  const key = JSON.stringify(filters);
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<PagedState | null>(null);
  const requestKey = `${key}:${retry}`;

  useEffect(() => {
    const controller = new AbortController();
    lfpApi.search(JSON.parse(key) as ListingFilters, { limit: PAGE_SIZE }, controller.signal).then(
      (page) => setState({ key: requestKey, items: page.items, nextCursor: page.nextCursor, loadingMore: false }),
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ key: requestKey, items: [], nextCursor: null, error, loadingMore: false });
      }
    );
    return () => controller.abort();
  }, [key, requestKey]);

  const current = state?.key === requestKey ? state : null;

  const loadMore = useCallback(() => {
    if (!current?.nextCursor || current.loadingMore) return;
    const cursor = current.nextCursor;
    setState((previous) => (previous ? { ...previous, loadingMore: true, moreError: undefined } : previous));
    lfpApi.search(JSON.parse(key) as ListingFilters, { limit: PAGE_SIZE, cursor }).then(
      (page) =>
        setState((previous) =>
          previous?.key === requestKey
            ? { ...previous, items: [...previous.items, ...page.items.filter((item) => !previous.items.some((old) => old.id === item.id))], nextCursor: page.nextCursor, loadingMore: false }
            : previous
        ),
      (moreError: unknown) => setState((previous) => (previous?.key === requestKey ? { ...previous, loadingMore: false, moreError } : previous))
    );
  }, [current, key, requestKey]);

  return {
    items: current?.items ?? [],
    loading: current === null,
    error: current?.error,
    hasMore: Boolean(current?.nextCursor),
    loadingMore: current?.loadingMore ?? false,
    moreError: current?.moreError,
    loadMore,
    reload: () => setRetry((value) => value + 1),
    /** Kapatılan ya da iletişimde "artık açık değil" çıkan ilanı listeden düşürür. */
    remove: (listingId: string) =>
      setState((previous) => (previous ? { ...previous, items: previous.items.filter((item) => item.id !== listingId) } : previous))
  };
}

const MINE_PAGE = 50;
const MINE_MAX_PAGES = 4;

/** Kendi ilanlarım (tüm durumlar; sayaçlar yerelde hesaplanır — serviste sayım ucu yok). En çok 200 ilan okunur. */
export function useMyListings() {
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<{ retry: number; items: Listing[]; truncated: boolean; error?: unknown } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      const items: Listing[] = [];
      let cursor: string | null = null;
      for (let page = 0; page < MINE_MAX_PAGES; page += 1) {
        const result = await lfpApi.mine({ limit: MINE_PAGE, cursor }, controller.signal);
        items.push(...result.items);
        cursor = result.nextCursor;
        if (!cursor) break;
      }
      return { items, truncated: cursor !== null };
    })().then(
      (result) => setState({ retry, ...result }),
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ retry, items: [], truncated: false, error });
      }
    );
    return () => controller.abort();
  }, [retry]);

  const current = state?.retry === retry ? state : null;
  const items = useMemo(() => current?.items ?? [], [current]);
  const counts = useMemo(() => {
    const result: Record<ListingStatus, number> = { OPEN: 0, CLOSED: 0, EXPIRED: 0 };
    for (const item of items) result[item.status] += 1;
    return result;
  }, [items]);

  return {
    items,
    counts,
    truncated: current?.truncated ?? false,
    loading: current === null,
    error: current?.error,
    reload: () => setRetry((value) => value + 1),
    replace: (listing: Listing) =>
      setState((previous) => (previous ? { ...previous, items: previous.items.map((item) => (item.id === listing.id ? listing : item)) } : previous))
  };
}

/**
 * Kartlardaki sahip adı/avatarı (user-service toplu kart, 50/istek) ve oyun kapağı (game-service toplu kart). Yardımcı
 * bilgidir: hata olursa ilanlar yine görünür, yedek ad ve nötr kapak kullanılır.
 */
export function useListingVisuals(listings: readonly Listing[]) {
  const t = useTranslations("Lfp.card");
  const ownerKey = [...new Set(listings.map((item) => item.ownerId))].sort().join(",");
  const gameKey = [...new Set(listings.map((item) => item.gameId))].sort().join(",");
  const [owners, setOwners] = useState<Map<string, { username: string | null; displayName: string | null; avatarPath: string | null }>>(new Map());
  const [covers, setCovers] = useState<Map<string, string | null>>(new Map());

  useEffect(() => {
    if (!ownerKey) return;
    const controller = new AbortController();
    fetchProfileCards(ownerKey.split(","), controller.signal).then(
      (cards) => setOwners((previous) => new Map([...previous, ...cards])),
      () => undefined
    );
    return () => controller.abort();
  }, [ownerKey]);

  useEffect(() => {
    if (!gameKey) return;
    const controller = new AbortController();
    fetchGameCards(gameKey.split(","), controller.signal).then(
      (result) => setCovers((previous) => new Map([...previous, ...result.items.map((card) => [card.gameId, coverOf(card)] as const)])),
      () => undefined
    );
    return () => controller.abort();
  }, [gameKey]);

  const owner = useCallback(
    (userId: string): Owner => {
      const card = owners.get(userId);
      return {
        userId,
        name: card?.displayName ?? card?.username ?? `${t("player")} #${userId.slice(-6)}`,
        handle: card?.username ? `@${card.username}` : null,
        avatarUrl: card?.avatarPath ? apiUrl(card.avatarPath as `/${string}`) : null
      };
    },
    [owners, t]
  );
  const game = useCallback((listing: Listing): GameVisual => ({ title: listing.gameTitle, coverUrl: covers.get(listing.gameId) ?? null }), [covers]);
  return { owner, game };
}

/**
 * İlan sahibiyle iletişim (LFP-16): LFP izni verir ve alıcıyı döner; sohbet messaging'de açılır (`/mesajlar?to=`). LFP ikinci
 * bir mesajlaşma altyapısı kurmaz. Engel ve kapalı ilan sunucuda denetlenir.
 */
export function useContactOwner() {
  const router = useRouter();
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<{ listingId: string; key: string } | null>(null);

  const contact = useCallback(
    async (listingId: string) => {
      setPending(listingId);
      setError(null);
      try {
        const result = await lfpApi.contact(listingId);
        router.push({ pathname: "/messages", query: { to: result.recipientId } });
      } catch (cause) {
        setError({ listingId, key: lfpErrorKey(cause) });
        setPending(null);
      }
    },
    [router]
  );
  return { contact, pending, error, clearError: () => setError(null) };
}
