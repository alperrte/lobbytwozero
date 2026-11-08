"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";

import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { useLoginRedirect } from "../hooks/use-login-redirect";
import type { Viewer } from "../hooks/use-viewer";
import { deletePost, likePost, repostPost, unlikePost } from "../services/community-api";
import { statusOf } from "../services/community-errors";
import { LIMITS, validateText } from "../schemas/community.schema";
import type { PostView } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import {
  actionClass,
  ActionError,
  buttonClass,
  ConfirmDialog,
  FieldError,
  inputClass,
  ModalDialog,
  primaryButtonClass
} from "./community-ui";
import { CommentsSection } from "./comments-section";
import { PostActionsMenu, type PostAction } from "./post-actions-menu";
import { PostCardShell } from "./post-card-shell";
import { PostContent } from "./post-content";
import { PostImages, type Lookups } from "./post-parts";
import { ReportDialog, type ReportTarget } from "./report-dialog";

export type PostCardProps = {
  post: PostView;
  viewer: Viewer;
  lookups: Lookups;
  /** Beğeni gibi yerinde değişikliklerde güncel gönderi. */
  onChange: (post: PostView) => void;
  /** Gönderi silindi (liste bunu kaldırır). */
  onRemoved: (postId: string) => void;
  /** Yeni repost oluştu. */
  onReposted?: (post: PostView) => void;
  /** Detay sayfası: tam metin; yorumlar sayfanın kendi bölümündedir (kart içi yorum bölümü yoktur). */
  detail?: boolean;
  /** Verilirse "Düzenle" aynı sayfada düzenleyiciyi açar (aksi halde detay sayfasına `?edit=1` ile gider). */
  onEdit?: () => void;
};

/**
 * Gönderi kartı: Akış, Gönderilerim, oyuncu gönderileri ve forum konuları AYNI bileşeni kullanır (ortak `PostCardShell`). Sıra:
 * kullanıcı ve tarih → metin → görseller → etkileşim satırı. Yorum ikonu kartın altında yorum bölümünü açar/kapatır (kapalıyken
 * yer kaplamaz, yorumlar yalnız açılınca yüklenir). Yetki kararı sunucudadır; "Düzenle/Sil" yalnız sahibe gösterilir (arayüz ipucu), sunucu 403/404 verir.
 * Sayaçlar yalnız sunucu yanıtıyla değişir: başarısız beğeni/repost sayaçları oynatmaz ve başarı mesajı göstermez.
 */
