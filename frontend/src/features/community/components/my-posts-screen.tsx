"use client";

import { useMemo, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAuthors, useGames } from "../hooks/use-directory";
import { useMyPosts } from "../hooks/use-my-posts";
import { useViewer } from "../hooks/use-viewer";
import { activeFilterCount, filtersFromParams, paramsFromFilters, type MyPostsView } from "../services/my-posts-query";
import { isInvalidRange } from "../services/post-query";
import { emptyFilters, type PostFilters } from "../types/community.types";
import { CommunityFrame, NewPostLink } from "./community-frame";
import { CommunityIcon } from "./community-icons";
import { buttonClass, ErrorBlock, LoadingBlock, LoadMore, primaryButtonClass } from "./community-ui";
import { MyPostsToolbar } from "./my-posts-toolbar";
import { PostCard } from "./post-card";

/**
 * Gönderilerim (kendi oluşturduğum tüm metin ve görselli gönderiler) ve Yeniden paylaşımlarım alt görünümü. Kartlar Akış ile
 * AYNI `PostCard` bileşenidir; her satırda tek kart bulunur. Süzgeç ve sıralama URL'dedir:
 * detaydan/düzenlemeden dönünce ya da sayfa yenilenince korunur. Liste ve kaydırma konumu bellek içi anlık görüntüden gelir.
 */
export function MyPostsScreen({ view }: { view: MyPostsView }) {
  const t = useTranslations("Community");
  const tm = useTranslations("Community.mine");
  const session = useRequireSession();
  const viewer = useViewer();
  const params = useSearchParams();
  const router = useRouter();
  const filters = useMemo(() => filtersFromParams((key) => params.get(key), view), [params, view]);
  const pathname = view === "posts" ? "/community/mine" : "/community/mine/reposts";

  const list = useMyPosts({ userId: viewer.status === "authenticated" ? viewer.accountId : null, view, filters });
  const authors = useAuthors(
    list.items.map((post) => post.repostOf?.authorId),
    viewer.status === "authenticated"
  );
  const games = useGames(list.items.flatMap((post) => [post.gameId, post.repostOf?.gameId]));

  function change(next: PostFilters) {
    router.replace({ pathname, query: paramsFromFilters(next) }, { scroll: false });
  }

  /** Silinen kartın yerine odak komşu kartın yorum düğmesine (yoksa arama alanına) taşınır; odak kaybolmaz. */
  function handleRemoved(id: string) {
    const index = list.items.findIndex((post) => post.id === id);
    list.remove(id);
    window.requestAnimationFrame(() => {
      const triggers = document.querySelectorAll<HTMLElement>("main article footer button[aria-controls]");
      const target = triggers[Math.min(index, triggers.length - 1)] ?? document.querySelector<HTMLElement>("main input[type=search], main a.bg-primary");
      target?.focus();
    });
  }

  const filtered = activeFilterCount(filters) > 0;
  const reposts = view === "reposts";
  // Hiç kayıt yokken (ve süzgeç yokken) arama/filtre/sıralama anlamsızdır; araç çubuğu yalnız boş durumu kalabalıklaştırırdı.
  const trulyEmpty = session.status === "authenticated" && !list.loading && !list.error && list.items.length === 0 && !list.hasMore && !filtered;

  const action: ReactNode = reposts ? (
    <Link href="/community/mine" className={cn(buttonClass, "gap-2")}>
      <span aria-hidden="true">←</span>
      {tm("backToPosts")}
    </Link>
  ) : (
    <NewPostLink />
  );

  let body: ReactNode;
  if (session.status !== "authenticated" || list.loading) {
    body = <LoadingBlock rows={3} />;
  } else if (list.error) {
    body = <ErrorBlock error={list.error} retry={list.reload} />;
  } else if (list.items.length === 0 && list.hasMore) {
    // Sunucu listesi sürüyor ama taranan sayfalarda bu görünüme ait kayıt çıkmadı: boş denmez, arama sürdürülür.
    body = (
      <MineEmpty title={tm("searchingTitle")} description={tm("searchingBody")}>
        <button type="button" className={buttonClass} onClick={list.loadMore} disabled={list.loadingMore}>
          {list.loadingMore ? t("state.loadingMore") : tm("continueSearch")}
        </button>
      </MineEmpty>
    );
  } else if (list.items.length === 0 && filtered) {
    body = (
      <MineEmpty title={tm("emptyFilteredTitle")} description={isInvalidRange(filters) ? tm("invalidRange") : tm("emptyFilteredBody")}>
        <button type="button" className={buttonClass} onClick={() => change({ ...emptyFilters, sort: filters.sort })}>
          {tm("clear")}
        </button>
      </MineEmpty>
    );
  } else if (list.items.length === 0) {
    body = reposts ? (
      <MineEmpty title={tm("repostsEmptyTitle")} description={tm("repostsEmptyBody")}>
        <Link href="/community" className={primaryButtonClass}>
          {tm("repostsEmptyCta")}
        </Link>
      </MineEmpty>
    ) : (
      <MineEmpty title={tm("emptyTitle")} description={tm("emptyBody")}>
        <Link href="/community/new" className={primaryButtonClass}>
          <CommunityIcon name="pencil" className="size-4" />
          {tm("emptyCta")}
        </Link>
      </MineEmpty>
    );
  } else {
    body = (
      <>
        <ul className="space-y-4">
          {list.items.map((post) => (
            <li key={post.id} className="min-w-0">
              <PostCard
                post={post}
                viewer={viewer}
                lookups={{ author: authors, game: games }}
                onChange={(next) => list.update(post.id, () => next)}
                onRemoved={handleRemoved}
              />
            </li>
          ))}
        </ul>
        <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
      </>
    );
  }

  return (
    <CommunityFrame
      title={reposts ? tm("repostsTitle") : tm("title")}
      description={reposts ? tm("repostsDescription") : tm("description")}
      action={action}
    >
      {trulyEmpty ? null : <MyPostsToolbar view={view} filters={filters} onChange={change} />}
      {body}
    </CommunityFrame>
  );
}

/** Boş durum: sade kenarlıklı kutu; sahte kapak ya da büyük dekoratif ikon yok. */
function MineEmpty({ title, description, children }: { title: string; description: string; children?: ReactNode }) {
  return (
    <div className="mx-auto max-w-xl rounded-panel border border-border bg-surface px-6 py-12 text-center sm:px-10">
      <h2 className="font-display text-xl font-semibold text-text-primary">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-text-secondary">{description}</p>
      {children ? <div className="mt-6 flex justify-center">{children}</div> : null}
    </div>
  );
}
