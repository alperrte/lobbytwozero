"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { useAction } from "../hooks/use-action";
import {
  deletePostImage,
  fetchPost,
  linkPostGame,
  unlinkPostGame,
  updatePost,
  uploadPostImage
} from "../services/community-api";
import { LIMITS, validateText, validateTitle } from "../schemas/community.schema";
import { postCategories, type PostCategory, type PostImage, type PostView } from "../types/community.types";
import { actionClass, ActionError, buttonClass, FieldError, inputClass, panel, primaryButtonClass } from "./community-ui";
import { GamePicker } from "./game-picker";
import { ImagePicker, releaseLocalImage, type LocalImage } from "./image-picker";
import { PostImageView } from "./post-parts";

/**
 * Gönderi düzenleme (yalnız sahip; yetkiyi sunucu denetler). Metin/kategori/başlık `PATCH`, oyun bağlantısı `PUT/DELETE
 * /game`, görseller ayrı uçlardır. Başlık yalnız türlü içerikte, oyun bağlantısı forum konusunda düzenlenemez.
 * Adımlar sırayla çalışır ve idempotenttir: kısmen başarısız kayıtta yeniden denemek güvenlidir.
 */
export function PostEditor({ post, onDone, onCancel }: { post: PostView; onDone: (post: PostView) => void; onCancel: () => void }) {
  const t = useTranslations("Community");
  const id = useId();
  const [heading, setHeading] = useState(post.title ?? "");
  const [body, setBody] = useState(post.body ?? "");
  const [category, setCategory] = useState<PostCategory>(post.category);
  const [gameId, setGameId] = useState(post.gameId ?? "");
  const [existing, setExisting] = useState<PostImage[]>(post.images);
  const [added, setAdded] = useState<LocalImage[]>([]);
  const [touched, setTouched] = useState(false);

  const typed = post.contentType !== null;
  const inForum = post.forumId !== null;
  const titleIssue = typed ? validateTitle(heading) : null;
  const bodyIssue = validateText(body, LIMITS.postBody);
  const gameIssue = typed && !gameId ? "required" : null;
  const invalid = titleIssue !== null || bodyIssue !== null || gameIssue !== null;

  const removeImage = useAction(async (image: PostImage) => {
    await deletePostImage(post.id, image.id);
    setExisting((current) => current.filter((item) => item.id !== image.id));
  });

  const save = useAction(async () => {
    await updatePost(post.id, {
      body: body.trim(),
      ...(typed ? { title: heading.trim() } : {}),
      ...(!typed && !inForum && category !== post.category ? { category } : {})
    });
    if (!inForum && gameId !== (post.gameId ?? "")) {
      if (gameId) await linkPostGame(post.id, gameId);
      else await unlinkPostGame(post.id);
    }
    const failed: LocalImage[] = [];
    let uploadError: unknown;
    for (const image of added) {
      try {
        await uploadPostImage(post.id, image.file);
        releaseLocalImage(image);
      } catch (error) {
        failed.push(image);
        uploadError = error;
      }
    }
    setAdded(failed);
    // Güncel durum sunucudan okunur (sayaçlar ve görsel listesi dahil); yerel tahmin yazılmaz.
    const fresh = await fetchPost(post.id);
    return { fresh, failed: failed.length, uploadError };
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (invalid) return;
    const result = await save.run();
    if (result.ok && result.value.failed === 0) onDone(result.value.fresh);
    else if (result.ok) setExisting(result.value.fresh.images);
  }

  const busy = save.pending || removeImage.pending;

  return (
    <form onSubmit={submit} noValidate className={`${panel} space-y-5 p-4 sm:p-6`}>
      <h2 className="font-display text-xl font-semibold text-text-primary">{t("edit.title")}</h2>

      {typed ? (
        <div>
          <label htmlFor={`${id}-title`} className="mb-1.5 block text-sm font-medium text-text-primary">
            {t("form.titleLabel")}
          </label>
          <input id={`${id}-title`} value={heading} onChange={(event) => setHeading(event.target.value)} aria-invalid={touched && titleIssue ? true : undefined} aria-describedby={touched && titleIssue ? `${id}-title-error` : undefined} className={inputClass} />
          {touched && titleIssue ? <FieldError id={`${id}-title-error`}>{t(`form.error.${titleIssue}`, { max: LIMITS.title })}</FieldError> : null}
        </div>
      ) : null}

      <div>
        <label htmlFor={`${id}-body`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("form.bodyLabel")}
        </label>
        <textarea id={`${id}-body`} rows={7} value={body} onChange={(event) => setBody(event.target.value)} aria-invalid={touched && bodyIssue ? true : undefined} aria-describedby={touched && bodyIssue ? `${id}-body-error` : undefined} className={`${inputClass} resize-y leading-6`} />
        <p className="mt-1 text-xs text-text-secondary">{body.trim().length}/{LIMITS.postBody}</p>
        {touched && bodyIssue ? <FieldError id={`${id}-body-error`}>{t(`form.error.${bodyIssue}`, { max: LIMITS.postBody })}</FieldError> : null}
      </div>

      {!inForum || !typed ? (
        <div className="grid gap-4 sm:grid-cols-2">
          {!inForum ? (
            <div>
              <GamePicker id={`${id}-game`} label={typed ? t("form.gameRequired") : t("form.gameOptional")} value={gameId} onChange={setGameId} />
              {touched && gameIssue ? <FieldError id={`${id}-game-error`}>{t("form.error.gameRequired")}</FieldError> : null}
            </div>
          ) : null}
          {!typed && !inForum ? (
            <div>
              <label htmlFor={`${id}-category`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("form.category")}
              </label>
              <select id={`${id}-category`} value={category} onChange={(event) => setCategory(event.target.value as PostCategory)} className={inputClass}>
                {postCategories.map((value) => (
                  <option key={value} value={value}>
                    {t(`category.${value}`)}
                  </option>
                ))}
              </select>
            </div>
          ) : null}
        </div>
      ) : null}

      <div>
        <p className="mb-2 text-sm font-medium text-text-primary">{t("form.images")}</p>
        {existing.length > 0 ? (
          <ul className="mb-3 grid grid-cols-[repeat(auto-fill,minmax(5.75rem,1fr))] gap-3">
            {existing.map((image, index) => (
              <li key={image.id} className="overflow-hidden rounded-control border border-border bg-surface-elevated">
                <PostImageView image={image} index={index} className="aspect-square max-h-none rounded-none border-0 object-cover" />
                <button type="button" className={`${actionClass} w-full justify-center border-t border-border text-xs`} disabled={busy} aria-label={t("edit.removeImage", { number: index + 1 })} onClick={() => void removeImage.run(image)}>
                  <Icon name="close" className="size-4" />
                  {index + 1}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <ActionError error={removeImage.error} />
        <ImagePicker items={added} setItems={setAdded} remoteCount={existing.length} disabled={busy} activeKey={null} />
        {existing.length > 0 ? <p className="mt-2 text-xs leading-5 text-text-secondary">{t("edit.orderLocked")}</p> : null}
      </div>

      {save.error === undefined && added.length > 0 && !save.pending && touched ? (
        <p role="alert" className="rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-sm text-text-primary">
          {t("edit.partial", { count: added.length })}
        </p>
      ) : null}
      <ActionError error={save.error} />

      <div className="flex flex-wrap justify-end gap-3 border-t border-border pt-4">
        <button type="button" className={buttonClass} onClick={onCancel} disabled={busy}>
          {t("form.cancel")}
        </button>
        <button type="submit" className={primaryButtonClass} disabled={busy}>
          {save.pending ? t("state.working") : t("edit.save")}
        </button>
      </div>
    </form>
  );
}