export function PostCard({ post, viewer, lookups, onChange, onRemoved, onReposted, detail = false, onEdit }: PostCardProps) {
  const t = useTranslations("Community");
  const headingId = useId();
  const goLogin = useLoginRedirect();
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);
  const [repostOpen, setRepostOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [commentsOpen, setCommentsOpen] = useState(false);
  const commentsId = useId();
  const tm = useTranslations("Community.mine.card");
  const [notice, setNotice] = useState<string | null>(null);

  const isOwner = viewer.accountId !== null && viewer.accountId === post.authorId;
  const isRepost = post.repostOf !== null;
  const guest = viewer.status !== "authenticated";
  const detailHref = { pathname: "/community/posts/[postId]", params: { postId: post.id } } as const;

  const like = useAction(async () => {
    const state = await (post.likedByMe ? unlikePost(post.id) : likePost(post.id));
    onChange({ ...post, likedByMe: state.liked, likeCount: state.likeCount });
  });
  const remove = useAction(async () => {
    await deletePost(post.id);
    setDeleteOpen(false);
    onRemoved(post.id);
  });

  const hasMedia = post.images.length > 0;
  // Yeniden paylaşımı silmek, paylaşımı geri almaktır.
  const undoRepost = isOwner && isRepost;

  // Menüde yalnız kullanıcının yetkisi olan işlemler bulunur (sunucu yine 403/404 ile korur). Düzenle/Sil yalnız sahibe,
  // Şikayet et başkasının görünür gönderisine gösterilir (rapor ve bildirim aynı işlemdir, tek seçenek).
  const actions: PostAction[] = [];
  if (isOwner && !isRepost && post.status === "VISIBLE") {
    actions.push(
      onEdit
        ? { id: "edit", label: t("post.edit"), onSelect: onEdit }
        : { id: "edit", label: t("post.edit"), href: { pathname: "/community/posts/[postId]", params: { postId: post.id }, query: { edit: "1" } } }
    );
  }
  if (isOwner) {
    actions.push({ id: "delete", label: undoRepost ? tm("undoRepost") : t("post.delete"), icon: undoRepost ? "repost" : "trash", onSelect: () => setDeleteOpen(true) });
  } else if (post.status === "VISIBLE") {
    actions.push({ id: "report", label: t("post.reportMenu"), onSelect: () => (guest ? goLogin() : setReportTarget({ kind: "post", id: post.id })) });
  }

  return (
    <PostCardShell labelledBy={headingId}>
      <PostContent
        headingId={headingId}
        lookups={lookups}
        authorId={post.authorId}
        createdAt={post.createdAt}
        edited={post.updatedAt !== post.createdAt && post.status === "VISIBLE" && !isRepost}
        gameId={post.gameId}
        contentType={post.contentType}
        category={post.category}
        hidden={post.status === "HIDDEN"}
        title={post.title}
        titleHref={detail ? undefined : detailHref}
        body={post.body}
        clamp={!detail}
        repostOf={post.repostOf}
        media={hasMedia ? <PostImages images={post.images} variant={detail ? "detail" : "feed"} /> : null}
        actions={<PostActionsMenu actions={actions} />}
      />

      <footer className="mt-4 flex flex-wrap items-center gap-1 border-t border-border pt-2">
        <button
          type="button"
          className={cn(actionClass, post.likedByMe && "text-brand-text")}
          aria-pressed={post.likedByMe}
          aria-label={post.likedByMe ? t("post.unlikeAria", { count: post.likeCount }) : t("post.likeAria", { count: post.likeCount })}
          disabled={like.pending}
          onClick={() => (guest ? goLogin() : void like.run())}
        >
          <CommunityIcon name="heart" filled={post.likedByMe} className="size-5" />
          <span aria-hidden="true">{post.likeCount}</span>
        </button>

        {detail ? (
          <span className={cn(actionClass, "pointer-events-none")}>
            <CommunityIcon name="comment" className="size-5" />
            <span className="sr-only">{t("post.commentsLabel")}</span>
            {post.commentCount}
          </span>
        ) : (
          <button
            type="button"
            className={cn(actionClass, commentsOpen && "text-brand-text")}
            aria-expanded={commentsOpen}
            aria-controls={commentsId}
            aria-label={`${commentsOpen ? t("post.commentsClose") : t("post.commentsOpen")} (${t("post.commentsAria", { count: post.commentCount })})`}
            onClick={() => setCommentsOpen((value) => !value)}
          >
            <CommunityIcon name="comment" className="size-5" />
            <span aria-hidden="true">{post.commentCount}</span>
          </button>
        )}

        {post.status === "VISIBLE" ? (
          <button type="button" className={actionClass} onClick={() => (guest ? goLogin() : setRepostOpen(true))}>
            <CommunityIcon name="repost" className="size-5" />
            {t("post.repost")}
          </button>
        ) : null}

      </footer>

      {like.error ? (
        <div className="mt-2">
          <ActionError error={like.error} />
        </div>
      ) : null}
      {notice ? (
        <p role="status" className="mt-2 text-sm text-text-primary">
          {notice}
        </p>
      ) : null}

      {commentsOpen && !detail ? (
        <div id={commentsId}>
          <CommentsSection
            postId={post.id}
            viewer={viewer}
            embedded
            onCountChange={(delta) => onChange({ ...post, commentCount: Math.max(0, post.commentCount + delta) })}
          />
        </div>
      ) : null}

      <RepostDialog
        open={repostOpen}
        postId={post.id}
        onClose={() => setRepostOpen(false)}
        onDone={(created) => {
          setRepostOpen(false);
          setNotice(t("post.repostDone"));
          onReposted?.(created);
        }}
      />
      <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />
      <ConfirmDialog
        open={deleteOpen}
        title={undoRepost ? tm("undoTitle") : t("post.deleteTitle")}
        description={undoRepost ? tm("undoBody") : t("post.deleteBody")}
        confirmLabel={undoRepost ? tm("undoConfirm") : t("post.delete")}
        pending={remove.pending}
        error={remove.error}
        onConfirm={() => void remove.run()}
        onClose={() => {
          remove.clearError();
          setDeleteOpen(false);
        }}
      />
    </PostCardShell>
  );
}

function RepostDialog({ open, postId, onClose, onDone }: { open: boolean; postId: string; onClose: () => void; onDone: (post: PostView) => void }) {
  const t = useTranslations("Community");
  return (
    <ModalDialog open={open} onClose={onClose} title={t("post.repostTitle")}>
      {open ? <RepostForm key={postId} postId={postId} onClose={onClose} onDone={onDone} /> : null}
    </ModalDialog>
  );
}

function RepostForm({ postId, onClose, onDone }: { postId: string; onClose: () => void; onDone: (post: PostView) => void }) {
  const t = useTranslations("Community");
  const id = useId();
  const [note, setNote] = useState("");
  const send = useAction((comment: string) => repostPost(postId, comment));
  const issue = validateText(note, LIMITS.repostNote, { required: false });

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (issue) return;
    const result = await send.run(note.trim());
    if (result.ok) onDone(result.value);
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <p className="text-sm leading-6 text-text-secondary">{t("post.repostHelp")}</p>
      <div>
        <label htmlFor={`${id}-note`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("post.repostNote")} <span className="font-normal text-text-secondary">({t("form.optional")})</span>
        </label>
        <textarea
          id={`${id}-note`}
          rows={3}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          aria-invalid={issue ? true : undefined}
          aria-describedby={issue ? `${id}-error` : undefined}
          className={`${inputClass} resize-y`}
        />
        <p className="mt-1 text-xs text-text-secondary">{note.trim().length}/{LIMITS.repostNote}</p>
        {issue ? <FieldError id={`${id}-error`}>{t(`form.error.${issue}`, { max: LIMITS.repostNote })}</FieldError> : null}
      </div>
      {statusOf(send.error) === 409 ? (
        <p role="alert" className="rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-sm text-text-primary">
          {t("post.repostAlready")}
        </p>
      ) : (
        <ActionError error={send.error} />
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <button type="button" className={buttonClass} onClick={onClose} disabled={send.pending}>
          {t("state.cancel")}
        </button>
        <button type="submit" className={primaryButtonClass} disabled={send.pending}>
          {send.pending ? t("state.working") : t("post.repostSubmit")}
        </button>
      </div>
    </form>
  );
}
