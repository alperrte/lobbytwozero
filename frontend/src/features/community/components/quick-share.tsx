"use client";

import { useId, useState, type FormEvent, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { useAuthors } from "../hooks/use-directory";
import type { Viewer } from "../hooks/use-viewer";
import { createPost } from "../services/community-api";
import { failPublish, startPublish } from "../services/publish-flow";
import { LIMITS, validateText } from "../schemas/community.schema";
import { CommunityIcon } from "./community-icons";
import { ActionError, Avatar, authorLabel, FieldError, panel, primaryButtonClass } from "./community-ui";

/** Sayaç yalnız sınıra yaklaşınca görünür. */
const COUNTER_FROM = 4500;

/**
 * Akışta doğrudan metin paylaşımı (Twitter benzeri). Başlık, görsel ya da kategori sormaz: yalnız `body` ile
 * `POST /posts` çağrılır (başlıksız, türsüz düz gönderi; backend bunu destekler). Boş ya da yalnız boşluk içeren metin
 * paylaşılamaz; istek sürerken tekrar gönderim engellenir (tam ekran yükleme katmanı da arka planı kilitler). Başarıda metin
 * temizlenir ve `onPublished` listeyi sunucudan yeniler (yerel kopya eklenmez, kart yinelenmez); hatada metin korunur.
 * Kapsamlı oluşturma (başlık, görsel, oyun, tür) üstteki "Gönderi oluştur" ekranındadır.
 */
export function QuickShare({ viewer, onPublished }: { viewer: Viewer; onPublished: (postId: string) => void }) {
  const t = useTranslations("Community");
  const id = useId();
  const [body, setBody] = useState("");
  const author = useAuthors([viewer.accountId], viewer.status === "authenticated")(viewer.accountId);
  const send = useAction((text: string) => createPost({ body: text }));

  const guest = viewer.status === "guest";
  const label = guest ? "?" : authorLabel(author, t("author.you"));
  const length = body.trim().length;
  const issue = length === 0 ? null : validateText(body, LIMITS.postBody);
  const canSubmit = viewer.status === "authenticated" && length > 0 && issue === null && !send.pending;

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!canSubmit) return;
    // Tam ekran yükleme katmanı açılır; zaten bir paylaşım sürüyorsa ikincisi başlamaz.
    if (!startPublish()) return;
    const result = await send.run(body.trim());
    if (!result.ok) {
      if (!result.skipped) failPublish();
      return;
    }
    setBody("");
    onPublished(result.value.id);
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void submit();
    }
  }

  if (guest) {
    return (
      <div className={cn(panel, "mb-5 flex items-center gap-3 p-3 sm:p-4")}>
        <Avatar author={null} label={label} />
        <Link
          href="/login"
          className="flex min-h-11 flex-1 items-center rounded-control border border-control-border bg-background px-3 text-sm text-text-secondary transition-colors hover:border-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          {t("feed.promptGuest")}
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className={cn(panel, "mb-5 flex items-start gap-3 p-3 sm:p-4")} aria-busy={send.pending}>
      <Avatar author={author} label={label} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-end gap-2 rounded-control border border-control-border bg-background p-1.5 transition-colors focus-within:border-primary focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-focus">
          <label htmlFor={`${id}-body`} className="sr-only">
            {t("feed.quickLabel")}
          </label>
          <textarea
            id={`${id}-body`}
            rows={1}
            value={body}
            disabled={viewer.status !== "authenticated" || send.pending}
            onChange={(event) => setBody(event.target.value)}
            onKeyDown={onKeyDown}
            aria-invalid={issue ? true : undefined}
            aria-describedby={issue ? `${id}-error` : undefined}
            placeholder={t("feed.prompt")}
            className="max-h-44 min-h-11 min-w-[min(100%,14rem)] flex-1 resize-none bg-transparent px-2 py-2.5 text-sm leading-6 text-text-primary [field-sizing:content] placeholder:text-text-secondary focus-visible:outline-none disabled:opacity-60"
          />
          <button type="submit" className={cn(primaryButtonClass, "ms-auto shrink-0")} disabled={!canSubmit}>
            <CommunityIcon name="send" className="size-4" />
            {t("feed.quickSubmit")}
          </button>
        </div>
        {body.trim().length >= COUNTER_FROM ? (
          <p className={cn("mt-1.5 text-end text-xs tabular-nums text-text-secondary", issue && "font-semibold text-brand-text")}>
            {body.trim().length}/{LIMITS.postBody}
          </p>
        ) : null}
        {issue ? <FieldError id={`${id}-error`}>{t(`form.error.${issue}`, { max: LIMITS.postBody })}</FieldError> : null}
        {send.error ? (
          <div className="mt-2">
            <ActionError error={send.error} />
          </div>
        ) : null}
      </div>
    </form>
  );
}
