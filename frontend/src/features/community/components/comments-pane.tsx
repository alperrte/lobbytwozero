"use client";

import { useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { useAuthors } from "../hooks/use-directory";
import { useInView } from "../hooks/use-in-view";
import { useMediaQuery } from "../hooks/use-media-query";
import { usePagedList } from "../hooks/use-paged-list";
import type { Viewer } from "../hooks/use-viewer";
import { createComment, fetchComments } from "../services/community-api";
import { CommentComposer, CommentThread } from "./comments-section";
import { ErrorBlock, LoadMore } from "./community-ui";

/** Kart içi bölme küçük sayfalarla yüklenir; sonrası "Daha fazla yükle" ile (mevcut imleçli sayfalama). */
const PANE_PAGE = 5;
/** Bu genişlikten itibaren kart iki sütun olur (yorumlar sağda, her zaman açık); altında yorumlar açılır başlıkla gelir. */
export const WIDE_QUERY = "(min-width: 1280px)";

/**
 * Gönderi kartının yorum bölmesi (kendi kartının sağ sütunu ya da dar ekranda altındaki açılır bölüm). Yorumları mevcut
 * imleçli yorum ucundan, KONTROLLÜ yükler: yorumu olmayan gönderi için hiç istek atılmaz; yorumu olan gönderide istek,
 * kart görüntü alanına yaklaşınca (geniş ekran) ya da kullanıcı bölümü açınca (dar ekran) tek bir küçük sayfa için atılır.
 * Yanıt/düzenle/sil/raporla davranışları ve yetkiler sayfadaki yorum bölümüyle aynı bileşenlerden gelir. Liste kendi içinde
 * en çok 18rem yüksekliğe kadar uzar ve dikey kayar; yorum yazma alanı listenin altında sabit kalır.
 */
export function CommentsPane({
  postId,
  commentCount,
  viewer,
  onCountChange
}: {
  postId: string;
  commentCount: number;
  viewer: Viewer;
  onCountChange: (delta: number) => void;
}) {
  const t = useTranslations("Community.comments");
  const panelId = useId();
  const wide = useMediaQuery(WIDE_QUERY);
  // Dar ekranda açılır bölümün durumu (varsayılan kapalı: kontrolsüz yükleme ve uzun kart yok).
  const [expanded, setExpanded] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const inView = useInView(rootRef);

  const open = wide || expanded;
  const shouldFetch = (wide ? inView : expanded) && commentCount > 0;
  const list = usePagedList(shouldFetch ? `pane:${postId}` : null, (cursor, signal) => fetchComments(postId, cursor, signal, PANE_PAGE));
  const author = useAuthors(
    list.items.map((comment) => comment.authorId),
    viewer.status === "authenticated"
  );

  const settledEmpty = commentCount === 0 || (shouldFetch && !list.loading && !list.error && list.items.length === 0);

  let body;
  if (settledEmpty) {
    body = <p className="py-1 text-sm text-text-secondary">{t("paneEmpty")}</p>;
  } else if (!shouldFetch || list.loading) {
    body = (
      <div role="status" className="space-y-2 motion-safe:animate-pulse">
        <p className="sr-only">{t("paneLoading")}</p>
        <div aria-hidden="true" className="h-3.5 w-3/4 rounded-control bg-surface-elevated" />
        <div aria-hidden="true" className="h-3.5 w-1/2 rounded-control bg-surface-elevated" />
      </div>
    );
  } else if (list.error) {
    body = <ErrorBlock error={list.error} retry={list.reload} />;
  } else {
    body = (
      <div
        role="region"
        aria-label={t("paneListLabel")}
        tabIndex={0}
        className="ltz-thin-scroll max-h-[22rem] min-h-0 overflow-y-auto overscroll-contain pe-2 xl:max-h-none xl:flex-1 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        <CommentThread list={list} postId={postId} viewer={viewer} resolveAuthor={author} onCountChange={onCountChange} depth={0} flat />
        <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
      </div>
    );
  }

  return (
    <div ref={rootRef} className="flex min-h-0 min-w-0 flex-1 flex-col">
      {wide ? (
        <h3 className="mb-2 flex shrink-0 items-center gap-2 font-display text-sm font-semibold text-text-primary">
          {t("paneTitle")}
          <span className="rounded-control border border-border bg-surface-elevated px-1.5 text-xs font-medium tabular-nums text-text-secondary">{commentCount}</span>
        </h3>
      ) : (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={panelId}
          onClick={() => setExpanded((value) => !value)}
          className="-mx-1 flex min-h-11 w-[calc(100%+0.5rem)] items-center justify-between gap-2 rounded-control px-1 text-start font-display text-sm font-semibold text-text-primary transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          <span>
            {t("paneTitle")} <span className="font-medium tabular-nums text-text-secondary">({commentCount})</span>
          </span>
          <Icon name="chevron" className={cn("size-4 shrink-0 text-text-secondary transition-transform motion-reduce:transition-none", expanded && "rotate-180")} />
        </button>
      )}

      {open ? (
        <div id={panelId} className={cn("flex min-w-0 flex-col", wide ? "min-h-0 flex-1" : "mt-1")}>
          {body}
          <div className="mt-auto min-w-0 shrink-0 break-words pt-3">
            <CommentComposer
              viewer={viewer}
              label={t("write")}
              submitLabel={t("send")}
              placeholder={t("placeholder")}
              compact
              hideLabel
              submit={(text) => createComment(postId, text)}
              onCreated={(comment) => {
                onCountChange(1);
                // Liste henüz yüklenmediyse (gönderide yorum yoktu) sayaç artışı yüklemeyi tetikler ve yeni yorum gelir.
                if (shouldFetch) {
                  if (list.hasMore) list.reload();
                  else list.append(comment);
                }
              }}
            />
          </div>
        </div>
      ) : null}
    </div>
  );
}
