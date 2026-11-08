import type { PostView } from "../types/community.types";

/**
 * "Gönderilerim" listesinin bellek içi anlık görüntüsü (depoya yazılmaz): detaydan ya da düzenlemeden dönünce yüklenmiş
 * sayfalar ve kaydırma konumu geri gelir. Veri değiştiren her Community isteği (oluştur/düzenle/sil/yeniden paylaş/görsel)
 * anlık görüntüyü geçersiz kılar; beğeni ve yorum sayaçları için kısa bir ömür (TTL) uygulanır.
 */
export type MyPostsSnapshot = { items: PostView[]; nextCursor: string | null; scrollY: number; savedAt: number };

export const SNAPSHOT_TTL_MS = 2 * 60 * 1000;
const store = new Map<string, MyPostsSnapshot>();

export function saveSnapshot(key: string, items: PostView[], nextCursor: string | null, scrollY: number): void {
  store.set(key, { items, nextCursor, scrollY, savedAt: Date.now() });
}

export function loadSnapshot(key: string, now: number = Date.now()): MyPostsSnapshot | undefined {
  const snapshot = store.get(key);
  if (!snapshot) return undefined;
  if (now - snapshot.savedAt > SNAPSHOT_TTL_MS) {
    store.delete(key);
    return undefined;
  }
  return snapshot;
}

/** Yalnız kaydırma konumunu günceller (listeyi ve ömrünü değiştirmez). */
export function rememberScroll(key: string, scrollY: number): void {
  const snapshot = store.get(key);
  if (snapshot) snapshot.scrollY = scrollY;
}

export function invalidateMyPosts(): void {
  store.clear();
}
