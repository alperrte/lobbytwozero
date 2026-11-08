"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useAuthors, useGames } from "../hooks/use-directory";
import { useResource } from "../hooks/use-resource";
import { useViewer } from "../hooks/use-viewer";
import { fetchPost } from "../services/community-api";
import { statusOf } from "../services/community-errors";
import { isUuid } from "../services/post-query";
import type { PostView } from "../types/community.types";
import { CommentsSection } from "./comments-section";
import { CommunityFrame } from "./community-frame";
import { buttonClass, EmptyBlock, ErrorBlock, LoadingBlock } from "./community-ui";
import { PostCard } from "./post-card";
import { PostEditor } from "./post-editor";

/**
 * Gönderi detayı: tam metin, tam boyutlu görsel bağlantıları, yorumlar ve (sahip için) düzenleme. Bulunamayan, silinmiş,
 * gizli ya da engelli içerik aynı 404 ile gelir ve aynı mesajla gösterilir (varlığı sızdırılmaz).
 */
export function PostDetailScreen({ postId }: { postId: string }) {
  const t = useTranslations("Community");
  const viewer = useViewer();
  const valid = isUuid(postId);
  // Kişiye özel alanlar (likedByMe, gizli gönderi sahipliği) için oturum durumu netleşince okunur.
  const key = valid && viewer.status !== "loading" ? `post:${postId}:${viewer.status}` : null;
  const post = useResource(key, (signal) => fetchPost(postId, signal));

  if (!valid || statusOf(post.error) === 404 || statusOf(post.error) === 400) {
    return (
      <CommunityFrame title={t("detail.title")} description={t("detail.description")}>
        <EmptyBlock title={t("detail.notFoundTitle")} description={t("detail.notFoundBody")}>
          <Link href="/community" className={buttonClass}>
            {t("detail.backToFeed")}
          </Link>
        </EmptyBlock>
      </CommunityFrame>
    );
  }

  return (
    <CommunityFrame title={t("detail.title")} description={t("detail.description")}>
      {post.loading || viewer.status === "loading" ? <LoadingBlock rows={2} /> : null}
      {post.error ? <ErrorBlock error={post.error} retry={post.reload} /> : null}
      {post.data ? <DetailBody post={post.data} onPost={post.set} /> : null}
    </CommunityFrame>
  );
}

function DetailBody({ post, onPost }: { post: PostView; onPost: (post: PostView) => void }) {
  const t = useTranslations("Community");
  const viewer = useViewer();
  const router = useRouter();
  const params = useSearchParams();
  const [editing, setEditing] = useState(params.get("edit") === "1");
  const author = useAuthors(
    [post.authorId, post.repostOf?.authorId],
    viewer.status === "authenticated"
  );
  const game = useGames([post.gameId, post.repostOf?.gameId]);
  const isOwner = viewer.accountId === post.authorId;
  const back = post.forumId
    ? ({ pathname: "/community/forums/[forumId]", params: { forumId: post.forumId } } as const)
    : ("/community" as const);

  return (
    <div>
      <Link href={back} className="mb-4 inline-flex min-h-11 items-center text-sm font-medium text-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
        ← {post.forumId ? t("detail.backToForum") : t("detail.backToFeed")}
      </Link>

      {editing && isOwner ? (
        <PostEditor
          post={post}
          onCancel={() => setEditing(false)}
          onDone={(fresh) => {
            onPost(fresh);
            setEditing(false);
          }}
        />
      ) : (
        <PostCard
          post={post}
          viewer={viewer}
          lookups={{ author, game }}
          detail
          onEdit={() => setEditing(true)}
          onChange={onPost}
          onRemoved={() => router.replace(post.forumId ? { pathname: "/community/forums/[forumId]", params: { forumId: post.forumId } } : "/community")}
        />
      )}

      {post.status === "VISIBLE" ? (
        <CommentsSection postId={post.id} viewer={viewer} onCountChange={(delta) => onPost({ ...post, commentCount: Math.max(0, post.commentCount + delta) })} />
      ) : null}
    </div>
  );
}
