"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { useAuthors } from "../hooks/use-directory";
import { useLoginRedirect } from "../hooks/use-login-redirect";
import { usePagedList } from "../hooks/use-paged-list";
import type { Viewer } from "../hooks/use-viewer";
import { createComment, deleteComment, fetchComments, fetchReplies, updateComment } from "../services/community-api";
import { LIMITS, validateText } from "../schemas/community.schema";
import type { CommentView } from "../types/community.types";
import { CommunityIcon } from "./community-icons";
import {
  actionClass,
  ActionError,
  buttonClass,
  ConfirmDialog,
  EmptyBlock,
  ErrorBlock,
  FieldError,
  inputClass,
  LoadMore,
  LoadingBlock,
  primaryButtonClass
} from "./community-ui";
import { AuthorLine } from "./post-parts";
import { ReportDialog, type ReportTarget } from "./report-dialog";

type CommentListState = ReturnType<typeof usePagedList<CommentView>>;

const MAX_INDENT_DEPTH = 3;

/**
 * Gönderi yorumları: üst düzey yorumlar eskiden yeniye (imleçli); yanıtlar istendiğinde ayrı uçtan yüklenir ve sınırsız
 * iç içe olabilir. Silinen yorum yanıtı varsa yer tutucu olarak kalır. Yorum beğenisi API'de yoktur ve gösterilmez.
 */
export function CommentsSection({
  postId,
  viewer,
  onCountChange
}: {
  postId: string;
  viewer: Viewer;
  /** Gönderinin toplam yorum sayacına eklenecek fark (yeni yorum +1, silinen -1). */
  onCountChange: (delta: number) => void;
}) {
  const t = useTranslations("Community.comments");
  const headingId = useId();
  const list = usePagedList(`comments:${postId}`, (cursor, signal) => fetchComments(postId, cursor, signal));
  const author = useAuthors(
    list.items.map((comment) => comment.authorId),
    viewer.status === "authenticated"
  );

  return (
    <section aria-labelledby={headingId} className="mt-8">
      <h2 id={headingId} className="mb-4 font-display text-xl font-semibold text-text-primary">
        {t("title")}
      </h2>

      <CommentComposer
        viewer={viewer}
        label={t("write")}
        submitLabel={t("send")}
        placeholder={t("placeholder")}
        submit={(body) => createComment(postId, body)}
        onCreated={(comment) => {
          onCountChange(1);
          if (list.hasMore) list.reload();
          else list.append(comment);
        }}
      />

      <div className="mt-5">
        {list.loading ? <LoadingBlock rows={2} /> : null}
        {list.error ? <ErrorBlock error={list.error} retry={list.reload} /> : null}
        {!list.loading && !list.error ? (
          list.items.length === 0 ? (
            <EmptyBlock title={t("emptyTitle")} description={t("emptyBody")} />
          ) : (
            <>
              <CommentThread list={list} postId={postId} viewer={viewer} resolveAuthor={author} onCountChange={onCountChange} depth={0} />
              <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
            </>
          )
        ) : null}
      </div>
    </section>
  );
}

export type ThreadProps = {
  list: CommentListState;
  postId: string;
  viewer: Viewer;
  resolveAuthor: ReturnType<typeof useAuthors>;
  onCountChange: (delta: number) => void;
  depth: number;
  /** Yanıt listesinin sahibi olan üst yorum: silinen yanıt üst yorumun sayacını düşürür. */
  onChildRemoved?: () => void;
  /** Kart içi yorum bölmesi: kutusuz, ince ayırıcılı düz görünüm (kart içinde kart oluşmasın). */
  flat?: boolean;
};

