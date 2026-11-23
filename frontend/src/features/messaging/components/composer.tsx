"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { usePopover } from "@/shared/hooks/use-popover";
import { cn } from "@/shared/utils/cn";
import { useMessaging } from "../hooks/use-messaging";
import type { PersonView } from "../hooks/use-people";
import { BODY_COUNTER_FROM, MAX_BODY_LENGTH, validateBody, type BodyIssue } from "../schemas/message-body";
import { ImagePrepareError, prepareImage, ACCEPTED_IMAGE_TYPES } from "../services/image-prepare";
import { isAbort, messagingErrorKey } from "../services/messaging-errors";
import type { UploadHandle } from "../services/messaging-http";
import type { Attachment, ConversationId, GifItem, Message } from "../types/messaging.types";
import { EmojiPicker } from "./emoji-picker";
import { GifPicker } from "./gif-picker";
import { useMessageSnippet } from "./message-parts";

type Upload =
  | { state: "preparing"; previewUrl: string }
  | { state: "uploading"; previewUrl: string; progress: number; handle: UploadHandle<Attachment> }
  | { state: "ready"; previewUrl: string; attachment: Attachment }
  | { state: "failed"; previewUrl: string | null; errorKey: string };

type ComposerProps = {
  conversationId: ConversationId;
  replyTo: Message | null;
  replyAuthor: PersonView | null;
  onCancelReply: () => void;
  /** Gönderimden sonra listeyi alta kaydırmak için. */
  onSent: () => void;
};

/**
 * Mesaj yazma alanı (MSG-06, MSG-17, MSG-18, MSG-19, MSG-22). Enter gönderir, Shift+Enter satır ekler (IME bileşimi sırasında
 * gönderilmez). Görsel seçilince istemcide küçültülür (≤1600 px, ≤1 MB) ve hemen yüklenir; ilerleme ve iptal gösterilir
 * (web-rules §7.2). Yüklenen görsel isteğe bağlı açıklamayla gönderilir; GIF seçilince doğrudan gönderilir.
 */
