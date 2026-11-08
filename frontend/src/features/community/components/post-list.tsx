"use client";

import type { ReactNode } from "react";
import { useAuthors, useGames } from "../hooks/use-directory";
import type { usePagedList } from "../hooks/use-paged-list";
import type { Viewer } from "../hooks/use-viewer";
import type { PostView } from "../types/community.types";
import { EmptyBlock, ErrorBlock, LoadMore, LoadingBlock } from "./community-ui";
import { PostCard } from "./post-card";
import "./community.css";

export type PostListState = ReturnType<typeof usePagedList<PostView>>;

/**
 * Gönderi listesi: yazar ve oyun kartlarını tüm sayfa için tek toplu istekle çözer, yükleme/boş/hata durumlarını ayırır
 * ve imleçli "daha fazla" sağlar. Başarısız sayfa yüklemesi mevcut listeyi silmez.
 */
export function PostList({
  list,
  viewer,
  emptyTitle,
  emptyDescription,
  emptyAction,
  onReposted,
  visible,
  revealId
}: {
  list: PostListState;
  viewer: Viewer;
  emptyTitle: string;
  emptyDescription: string;
  emptyAction?: ReactNode;
  onReposted?: (post: PostView) => void;
  /** Yüklenmiş öğeler üzerinde istemci tarafı süzme (ör. yalnız yeniden paylaşımlar). */
  visible?: (post: PostView) => boolean;
  /** Paylaşım sonrası kısa giriş animasyonuyla belirecek gönderi. */
  revealId?: string | null;
}) {
  const items = visible ? list.items.filter(visible) : list.items;
  const author = useAuthors(
    list.items.flatMap((post) => [post.authorId, post.repostOf?.authorId]),
    viewer.status === "authenticated"
  );
  const game = useGames(list.items.flatMap((post) => [post.gameId, post.repostOf?.gameId]));

  if (list.loading) return <LoadingBlock rows={3} />;
  if (list.error) return <ErrorBlock error={list.error} retry={list.reload} />;

  return (
    <>
      {items.length === 0 ? (
        <EmptyBlock title={emptyTitle} description={emptyDescription}>
          {emptyAction}
        </EmptyBlock>
      ) : (
        <ul className="space-y-4">
          {items.map((post) => (
            <li key={post.id} className={revealId === post.id ? "ltz-post-enter" : undefined}>
              <PostCard
                post={post}
                viewer={viewer}
                lookups={{ author, game }}
                onChange={(next) => list.update(post.id, () => next)}
                onRemoved={list.remove}
                onReposted={onReposted}
              />
            </li>
          ))}
        </ul>
      )}
      <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
    </>
  );
}