export function CommentThread({ list, postId, viewer, resolveAuthor, onCountChange, depth, onChildRemoved, flat = false }: ThreadProps) {
  return (
    <ul className={cn(flat ? "divide-y divide-border/60" : "space-y-3", depth > 0 && !flat && "mt-3")}>
      {list.items.map((comment) => (
        <li key={comment.id}>
          <CommentItem
            comment={comment}
            postId={postId}
            viewer={viewer}
            resolveAuthor={resolveAuthor}
            depth={depth}
            flat={flat}
            onCountChange={onCountChange}
            onChange={(next) => list.update(comment.id, () => next)}
            onRemove={(placeholder) => {
              onChildRemoved?.();
              if (placeholder) list.update(comment.id, (current) => ({ ...current, status: "DELETED", body: null, authorId: null }));
              else list.remove(comment.id);
            }}
          />
        </li>
      ))}
    </ul>
  );
}

function CommentItem({
  comment,
  postId,
  viewer,
  resolveAuthor,
  depth,
  flat,
  onChange,
  onRemove,
  onCountChange
}: {
  comment: CommentView;
  postId: string;
  viewer: Viewer;
  resolveAuthor: ReturnType<typeof useAuthors>;
  depth: number;
  flat: boolean;
  onChange: (comment: CommentView) => void;
  onRemove: (placeholder: boolean) => void;
  onCountChange: (delta: number) => void;
}) {
  const t = useTranslations("Community.comments");
  const goLogin = useLoginRedirect();
  const [expanded, setExpanded] = useState(false);
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);

  // Kapalıyken ağ isteği atılmaz; açılınca anahtar değişir ve yanıtlar baştan yüklenir.
  const replies = usePagedList(expanded ? `replies:${comment.id}` : `replies:${comment.id}:closed`, (cursor, signal) =>
    expanded ? fetchReplies(comment.id, cursor, signal) : Promise.resolve({ items: [], nextCursor: null })
  );
  const replyAuthors = useAuthors(
    replies.items.map((reply) => reply.authorId),
    viewer.status === "authenticated"
  );

  const deleted = comment.status === "DELETED";
  const isOwner = viewer.accountId !== null && viewer.accountId === comment.authorId && !deleted;
  const guest = viewer.status !== "authenticated";
  const lookups = { author: resolveAuthor, game: () => null };

  const remove = useAction(async () => {
    await deleteComment(comment.id);
    setDeleteOpen(false);
    onCountChange(-1);
    onRemove(comment.replyCount > 0);
  });

  return (
    <div className={cn(depth > 0 && depth <= MAX_INDENT_DEPTH && "border-s-2 border-border ps-3 sm:ps-4")}>
      <div className={flat ? "py-3" : "rounded-panel border border-border bg-surface p-3.5 sm:p-4"}>
        {deleted ? (
          <p className="text-sm italic text-text-secondary">{t("deleted")}</p>
        ) : (
          <>
            <AuthorLine authorId={comment.authorId} lookups={lookups} createdAt={comment.createdAt} size="sm" trailing={comment.updatedAt !== comment.createdAt ? <span className="text-xs text-text-secondary">· {t("edited")}</span> : null} />
            {editing ? (
              <CommentEditor
                initial={comment.body ?? ""}
                onCancel={() => setEditing(false)}
                onSaved={(saved) => {
                  onChange({ ...comment, body: saved.body, updatedAt: saved.updatedAt });
                  setEditing(false);
                }}
                commentId={comment.id}
              />
            ) : (
              <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-text-primary">{comment.body}</p>
            )}
          </>
        )}

        {!deleted && !editing ? (
          <div className="mt-1 flex flex-wrap items-center gap-1">
            <button type="button" className={actionClass} aria-expanded={replying} onClick={() => (guest ? goLogin() : setReplying((value) => !value))}>
              <CommunityIcon name="reply" className="size-4" />
              {t("reply")}
            </button>
            {isOwner ? (
              <>
                <button type="button" className={actionClass} onClick={() => setEditing(true)}>
                  <CommunityIcon name="pencil" className="size-4" />
                  {t("edit")}
                </button>
                <button type="button" className={actionClass} onClick={() => setDeleteOpen(true)}>
                  <CommunityIcon name="trash" className="size-4" />
                  {t("delete")}
                </button>
              </>
            ) : (
              <button type="button" className={actionClass} onClick={() => (guest ? goLogin() : setReportTarget({ kind: "comment", id: comment.id }))}>
                <CommunityIcon name="flag" className="size-4" />
                {t("report")}
              </button>
            )}
          </div>
        ) : null}

        {replying ? (
          <div className="mt-2">
            <CommentComposer
              viewer={viewer}
              label={t("replyLabel")}
              submitLabel={t("sendReply")}
              placeholder={t("replyPlaceholder")}
              compact
              onCancel={() => setReplying(false)}
              submit={(body) => createComment(postId, body, comment.id)}
              onCreated={(created) => {
                onCountChange(1);
                onChange({ ...comment, replyCount: comment.replyCount + 1 });
                setReplying(false);
                if (expanded && !replies.hasMore) replies.append(created);
                else if (expanded) replies.reload();
                else setExpanded(true);
              }}
            />
          </div>
        ) : null}

        {comment.replyCount > 0 ? (
          <button
            type="button"
            className={cn(actionClass, "mt-1 text-brand-text")}
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            {expanded ? t("hideReplies") : t("showReplies", { count: comment.replyCount })}
          </button>
        ) : null}
      </div>

      {expanded ? (
        <div>
          {replies.loading ? <div className="mt-3"><LoadingBlock rows={1} /></div> : null}
          {replies.error ? <div className="mt-3"><ErrorBlock error={replies.error} retry={replies.reload} /></div> : null}
          {!replies.loading && !replies.error ? (
            <>
              <CommentThread
                list={replies}
                postId={postId}
                viewer={viewer}
                resolveAuthor={replyAuthors}
                onCountChange={onCountChange}
                depth={depth + 1}
                flat={flat}
                onChildRemoved={() => onChange({ ...comment, replyCount: Math.max(0, comment.replyCount - 1) })}
              />
              <LoadMore hasMore={replies.hasMore} loading={replies.loadingMore} error={replies.moreError} onMore={replies.loadMore} />
            </>
          ) : null}
        </div>
      ) : null}

      <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />
      <ConfirmDialog
        open={deleteOpen}
        title={t("deleteTitle")}
        description={t("deleteBody")}
        confirmLabel={t("delete")}
        pending={remove.pending}
        error={remove.error}
        onConfirm={() => void remove.run()}
        onClose={() => {
          remove.clearError();
          setDeleteOpen(false);
        }}
      />
    </div>
  );
}

