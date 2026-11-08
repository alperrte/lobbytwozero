"use client";

import { useEffect, useRef, useState } from "react";
import type { CursorPage, PageQuery } from "../types/social.types";
import { useSocialRevision } from "./social-revision";

/** Sunucunun izin verdiği en büyük sayfa (social-service `max-page-size`). */
const MAX_PAGE_SIZE = 50;

type ListState<T> = {
  key: string;
  items: T[];
  nextCursor: string | null;
  error?: unknown;
};

export type PagedList<T> = {
  items: T[];
  hasMore: boolean;
  /** İlk yükleme sürüyor (henüz hiç veri yok). */
  loading: boolean;
  error: unknown;
  loadingMore: boolean;
  loadMoreError: unknown;
  loadMore: () => void;
  retry: () => void;
};

/**
 * İmleçli liste (`items` + `nextCursor`). "Daha fazla yükle" sonraki sayfayı ekler. Sosyal sürüm değişince (ör. bir istek kabul
 * edildi) liste baştan, o ana kadar yüklenen kadar öğeyle (en çok 50) yeniden okunur; böylece kullanıcının kaydırdığı yer kaybolmaz.
 */
export function usePagedSocialList<T>(
  fetchPage: (query: PageQuery, signal: AbortSignal) => Promise<CursorPage<T>>,
  pageSize: number
): PagedList<T> {
  const revision = useSocialRevision();
  const [retryCount, setRetryCount] = useState(0);
  const [state, setState] = useState<ListState<T>>();
  const [more, setMore] = useState<{ loading: boolean; error?: unknown }>({ loading: false });
  const loadedCount = useRef(0);
  const key = `${revision}:${retryCount}`;

  useEffect(() => {
    const controller = new AbortController();
    const limit = Math.min(Math.max(loadedCount.current, pageSize), MAX_PAGE_SIZE);
    fetchPage({ limit }, controller.signal).then(
      (page) => {
        loadedCount.current = page.items.length;
        setState({ key, items: page.items, nextCursor: page.nextCursor });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setState((previous) => ({
            key,
            items: previous?.items ?? [],
            nextCursor: previous?.nextCursor ?? null,
            error
          }));
        }
      }
    );
    return () => controller.abort();
  }, [key, fetchPage, pageSize]);

  function loadMore() {
    const cursor = state?.nextCursor;
    if (!cursor || more.loading) {
      return;
    }
    setMore({ loading: true });
    const controller = new AbortController();
    fetchPage({ limit: pageSize, cursor }, controller.signal).then(
      (page) => {
        setState((previous) => {
          const items = [...(previous?.items ?? []), ...page.items];
          loadedCount.current = items.length;
          return { key: previous?.key ?? key, items, nextCursor: page.nextCursor };
        });
        setMore({ loading: false });
      },
      (error: unknown) => setMore({ loading: false, error })
    );
  }

  return {
    items: state?.items ?? [],
    hasMore: Boolean(state?.nextCursor),
    loading: state === undefined,
    error: state?.error,
    loadingMore: more.loading,
    loadMoreError: more.error,
    loadMore,
    retry: () => setRetryCount((value) => value + 1)
  };
}
