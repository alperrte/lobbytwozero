"use client";

import { useEffect, useRef, useState } from "react";
import { isAbort } from "../services/community-errors";

type Page<T> = { items: T[]; nextCursor: string | null };

type ListState<T> = {
  key: string;
  items: T[];
  nextCursor: string | null;
  error?: unknown;
  loadingMore: boolean;
  moreError?: unknown;
};

/**
 * İmleçli (keyset) sayfalama: `key` değişince eski veri görünmez ve liste baştan yüklenir. Sonraki sayfa aynı filtre ve
 * `sort` ile istenir (imleç bunları taşımaz). Tekrarlayan kimlikler elenir; eşzamanlı "daha fazla" istekleri tek uçuştur.
 */
export function usePagedList<T extends { id: string }>(
  key: string | null,
  fetchPage: (cursor: string | null, signal: AbortSignal) => Promise<Page<T>>
) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<ListState<T>>();
  const moreController = useRef<AbortController | null>(null);
  // Son `fetchPage` (filtreler dahil) yalnız efekt ve olay işleyicilerinde okunur; anahtar değişince efekt yeniden çalışır.
  const fetchRef = useRef(fetchPage);
  useEffect(() => {
    fetchRef.current = fetchPage;
  });
  const fullKey = key === null ? null : `${key}#${revision}`;

  useEffect(() => {
    if (fullKey === null) return;
    const controller = new AbortController();
    let active = true;
    fetchRef.current(null, controller.signal).then(
      (page) => active && setState({ key: fullKey, items: page.items, nextCursor: page.nextCursor, loadingMore: false }),
      (error: unknown) => {
        if (active && !isAbort(error)) setState({ key: fullKey, items: [], nextCursor: null, error, loadingMore: false });
      }
    );
    return () => {
      active = false;
      controller.abort();
      moreController.current?.abort();
    };
  }, [fullKey]);

  const current = fullKey !== null && state?.key === fullKey ? state : undefined;

  function loadMore() {
    if (!current || current.loadingMore || current.nextCursor === null) return;
    const cursor = current.nextCursor;
    const controller = new AbortController();
    moreController.current = controller;
    setState({ ...current, loadingMore: true, moreError: undefined });
    fetchRef.current(cursor, controller.signal).then(
      (page) =>
        setState((previous) => {
          if (!previous || previous.key !== fullKey) return previous;
          const known = new Set(previous.items.map((item) => item.id));
          return {
            ...previous,
            items: [...previous.items, ...page.items.filter((item) => !known.has(item.id))],
            nextCursor: page.nextCursor,
            loadingMore: false
          };
        }),
      (error: unknown) => {
        if (isAbort(error)) return;
        setState((previous) => (previous && previous.key === fullKey ? { ...previous, loadingMore: false, moreError: error } : previous));
      }
    );
  }

  function update(id: string, change: (item: T) => T) {
    setState((previous) =>
      previous && previous.key === fullKey
        ? { ...previous, items: previous.items.map((item) => (item.id === id ? change(item) : item)) }
        : previous
    );
  }

  function remove(id: string) {
    setState((previous) =>
      previous && previous.key === fullKey ? { ...previous, items: previous.items.filter((item) => item.id !== id) } : previous
    );
  }

  function prepend(item: T) {
    setState((previous) =>
      previous && previous.key === fullKey && !previous.items.some((existing) => existing.id === item.id)
        ? { ...previous, items: [item, ...previous.items] }
        : previous
    );
  }

  function append(item: T) {
    setState((previous) =>
      previous && previous.key === fullKey && !previous.items.some((existing) => existing.id === item.id)
        ? { ...previous, items: [...previous.items, item] }
        : previous
    );
  }

  return {
    items: current?.items ?? [],
    loading: !current,
    error: current?.error,
    hasMore: current?.nextCursor != null,
    loadingMore: current?.loadingMore ?? false,
    moreError: current?.moreError,
    loadMore,
    reload: () => setRevision((value) => value + 1),
    update,
    remove,
    prepend,
    append
  };
}
