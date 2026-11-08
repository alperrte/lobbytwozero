"use client";

import { useCallback } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useRemote } from "../hooks/use-remote";
import { getRecentPosts } from "../services/activity-api";
import type { RecentPost } from "../types/activity.types";
import { ProfileSection, RemoteBody, sectionLinkClass } from "./remote-section";

const PREVIEW_CHARS = 220;

function excerpt(value: string): string {
  const chars = Array.from(value.trim().replace(/\s+/g, " "));
  return chars.length > PREVIEW_CHARS ? `${chars.slice(0, PREVIEW_CHARS).join("")}…` : chars.join("");
}

/**
 * Aktivite sekmesi: Community'deki son gönderilerin (`GET /community/users/{id}/posts`). Metin düz yazı olarak gösterilir.
 * Toplam gönderi sayısı sunucudan gelmediği için sayı yazılmaz; ayrıntı için Community'deki gönderi sayfalarına bağlanır.
 */
export function ProfileActivity({ userId }: { userId: string }) {
  const t = useTranslations("Profile.activity");
  const load = useCallback((signal: AbortSignal) => getRecentPosts(userId, signal), [userId]);
  const remote = useRemote(userId, load);

  return (
    <ProfileSection
      title={t("title")}
      action={
        <Link href="/community/mine" className={sectionLinkClass}>
          {t("all")}
        </Link>
      }
    >
      <RemoteBody remote={remote}>
        {(posts) =>
          posts.length === 0 ? (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm text-text-secondary">{t("empty")}</p>
              <Link href="/community/new" className={sectionLinkClass}>
                {t("write")}
              </Link>
            </div>
          ) : (
            <ul className="flex flex-col gap-3">
              {posts.map((post) => (
                <li key={post.id}>
                  <PostRow post={post} />
                </li>
              ))}
            </ul>
          )
        }
      </RemoteBody>
    </ProfileSection>
  );
}

function PostRow({ post }: { post: RecentPost }) {
  const t = useTranslations("Profile.activity");
  const format = useFormatter();
  const heading = post.title ?? (post.body ? excerpt(post.body) : t("untitled"));
  const detail = post.title && post.body ? excerpt(post.body) : null;

  return (
    <article className="rounded-control border border-border bg-surface-elevated p-4">
      <div className="flex flex-wrap items-center gap-2 text-xs text-text-secondary">
        {post.createdAt ? (
          <time dateTime={post.createdAt}>{format.dateTime(new Date(post.createdAt), { dateStyle: "medium" })}</time>
        ) : null}
        {post.isRepost ? <span className="rounded-full border border-border px-2 py-0.5">{t("repost")}</span> : null}
        {post.hidden ? <span className="rounded-full border border-border px-2 py-0.5">{t("hidden")}</span> : null}
      </div>
      <h3 className="mt-2 text-sm font-semibold text-text-primary">
        <Link
          href={{ pathname: "/community/posts/[postId]", params: { postId: post.id } }}
          className="hover:underline"
        >
          {heading}
        </Link>
      </h3>
      {detail ? <p className="mt-1 text-sm text-text-secondary">{detail}</p> : null}
      <p className="mt-3 flex flex-wrap gap-4 text-xs text-text-secondary">
        <span>{t("likes", { count: post.likeCount })}</span>
        <span>{t("comments", { count: post.commentCount })}</span>
      </p>
    </article>
  );
}
