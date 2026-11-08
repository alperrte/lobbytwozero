"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAuthors, useGames } from "../hooks/use-directory";
import { usePagedList } from "../hooks/use-paged-list";
import { usePublishHandoff } from "../hooks/use-publish-handoff";
import { useResource } from "../hooks/use-resource";
import { useViewer } from "../hooks/use-viewer";
import { fetchForum, fetchPostPage } from "../services/community-api";
import { statusOf } from "../services/community-errors";
import { buildPostQuery, isUuid } from "../services/post-query";
import { emptyFilters, type PostFilters, type PostView } from "../types/community.types";
import { CommunityFrame, NewPostLink } from "./community-frame";
import { CommunityIcon } from "./community-icons";
import { buttonClass, chipClass, EmptyBlock, ErrorBlock, LoadMore, LoadingBlock, panel, RelativeTime } from "./community-ui";
import { PostFilterBar } from "./post-filters";
import { PublishNotice } from "./publish-notice";
import "./community.css";
import { AuthorLine } from "./post-parts";

/** Konu başlığı: forum konusunun ayrı başlığı yoktur; gövdenin ilk satırı kısaltılarak gösterilir. */
export function topicTitle(body: string | null, max = 90): string {
  const first = (body ?? "").trim().split("\n")[0]?.trim() ?? "";
  return first.length > max ? `${first.slice(0, max - 1)}…` : first;
}

/**
 * Forum konuları: forum, `GET /posts?forumId=` ile listelenen gönderilerdir (konu = gönderi). Son yanıt zamanı API'de
 * yoktur ve gösterilmez; konunun yazarı, açılış zamanı, beğeni ve yorum sayısı sunucudan gelir.
 */
export function ForumTopicsScreen({ forumId }: { forumId: string }) {
  const t = useTranslations("Community");
  const viewer = useViewer();
  const valid = isUuid(forumId);
  const forum = useResource(valid ? `forum:${forumId}` : null, (signal) => fetchForum(forumId, signal));
  const [filters, setFilters] = useState<PostFilters>(emptyFilters);
  const key = valid && viewer.status !== "loading" ? `topics:${forumId}:${viewer.status}:${buildPostQuery(filters, { forumId })}` : null;
  const list = usePagedList(key, (cursor, signal) => fetchPostPage(buildPostQuery(filters, { forumId, cursor }), signal));
  const author = useAuthors(
    list.items.map((post) => post.authorId),
    viewer.status === "authenticated"
  );
  const game = useGames([forum.data?.gameId]);
  const revealId = usePublishHandoff(!list.loading);

  if (!valid || statusOf(forum.error) === 404 || statusOf(forum.error) === 400) {
    return (
      <CommunityFrame title={t("forums.title")} description={t("forums.description")}>
        <EmptyBlock title={t("forum.notFoundTitle")} description={t("forum.notFoundBody")}>
          <Link href="/community/forums" className={buttonClass}>
            {t("forum.back")}
          </Link>
        </EmptyBlock>
      </CommunityFrame>
    );
  }

  const gameTitle = forum.data?.gameId ? (game(forum.data.gameId)?.title ?? null) : null;

  return (
    <CommunityFrame
      title={forum.data?.title ?? t("forums.title")}
      description={forum.data?.description ?? t("forum.noDescription")}
      action={forum.data ? <NewPostLink forumId={forum.data.id} /> : undefined}
    >
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Link href="/community/forums" className="inline-flex min-h-11 items-center text-sm font-medium text-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
          ← {t("forum.back")}
        </Link>
        {gameTitle ? <span className={chipClass}>{gameTitle}</span> : null}
      </div>

      {forum.error && !forum.data ? <ErrorBlock error={forum.error} retry={forum.reload} /> : null}

      <PublishNotice />
      <PostFilterBar filters={filters} onChange={setFilters} options={{ dates: true }} />

      {list.loading ? <LoadingBlock rows={3} /> : null}
      {list.error ? <ErrorBlock error={list.error} retry={list.reload} /> : null}
      {!list.loading && !list.error ? (
        list.items.length === 0 ? (
          <EmptyBlock title={t("forum.emptyTitle")} description={t("forum.emptyBody")}>
            <NewPostLink forumId={forumId} />
          </EmptyBlock>
        ) : (
          <>
            <ul className={cn(panel, "divide-y divide-border")}>
              {list.items.map((post) => (
                <li key={post.id} className={revealId === post.id ? "ltz-post-enter" : undefined}>
                  <TopicRow post={post} lookups={{ author, game: () => null }} />
                </li>
              ))}
            </ul>
            <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
          </>
        )
      ) : null}
    </CommunityFrame>
  );
}

function TopicRow({ post, lookups }: { post: PostView; lookups: Parameters<typeof AuthorLine>[0]["lookups"] }) {
  const t = useTranslations("Community");
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 p-4">
      <div className="min-w-0 flex-1 basis-64">
        <Link
          href={{ pathname: "/community/posts/[postId]", params: { postId: post.id } }}
          className="block rounded-control font-display text-base font-semibold leading-snug text-text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          {topicTitle(post.body) || t("forum.untitled")}
        </Link>
        <div className="mt-2">
          <AuthorLine authorId={post.authorId} lookups={lookups} createdAt={post.createdAt} size="sm" />
        </div>
      </div>
      <dl className="flex items-center gap-4 text-sm text-text-secondary">
        <div className="flex items-center gap-1.5">
          <CommunityIcon name="heart" className="size-4" />
          <dt className="sr-only">{t("forum.likes")}</dt>
          <dd>{post.likeCount}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <CommunityIcon name="comment" className="size-4" />
          <dt className="sr-only">{t("forum.replies")}</dt>
          <dd>{post.commentCount}</dd>
        </div>
        <div className="hidden items-center gap-1.5 sm:flex">
          <CommunityIcon name="clock" className="size-4" />
          <dt className="sr-only">{t("forum.opened")}</dt>
          <dd>
            <RelativeTime value={post.createdAt} />
          </dd>
        </div>
      </dl>
    </div>
  );
}