export function Composer({ conversationId, replyTo, replyAuthor, onCancelReply, onSent }: ComposerProps) {
  const t = useTranslations("Messaging");
  const { api, send } = useMessaging();
  const snippet = useMessageSnippet();
  const [body, setBody] = useState("");
  const [issue, setIssue] = useState<BodyIssue | null>(null);
  const [upload, setUpload] = useState<Upload | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const errorId = useId();
  const counterId = useId();
  const { open: emojiOpen, setOpen: setEmojiOpen, close: closeEmoji, rootRef: emojiRoot, triggerRef: emojiTrigger } = usePopover();
  const { open: gifOpen, setOpen: setGifOpen, close: closeGif, rootRef: gifRoot, triggerRef: gifTrigger } = usePopover();

  // Sohbet değişince taslak ve yükleme sıfırlanır (başka sohbete yanlışlıkla gönderilmez).
  const [shownFor, setShownFor] = useState(conversationId);
  if (shownFor !== conversationId) {
    setShownFor(conversationId);
    setBody("");
    setIssue(null);
    setUpload(null);
  }

  useEffect(() => {
    if (replyTo) textareaRef.current?.focus();
  }, [replyTo]);

  // Önizleme adresi bellekte tutulur; yükleme bırakılınca serbest bırakılır.
  const previewUrl = upload?.previewUrl ?? null;
  useEffect(() => {
    if (!previewUrl) return;
    return () => {
      // Gönderilen mesaj iyimser görünümde aynı adresi kısa süre kullanır; sunucu kaydı gelince bırakılır.
      setTimeout(() => URL.revokeObjectURL(previewUrl), 60_000);
    };
  }, [previewUrl]);

  function insertEmoji(value: string) {
    const element = textareaRef.current;
    const start = element?.selectionStart ?? body.length;
    const end = element?.selectionEnd ?? body.length;
    const next = body.slice(0, start) + value + body.slice(end);
    if (next.length > MAX_BODY_LENGTH) return;
    setBody(next);
    requestAnimationFrame(() => {
      element?.focus();
      element?.setSelectionRange(start + value.length, start + value.length);
    });
  }

  async function onFile(file: File | undefined) {
    if (!file) return;
    if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
      setUpload({ state: "failed", previewUrl: null, errorKey: "unsupportedImage" });
      return;
    }
    const localUrl = URL.createObjectURL(file);
    setUpload({ state: "preparing", previewUrl: localUrl });
    try {
      const prepared = await prepareImage(file);
      const handle = api.upload(conversationId, prepared.blob, (progress) =>
        setUpload((current) => (current?.state === "uploading" && current.handle === handle ? { ...current, progress } : current))
      );
      setUpload({ state: "uploading", previewUrl: localUrl, progress: 0, handle });
      const attachment = await handle.promise;
      setUpload((current) => (current?.state === "uploading" && current.handle === handle ? { state: "ready", previewUrl: localUrl, attachment } : current));
      textareaRef.current?.focus();
    } catch (error) {
      if (isAbort(error)) return;
      const errorKey = error instanceof ImagePrepareError ? error.key : messagingErrorKey(error);
      setUpload((current) => (current && current.previewUrl === localUrl ? { state: "failed", previewUrl: localUrl, errorKey } : current));
    }
  }

  function cancelUpload() {
    if (upload?.state === "uploading") upload.handle.abort();
    setUpload(null);
    fileRef.current?.focus();
  }

  function submit() {
    const ready = upload?.state === "ready" ? upload : null;
    if (upload && !ready) return;
    const problem = validateBody(body, { optional: Boolean(ready) });
    if (problem) {
      setIssue(body.length === 0 && !ready ? null : problem);
      return;
    }
    send(conversationId, {
      body: body.length > 0 ? body : undefined,
      attachment: ready?.attachment,
      previewUrl: ready?.previewUrl,
      replyTo: replyTo ?? undefined
    });
    setBody("");
    setIssue(null);
    setUpload(null);
    onCancelReply();
    onSent();
    textareaRef.current?.focus();
  }

  function sendGif(item: GifItem) {
    closeGif();
    send(conversationId, { gif: item, replyTo: replyTo ?? undefined });
    onCancelReply();
    onSent();
    textareaRef.current?.focus();
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    submit();
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
    if (event.key === "Escape" && replyTo) {
      event.preventDefault();
      onCancelReply();
    }
  }

  const uploading = upload?.state === "uploading" || upload?.state === "preparing";
  const canSend = upload ? upload.state === "ready" : body.trim().length > 0;
  const showCounter = body.length >= BODY_COUNTER_FROM;

  return (
    <form onSubmit={onSubmit} className="border-t border-border bg-surface p-3" aria-label={t("composer.label")}>
      {replyTo ? (
        <div className="mb-2 flex items-start gap-3 rounded-panel border-s-2 border-primary bg-surface-elevated px-3 py-2">
          <Icon name="reply" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-brand-text">{t("composer.replyingTo", { name: replyAuthor?.name ?? "" })}</p>
            <p className="truncate text-sm text-text-secondary">{snippet(replyTo)}</p>
          </div>
          <button type="button" onClick={onCancelReply} aria-label={t("composer.cancelReply")} className={cn(iconButtonClass, "size-8 sm:size-8")}>
            <Icon name="close" className="size-4" />
          </button>
        </div>
      ) : null}

      {upload ? (
        <div className="mb-2 flex items-center gap-3 rounded-panel border border-border bg-surface-elevated p-2">
          {upload.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- yerel blob önizlemesi
            <img src={upload.previewUrl} alt="" className="size-16 shrink-0 rounded-control object-cover" />
          ) : (
            <span className="inline-flex size-16 shrink-0 items-center justify-center rounded-control bg-surface text-text-secondary">
              <Icon name="image" className="size-6" />
            </span>
          )}
          <div className="min-w-0 flex-1 text-sm">
            {upload.state === "failed" ? (
              <p role="alert" className="text-brand-text">{t(`errors.${upload.errorKey}`)}</p>
            ) : upload.state === "ready" ? (
              <p className="text-text-secondary">{t("composer.imageReady")}</p>
            ) : (
              <>
                <p className="text-text-secondary">{upload.state === "preparing" ? t("composer.preparing") : t("composer.uploading")}</p>
                <progress
                  className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full accent-primary"
                  max={1}
                  value={upload.state === "uploading" ? upload.progress : undefined}
                  aria-label={t("composer.uploadProgress")}
                />
              </>
            )}
          </div>
          <button type="button" onClick={cancelUpload} aria-label={uploading ? t("composer.cancelUpload") : t("composer.removeImage")} className={cn(iconButtonClass, "size-8 sm:size-8")}>
            <Icon name="close" className="size-4" />
          </button>
        </div>
      ) : null}

      <div className="flex items-end gap-1.5">
        <div ref={emojiRoot} className="relative">
          <button
            ref={emojiTrigger}
            type="button"
            aria-label={t("composer.emoji")}
            aria-haspopup="dialog"
            aria-expanded={emojiOpen}
            onClick={() => {
              closeGif();
              setEmojiOpen(!emojiOpen);
            }}
            className={iconButtonClass}
          >
            <Icon name="smile" />
          </button>
          {emojiOpen ? (
            <div role="dialog" aria-label={t("composer.emoji")} className="absolute bottom-full start-0 z-30 mb-2">
              <EmojiPicker onPick={insertEmoji} />
            </div>
          ) : null}
        </div>
        <div ref={gifRoot} className="relative">
          <button
            ref={gifTrigger}
            type="button"
            aria-label={t("composer.gif")}
            aria-haspopup="dialog"
            aria-expanded={gifOpen}
            disabled={Boolean(upload)}
            onClick={() => {
              closeEmoji();
              setGifOpen(!gifOpen);
            }}
            className={cn(iconButtonClass, "disabled:opacity-50")}
          >
            <Icon name="gif" />
          </button>
          {gifOpen ? (
            <div role="dialog" aria-label={t("composer.gif")} className="absolute bottom-full start-0 z-30 mb-2 max-sm:-start-12">
              <GifPicker onPick={sendGif} />
            </div>
          ) : null}
        </div>
        <button
          type="button"
          aria-label={t("composer.image")}
          disabled={uploading}
          onClick={() => fileRef.current?.click()}
          className={cn(iconButtonClass, "disabled:opacity-50")}
        >
          <Icon name="image" />
        </button>
        <input
          ref={fileRef}
          type="file"
          accept={ACCEPTED_IMAGE_TYPES.join(",")}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(event) => {
            void onFile(event.target.files?.[0]);
            event.target.value = "";
          }}
        />

        <label className="sr-only" htmlFor={`${errorId}-input`}>
          {t("composer.placeholder")}
        </label>
        <textarea
          id={`${errorId}-input`}
          ref={textareaRef}
          rows={1}
          value={body}
          maxLength={MAX_BODY_LENGTH}
          placeholder={upload?.state === "ready" ? t("composer.captionPlaceholder") : t("composer.placeholder")}
          aria-invalid={issue ? true : undefined}
          aria-describedby={cn(issue && errorId, showCounter && counterId) || undefined}
          onChange={(event) => {
            setBody(event.target.value);
            if (issue) setIssue(null);
          }}
          onKeyDown={onKeyDown}
          className="max-h-40 min-h-10 flex-1 resize-none rounded-xl border border-transparent bg-surface-elevated px-3.5 py-2 text-sm text-text-primary [field-sizing:content] placeholder:text-text-secondary hover:border-control-border focus-visible:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus aria-invalid:border-primary"
        />
        <button
          type="submit"
          disabled={!canSend}
          aria-label={t("composer.send")}
          className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground transition-colors hover:bg-primary/88 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon name="send" />
        </button>
      </div>
      <div className="flex justify-between gap-3 px-1">
        <p id={errorId} role={issue ? "alert" : undefined} className="text-xs text-brand-text">
          {issue ? t(`composer.issue.${issue}`) : null}
        </p>
        {showCounter ? (
          <p id={counterId} className={cn("shrink-0 text-xs", body.length >= MAX_BODY_LENGTH ? "text-brand-text" : "text-text-secondary")}>
            {t("composer.counter", { count: body.length, max: MAX_BODY_LENGTH })}
          </p>
        ) : null}
      </div>
    </form>
  );
}
