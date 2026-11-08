"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { focusFirstMenuItem, onMenuKeyDown, usePopover } from "@/shared/hooks/use-popover";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { deletePost } from "../services/community-api";
import type { MyPostsView } from "../services/my-posts-query";
import type { PostView } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import { chipClass, ConfirmDialog, RelativeTime } from "./community-ui";
import { ImageViewer, MediaCarousel } from "./media-carousel";
import { AuthorLine, GameChip, toMediaItems, type Lookups } from "./post-parts";

const MEDIA_RATIO = 4 / 3;

const menuItemClass =
  "flex min-h-11 w-full items-center gap-2.5 rounded-control px-3 text-start text-sm font-medium text-text-primary transition-colors hover:bg-surface-elevated focus-visible:bg-surface-elevated focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus";

/**
 * "Gönderilerim" kartı, içeriğine göre doğal yüksekliktedir (satırdaki komşu kart yüzünden uzamaz). Görselli kartın üstünde
 * kontrollü oranlı (4:3) önizleme vardır (çoklu görselde carousel); görselsiz kartta boş görsel alanı yoktur ve metin başlık
 * gibi büyütülmeden normal gönderi metni olarak okunur. Dekoratif köşe/şerit yoktur. İçerik bağlantısı gönderi detayını açar. Düzenle/Sil üç nokta menüsündedir; menü ve carousel bağlantının
 * DIŞINDADIR, bu yüzden yanlışlıkla detay açmaz ve iç içe etkileşimli öğe oluşmaz. Yetki kararı sunucudadır.
 */
