"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { GameContentType, PostCategory, RepostSource } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import { chipClass } from "./community-ui";
import { ExpandableText } from "./expandable-text";
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
  clamp = false,
  repostOf,
  media,
  actions
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
  /** Liste kartı: uzun metin satır sınırıyla kesilir ("Devamını gör"); detay ve önizleme tam metni gösterir. */
  clamp?: boolean;
  repostOf?: RepostSource | null;
  /** Ortak medya (carousel). */
  media?: ReactNode;
  /** Başlık satırının sağ ucundaki işlem menüsü (üç nokta). */
  actions?: ReactNode;
}) {
  const t = useTranslations("Community");
  const showCategory = category !== "GENERAL" && !contentType;
  const text = body ?? "";

  return (
    <>
      <header className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-4 gap-y-2">
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
        </div>
        {actions}
      </header>

      {repostOf !== undefined && repostOf !== null ? (
        <p className="mt-3 flex items-center gap-1.5 text-sm text-text-secondary">
          <CommunityIcon name="repost" className="size-4" />
          {t("post.repostedBy")}
        </p>
      ) : null}

      {title ? (
        <h2 id={headingId} className={cn("mt-4 break-words font-display text-xl font-semibold leading-snug text-text-primary", clamp && "line-clamp-2")}>
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
        clamp ? (
          <div className="mt-4">
            <ExpandableText text={text} />
          </div>
        ) : (
          <p className="mt-4 whitespace-pre-wrap break-words [overflow-wrap:anywhere] text-[0.9375rem] leading-7 text-text-primary">{text}</p>
        )
      ) : null}
      {repostOf ? <RepostBlock source={repostOf} lookups={lookups} /> : null}
      {media}
    </>
  );
}