/** Yorum / yanıt yazma formu. Misafire giriş çağrısı gösterir. Başarı yalnız sunucu 201 döndüğünde işlenir. */
export function CommentComposer({
  viewer,
  label,
  submitLabel,
  placeholder,
  compact = false,
  hideLabel = false,
  submit,
  onCreated,
  onCancel
}: {
  viewer: Viewer;
  label: string;
  submitLabel: string;
  placeholder: string;
  compact?: boolean;
  /** Etiket yalnız ekran okuyucuya görünür (dar yorum bölmesi). */
  hideLabel?: boolean;
  submit: (body: string) => Promise<CommentView>;
  onCreated: (comment: CommentView) => void;
  onCancel?: () => void;
}) {
  const t = useTranslations("Community");
  const id = useId();
  const goLogin = useLoginRedirect();
  const [body, setBody] = useState("");
  const [touched, setTouched] = useState(false);
  const send = useAction(submit);
  const issue = validateText(body, LIMITS.comment);

  if (viewer.status !== "authenticated") {
    return (
      <p className="rounded-control border border-border bg-surface px-4 py-3 text-sm text-text-secondary">
        {viewer.status === "loading" ? t("state.loading") : (
          <button type="button" onClick={goLogin} className="font-semibold text-brand-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
            {t("comments.loginPrompt")}
          </button>
        )}
      </p>
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (issue) return;
    const result = await send.run(body.trim());
    if (result.ok) {
      setBody("");
      setTouched(false);
      onCreated(result.value);
    }
  }

  const submitButton = (
    <button type="submit" className={cn(primaryButtonClass, hideLabel && "shrink-0")} disabled={send.pending}>
      <CommunityIcon name="send" className="size-4" />
      {send.pending ? t("state.working") : submitLabel}
    </button>
  );
  const field = (
    <textarea
      id={`${id}-body`}
      rows={hideLabel ? 1 : compact ? 2 : 3}
      value={body}
      onChange={(event) => setBody(event.target.value)}
      placeholder={placeholder}
      aria-invalid={touched && issue ? true : undefined}
      aria-describedby={touched && issue ? `${id}-error` : undefined}
      className={cn(inputClass, "resize-y", hideLabel && "max-h-32 min-h-11 flex-1 resize-none [field-sizing:content]")}
    />
  );

  return (
    <form onSubmit={onSubmit} noValidate className={hideLabel ? "space-y-1" : "space-y-2"}>
      <label htmlFor={`${id}-body`} className={hideLabel ? "sr-only" : "block text-sm font-medium text-text-primary"}>
        {label}
      </label>
      {hideLabel ? (
        <>
          {/* Dar yorum bölmesi: tek satırlık alan + yanında gönder. Sayaç her zaman görünür ve yeri baştan ayrılıdır
              (sabit yükseklik), böylece yazma/odak/temizleme formun ve kartın yüksekliğini değiştirmez. */}
          <div className="flex items-end gap-2">
            {field}
            {submitButton}
          </div>
          <p className="h-5 text-start text-xs leading-5 tabular-nums text-text-secondary">
            {body.trim().length}/{LIMITS.comment}
          </p>
        </>
      ) : (
        <>
          {field}
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs text-text-secondary">{body.trim().length}/{LIMITS.comment}</p>
            <div className="flex gap-2">
              {onCancel ? (
                <button type="button" className={buttonClass} onClick={onCancel} disabled={send.pending}>
                  {t("state.cancel")}
                </button>
              ) : null}
              {submitButton}
            </div>
          </div>
        </>
      )}
      {touched && issue ? <FieldError id={`${id}-error`}>{t(`form.error.${issue}`, { max: LIMITS.comment })}</FieldError> : null}
      <ActionError error={send.error} />
    </form>
  );
}

function CommentEditor({ commentId, initial, onSaved, onCancel }: { commentId: string; initial: string; onSaved: (comment: CommentView) => void; onCancel: () => void }) {
  const t = useTranslations("Community");
  const id = useId();
  const [body, setBody] = useState(initial);
  const save = useAction((text: string) => updateComment(commentId, text));
  const issue = validateText(body, LIMITS.comment);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (issue) return;
    const result = await save.run(body.trim());
    if (result.ok) onSaved(result.value);
  }

  return (
    <form onSubmit={onSubmit} noValidate className="mt-2 space-y-2">
      <label htmlFor={`${id}-body`} className="sr-only">
        {t("comments.edit")}
      </label>
      <textarea id={`${id}-body`} rows={3} value={body} onChange={(event) => setBody(event.target.value)} aria-invalid={issue ? true : undefined} aria-describedby={issue ? `${id}-error` : undefined} className={`${inputClass} resize-y`} />
      {issue ? <FieldError id={`${id}-error`}>{t(`form.error.${issue}`, { max: LIMITS.comment })}</FieldError> : null}
      <ActionError error={save.error} />
      <div className="flex justify-end gap-2">
        <button type="button" className={buttonClass} onClick={onCancel} disabled={save.pending}>
          {t("state.cancel")}
        </button>
        <button type="submit" className={primaryButtonClass} disabled={save.pending}>
          {save.pending ? t("state.working") : t("comments.save")}
        </button>
      </div>
    </form>
  );
}
