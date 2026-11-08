"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { GameContentType, PostCategory, RepostSource } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import { chipClass } from "./community-ui";
import { AuthorLine, GameChip, RepostBlock, type Lookups } from "./post-parts";

type DetailHref = { pathname: "/community/posts/[postId]"; params: { postId: string } };

/**
 * Gönderinin içerik bölümü: yazar satırı, rozetler, başlık, metin, repost atfı ve medya. Akış kartı, detay ve oluşturma
 * önizlemesi AYNI bileşeni kullanır; böylece tipografi, boşluk ve medya davranışı yayında ve önizlemede birebir aynıdır.
 */
export function PostContent({
  headingId,
  lookups,
  authorId,
  createdAt,
  edited = false,
  gameId,
  contentType,
  category,
  hidden = false,
  title,
  titleHref,
  body,
  clampBody = false,
  readMoreHref,
  repostOf,
  media
}: {
  headingId: string;
  lookups: Lookups;
  authorId: string | null;
  createdAt?: string;
  edited?: boolean;
  gameId: string | null;
  contentType: GameContentType | null;
  category: PostCategory;
  hidden?: boolean;
  title: string | null;
  /** Verilirse başlık bağlantıdır (akış kartı). */
  titleHref?: DetailHref;
  body: string | null;
  clampBody?: boolean;
  readMoreHref?: DetailHref;
  repostOf?: RepostSource | null;
  /** Ortak medya (carousel). */
  media?: ReactNode;
}) {
  const t = useTranslations("Community");
  const showCategory = category !== "GENERAL" && !contentType;
  const text = body ?? "";

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <AuthorLine
          authorId={authorId}
          lookups={lookups}
          createdAt={createdAt}
          trailing={edited ? <span className="text-xs text-text-secondary">· {t("post.edited")}</span> : null}
        />
        <div className="flex flex-wrap items-center gap-2">
          <GameChip gameId={gameId} lookups={lookups} />
          {contentType ? <span className={chipClass}>{t(`post.contentType.${contentType}`)}</span> : null}
          {showCategory ? <span className={chipClass}>{t(`category.${category}`)}</span> : null}
          {hidden ? <span className={cn(chipClass, "border-warning/60 text-text-primary")}>{t("post.hidden")}</span> : null}
        </div>
      </header>

      {repostOf !== undefined && repostOf !== null ? (
        <p className="mt-3 flex items-center gap-1.5 text-sm text-text-secondary">
          <CommunityIcon name="repost" className="size-4" />
          {t("post.repostedBy")}
        </p>
      ) : null}

      {title ? (
        <h2 id={headingId} className="mt-3 break-words font-display text-xl font-semibold leading-snug text-text-primary">
          {titleHref ? (
            <Link href={titleHref} className="hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
              {title}
            </Link>
          ) : (
            title
          )}
        </h2>
      ) : (
        <h2 id={headingId} className="sr-only">
          {t("post.srTitle")}
        </h2>
      )}

      {text ? (
        <p className={cn("mt-3 whitespace-pre-wrap break-words text-[0.9375rem] leading-7 text-text-primary", clampBody && "line-clamp-6")}>
          {text}
        </p>
      ) : null}
      {clampBody && readMoreHref ? (
        <Link
          href={readMoreHref}
          className="mt-1 inline-flex min-h-11 items-center text-sm font-medium text-brand-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          {t("post.readMore")}
        </Link>
      ) : null}

      {repostOf ? <RepostBlock source={repostOf} lookups={lookups} /> : null}
      {media}
    </>
  );
}
