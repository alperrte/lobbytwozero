"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { fetchPostPage } from "../services/community-api";
import { isAbort } from "../services/community-errors";
import { belongsToView, type MyPostsView } from "../services/my-posts-query";
import { invalidateMyPosts, loadSnapshot, rememberScroll, saveSnapshot } from "../services/my-posts-snapshot";
import { buildPostQuery } from "../services/post-query";
import type { PostFilters, PostView } from "../types/community.types";

/** Her istek en çok bu kadar kayıt getirir; görünüme uyan kayıt bu sayıya ulaşana dek imleç taranır. */
const PAGE_LIMIT = 24;
const TARGET_VISIBLE = 12;
/** Tek "yükle" eyleminde en çok bu kadar istek atılır; sonrasında kullanıcı aramaya kendisi devam eder. */
const MAX_REQUESTS = 20;

type State = { key: string; items: PostView[]; nextCursor: string | null; error?: unknown; loadingMore: boolean; moreError?: unknown };

/**
 * Kendi gönderilerim / yeniden paylaşımlarım listesi. API `GET /users/{id}/posts` gönderi ile yeniden paylaşımı tek
 * karma imleçli listede döndürür ve bir tür süzgeci ya da toplam sayı sunmaz. Bu yüzden görünüme ait kayıtlar, imleç
 * ZİNCİRİ sırayla taranarak toplanır: görünüme uymayanlar atlanır, yeterli kayıt birikene ya da liste bitene dek sonraki
 * sayfa istenir. `hasMore` yalnız sunucu imleci sürdüğü sürece doğrudur; boş sonuç ancak liste bittiğinde "boş" sayılır.
 * Toplam sayı hiçbir yerde uydurulmaz.
 *
 * Detaydan dönüşte yüklenmiş liste ve kaydırma konumu bellek içi anlık görüntüden gelir (`my-posts-snapshot`).
 */
export function useMyPosts({ userId, view, filters }: { userId: string | null; view: MyPostsView; filters: PostFilters }) {
  const key = userId ? `${userId}|${view}|${buildPostQuery(filters)}` : null;
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<State>();
  const moreController = useRef<AbortController | null>(null);

  // Son süzgeç/görünüm değerleri yalnız efekt ve olay işleyicilerinde okunur.
  const latest = useRef({ userId, view, filters });
  useEffect(() => {
    latest.current = { userId, view, filters };
  });

  const fullKey = key === null ? null : `${key}#${revision}`;
  const snapshot = key === null ? undefined : loadSnapshot(key);
  const current = useMemo<State | undefined>(() => {
    if (key === null) return undefined;
    if (state?.key === fullKey) return state;
    return snapshot
      ? { key: fullKey as string, items: snapshot.items, nextCursor: snapshot.nextCursor, loadingMore: false }
      : undefined;
  }, [key, fullKey, state, snapshot]);

  async function collect(cursor: string | null, signal: AbortSignal): Promise<{ items: PostView[]; nextCursor: string | null }> {
    const { userId: id, view: currentView, filters: currentFilters } = latest.current;
    const items: PostView[] = [];
    let next = cursor;
    for (let request = 0; request < MAX_REQUESTS; request += 1) {
      const page = await fetchPostPage(buildPostQuery(currentFilters, { cursor: next, limit: PAGE_LIMIT }), signal, id as string);
      for (const post of page.items) {
        if (belongsToView(post, currentView)) items.push(post);
      }
      next = page.nextCursor;
      if (!next || items.length >= TARGET_VISIBLE) break;
    }
    return { items, nextCursor: next };
  }

  useEffect(() => {
    if (fullKey === null || key === null) return;
    const saved = loadSnapshot(key);
    if (saved) {
      // Anlık görüntü varsa ağ isteği atılmaz. Kaydırma konumu çizimden sonra geri yüklenir; Next gezinme sonrası sayfayı
      // başa sarabildiği için, kullanıcı kendisi kaydırmaya başlayana dek birkaç kez yeniden denenir.
      if (saved.scrollY <= 0) return;
      let userScrolled = false;
      const stop = () => {
        userScrolled = true;
      };
      window.addEventListener("wheel", stop, { passive: true });
      window.addEventListener("touchstart", stop, { passive: true });
      window.addEventListener("keydown", stop);
      const timers = [0, 150, 400, 800, 1400].map((delay) =>
        window.setTimeout(() => {
          if (!userScrolled && Math.abs(window.scrollY - saved.scrollY) > 2) window.scrollTo(0, saved.scrollY);
        }, delay)
      );
      return () => {
        timers.forEach((timer) => window.clearTimeout(timer));
        window.removeEventListener("wheel", stop);
        window.removeEventListener("touchstart", stop);
        window.removeEventListener("keydown", stop);
      };
    }
    const controller = new AbortController();
    let active = true;
    collect(null, controller.signal).then(
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
    // `collect` yalnız `latest` ref'ini okur; anahtar değişince efekt yeniden çalışır.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fullKey]);

  // Yüklenmiş liste anlık görüntüye yazılır; kaydırma konumu kaydırdıkça güncellenir.
  useEffect(() => {
    if (key === null || !current || current.error) return;
    saveSnapshot(key, current.items, current.nextCursor, window.scrollY);
  }, [key, current]);

  useEffect(() => {
    if (key === null) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    // Gecikmeli kayıt: başka sayfaya geçerken Next'in başa sarma hareketi (scrollY=0) bileşen kaldırılmadan önce yeni
    // konum olarak yazılmasın; temizlikte bekleyen kayıt iptal edilir ve son gerçek kullanıcı konumu korunur.
    function onScroll() {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => rememberScroll(key as string, window.scrollY), 120);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      if (timer) clearTimeout(timer);
      window.removeEventListener("scroll", onScroll);
    };
  }, [key]);

  function loadMore() {
    if (!current || key === null || current.loadingMore || current.nextCursor === null) return;
    const cursor = current.nextCursor;
    const controller = new AbortController();
    moreController.current = controller;
    setState({ ...current, loadingMore: true, moreError: undefined });
    collect(cursor, controller.signal).then(
      (page) =>
        setState((previous) => {
          const base = previous && previous.key === fullKey ? previous : current;
          const known = new Set(base.items.map((item) => item.id));
          return {
            ...base,
            items: [...base.items, ...page.items.filter((item) => !known.has(item.id))],
            nextCursor: page.nextCursor,
            loadingMore: false
          };
        }),
      (error: unknown) => {
        if (isAbort(error)) return;
        setState((previous) => ({ ...(previous && previous.key === fullKey ? previous : current), loadingMore: false, moreError: error }));
      }
    );
  }

  function mutate(change: (items: PostView[]) => PostView[]) {
    if (!current) return;
    setState((previous) => {
      const base = previous && previous.key === fullKey ? previous : current;
      return { ...base, items: change(base.items) };
    });
  }

  return {
    items: current?.items ?? [],
    loading: key === null || !current,
    error: current?.error,
    hasMore: current?.nextCursor != null,
    loadingMore: current?.loadingMore ?? false,
    moreError: current?.moreError,
    loadMore,
    reload: () => {
      invalidateMyPosts();
      setState(undefined);
      setRevision((value) => value + 1);
    },
    update: (id: string, next: (post: PostView) => PostView) => mutate((items) => items.map((item) => (item.id === id ? next(item) : item))),
    remove: (id: string) => mutate((items) => items.filter((item) => item.id !== id))
  };
}