export function PostGalleryCard({
  post,
  view,
  lookups,
  onRemoved
}: {
  post: PostView;
  view: MyPostsView;
  lookups: Lookups;
  onRemoved: (postId: string) => void;
}) {
  const t = useTranslations("Community");
  const tc = useTranslations("Community.mine.card");
  const ti = useTranslations("Community.images");
  const headingId = useId();
  const { open: menuOpen, setOpen: setMenuOpen, close: closeMenu, rootRef: menuRootRef, triggerRef: menuTriggerRef } = usePopover();
  const menuRef = useRef<HTMLDivElement>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [viewer, setViewer] = useState<number | null>(null);

  const isRepost = view === "reposts" && post.repostOf !== null;
  const source = isRepost ? post.repostOf : null;
  const sourceAvailable = source?.available === true;
  const text = isRepost ? (sourceAvailable ? (source?.body ?? "") : "") : (post.body ?? "");
  const title = isRepost ? null : post.title;
  const placeholder = isRepost && !sourceAvailable ? t("post.repostUnavailable") : "";
  const items = useMemo(() => {
    const images = isRepost ? (sourceAvailable ? (source?.images ?? []) : []) : post.images;
    return toMediaItems(images, (number) => ti("alt", { number }));
  }, [isRepost, sourceAvailable, source, post.images, ti]);
  const hasMedia = items.length > 0;
  const detailHref = { pathname: "/community/posts/[postId]", params: { postId: post.id } } as const;
  const canEdit = !isRepost && post.status === "VISIBLE";
  const showCategory = post.category !== "GENERAL" && !post.contentType && !isRepost;

  const remove = useAction(async () => {
    await deletePost(post.id);
    setDeleteOpen(false);
    onRemoved(post.id);
  });

  useEffect(() => {
    if (menuOpen) focusFirstMenuItem(menuRef.current);
  }, [menuOpen]);

  function closeDialog() {
    remove.clearError();
    setDeleteOpen(false);
    // Menü kapandığı için odak tetikleyiciye döner.
    window.requestAnimationFrame(() => menuTriggerRef.current?.focus());
  }

  const chipGameId = isRepost ? (sourceAvailable ? (source?.gameId ?? null) : null) : post.gameId;
  const summary = text || placeholder || (title ?? tc("noText"));
  const linkClass =
    "rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";

  return (
    <article
      aria-labelledby={headingId}
      className="flex min-w-0 flex-col rounded-panel border border-border bg-surface transition-colors hover:border-text-secondary/60 focus-within:border-text-secondary/60"
    >

      {hasMedia ? (
        <div className="overflow-hidden rounded-t-panel border-b border-border">
          <MediaCarousel
            items={items}
            variant="card"
            bare
            ratio={MEDIA_RATIO}
            label={ti("galleryLabel", { count: items.length })}
            onOpen={setViewer}
          />
          <ImageViewer items={items} startIndex={viewer} label={ti("galleryLabel", { count: items.length })} onClose={() => setViewer(null)} />
        </div>
      ) : (
        <h3 id={headingId} className="min-w-0 px-4 pt-4">
          <Link href={detailHref} className={cn("block break-words", linkClass)}>
            {title ? (
              <>
                <span className="line-clamp-2 break-words font-display text-base font-semibold leading-snug text-text-primary hover:underline">{title}</span>
                <span className="mt-1 line-clamp-4 block break-words text-[0.9375rem] leading-6 text-text-secondary">{summary}</span>
              </>
            ) : (
              <span className={cn("line-clamp-6 block break-words text-[0.9375rem] leading-6", text ? "text-text-primary" : "text-text-secondary")}>{summary}</span>
            )}
          </Link>
        </h3>
      )}

      {hasMedia ? (
        <div className="px-4 pt-3">
          <h3 id={headingId} className="min-w-0">
            <Link href={detailHref} className={cn("block break-words", linkClass)}>
              {title ? (
                <>
                  <span className="line-clamp-1 break-words font-display text-base font-semibold text-text-primary hover:underline">{title}</span>
                  <span className="mt-0.5 line-clamp-2 break-words text-sm leading-6 text-text-secondary">{summary}</span>
                </>
              ) : (
                <span className="line-clamp-3 break-words text-[0.9375rem] leading-6 text-text-primary hover:underline">{summary}</span>
              )}
            </Link>
          </h3>
        </div>
      ) : null}

      {isRepost && sourceAvailable ? (
        <div className="flex items-center gap-2 px-4 pt-3">
          <CommunityIcon name="repost" className="size-4 shrink-0 text-text-secondary" />
          <span className="sr-only">{tc("originalBy")}</span>
          <AuthorLine authorId={source?.authorId} lookups={lookups} size="sm" />
        </div>
      ) : null}
      {isRepost && post.body ? <p className="line-clamp-2 px-4 pt-2 text-sm italic leading-5 text-text-secondary">“{post.body}”</p> : null}

      <div>
        {chipGameId || post.contentType || showCategory || post.status === "HIDDEN" ? (
          <div className="flex flex-wrap items-center gap-1.5 px-4 pt-3">
            <GameChip gameId={chipGameId} lookups={lookups} />
            {post.contentType ? <span className={chipClass}>{t(`post.contentType.${post.contentType}`)}</span> : null}
            {showCategory ? <span className={chipClass}>{t(`category.${post.category}`)}</span> : null}
            {post.status === "HIDDEN" ? <span className={cn(chipClass, "border-warning/60 text-text-primary")}>{t("post.hidden")}</span> : null}
          </div>
        ) : null}
      <footer className="flex items-center gap-3 px-4 pb-1 pt-2">
        <div className="min-w-0 flex-1 text-xs text-text-secondary">
          <RelativeTime value={post.createdAt} />
        </div>
        <dl className="flex items-center gap-3 text-xs tabular-nums text-text-secondary">
          <div className="flex items-center gap-1">
            <CommunityIcon name="heart" className="size-3.5" />
            <dt className="sr-only">{tc("likes")}</dt>
            <dd>{post.likeCount}</dd>
          </div>
          <div className="flex items-center gap-1">
            <CommunityIcon name="comment" className="size-3.5" />
            <dt className="sr-only">{tc("comments")}</dt>
            <dd>{post.commentCount}</dd>
          </div>
        </dl>

        <div ref={menuRootRef} className="relative -me-2 -my-1">
          <button
            ref={menuTriggerRef}
            data-card-menu=""
            type="button"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label={tc("menu")}
            onClick={() => setMenuOpen(!menuOpen)}
            onKeyDown={(event) => {
              if (event.key === "ArrowDown" && !menuOpen) {
                event.preventDefault();
                setMenuOpen(true);
              }
            }}
            className="inline-flex size-11 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary aria-expanded:bg-surface-elevated aria-expanded:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-focus"
          >
            <CommunityIcon name="more" className="size-5" />
          </button>
          {menuOpen ? (
            <div
              ref={menuRef}
              role="menu"
              aria-label={tc("menu")}
              onKeyDown={onMenuKeyDown}
              className="absolute bottom-full end-0 z-30 mb-1 w-60 rounded-control border border-border bg-surface p-1 shadow-popover"
            >
              {canEdit ? (
                <Link
                  role="menuitem"
                  href={{ pathname: "/community/posts/[postId]", params: { postId: post.id }, query: { edit: "1" } }}
                  className={menuItemClass}
                >
                  <CommunityIcon name="pencil" className="size-4" />
                  {tc("edit")}
                </Link>
              ) : null}
              {isRepost && sourceAvailable && source ? (
                <Link
                  role="menuitem"
                  href={{ pathname: "/community/posts/[postId]", params: { postId: source.postId } }}
                  className={menuItemClass}
                >
                  <CommunityIcon name="comment" className="size-4" />
                  {tc("openOriginal")}
                </Link>
              ) : null}
              <button
                type="button"
                role="menuitem"
                className={cn(menuItemClass, "text-brand-text")}
                onClick={() => {
                  closeMenu(false);
                  setDeleteOpen(true);
                }}
              >
                <CommunityIcon name={isRepost ? "repost" : "trash"} className="size-4" />
                {isRepost ? tc("undoRepost") : tc("delete")}
              </button>
            </div>
          ) : null}
        </div>
      </footer>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        title={isRepost ? tc("undoTitle") : t("post.deleteTitle")}
        description={isRepost ? tc("undoBody") : t("post.deleteBody")}
        confirmLabel={isRepost ? tc("undoConfirm") : t("post.delete")}
        pending={remove.pending}
        error={remove.error}
        onConfirm={() => void remove.run()}
        onClose={closeDialog}
      />
    </article>
  );
}
