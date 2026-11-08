"use client";

import { useId, useState, type FormEvent, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { useAuthors, useGames } from "../hooks/use-directory";
import { useResource } from "../hooks/use-resource";
import { createPost, fetchForum, uploadPostImage } from "../services/community-api";
import { completePublish, failPublish, setPublishStage, startPublish } from "../services/publish-flow";
import { isUuid } from "../services/post-query";
import { contentTypeNeeds, LIMITS, validateText, validateTitle, type FieldError as FieldIssue } from "../schemas/community.schema";
import { gameContentTypes, postCategories, type GameContentType, type PostCategory } from "../types/community.types";
import { CommunityFrame } from "./community-frame";
import { CommunityIcon } from "./community-icons";
import {
  ActionError,
  buttonClass,
  ErrorBlock,
  FieldError,
  inputClass,
  LoadingBlock,
  panel,
  primaryButtonClass
} from "./community-ui";
import { GamePicker } from "./game-picker";
import { ImagePicker, releaseLocalImage, type LocalImage } from "./image-picker";
import { MediaCarousel, type MediaItem } from "./media-carousel";
import { PostContent } from "./post-content";

type Created = { postId: string; pending: LocalImage[]; uploadError?: unknown };
type Mode = "post" | "game";

/**
 * Gönderi oluşturma. Sıra: önce gönderi (JSON), sonra her görsel ayrı `multipart` isteğiyle, ekrandaki sırayla (sunucu
 * `sortOrder`'ı yükleme sırasından verir). Gönderi oluştuktan sonra form kilitlenir (mükerrer gönderi olmaz); görsel
 * yükleme kısmen başarısız olursa yalnız eksik görseller yeniden denenir. Forum konusu (`?forum=<id>`) forumun oyununu
 * miras alır. Başarı yalnız sunucu onayından sonra gösterilir ve gönderi akışta sunucudan okunarak görünür.
 */
export function PostFormScreen() {
  const t = useTranslations("Community");
  const session = useRequireSession();
  const params = useSearchParams();
  const forumParam = params.get("forum");
  const forumId = forumParam && isUuid(forumParam) ? forumParam : null;
  const forum = useResource(forumId ? `forum:${forumId}` : null, (signal) => fetchForum(forumId as string, signal));

  const title = forumId ? t("form.titleTopic") : t("form.titlePost");

  if (session.status !== "authenticated") {
    return (
      <CommunityFrame title={title} description={t("form.description")} hideNav>
        <LoadingBlock rows={2} />
      </CommunityFrame>
    );
  }

  return (
    <CommunityFrame title={title} description={forumId ? t("form.descriptionTopic") : t("form.description")} hideNav>
      {forumId && forum.loading ? <LoadingBlock rows={2} /> : null}
      {forumId && forum.error ? <ErrorBlock error={forum.error} retry={forum.reload} /> : null}
      {!forumId || forum.data ? (
        <PostForm accountId={session.me.accountId} forumId={forumId} forumTitle={forum.data?.title ?? null} />
      ) : null}
    </CommunityFrame>
  );
}

/** Bölüm başlığı: sol çizgi, tüm LTZ ekranlarındaki başlık işaretiyle aynıdır. */
function FormSection({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <div>
        <div className="flex items-center gap-2.5">
          <span aria-hidden="true" className="h-4 w-1 bg-primary" />
          <h2 className="font-display text-base font-semibold text-text-primary">{title}</h2>
        </div>
        {hint ? <p className="mt-1 ps-3.5 text-sm leading-5 text-text-secondary">{hint}</p> : null}
      </div>
      {children}
    </section>
  );
}

const choiceClass =
  "flex cursor-pointer items-start gap-3 rounded-control border border-control-border bg-background/60 p-3.5 transition-colors hover:border-text-secondary has-[:checked]:border-primary has-[:checked]:bg-primary/10 has-[:checked]:shadow-[inset_3px_0_0_var(--ltz-primary)] has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60";
const chipChoiceClass =
  "inline-flex min-h-10 cursor-pointer items-center rounded-control border border-control-border px-3.5 text-sm font-medium transition-colors hover:border-text-secondary has-[:checked]:border-primary has-[:checked]:bg-primary/10 has-[:checked]:text-text-primary has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60";

function PostForm({ accountId, forumId, forumTitle }: { accountId: string; forumId: string | null; forumTitle: string | null }) {
  const t = useTranslations("Community");
  const id = useId();
  const router = useRouter();

  const [mode, setMode] = useState<Mode>("post");
  const [contentType, setContentType] = useState<GameContentType>("GUIDES");
  const [heading, setHeading] = useState("");
  const [body, setBody] = useState("");
  const [category, setCategory] = useState<PostCategory>("GENERAL");
  const [gameId, setGameId] = useState("");
  const [images, setImages] = useState<LocalImage[]>([]);
  const [activeKey, setActiveKey] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);
  const [created, setCreated] = useState<Created | null>(null);
  const [now] = useState(() => new Date().toISOString());

  const author = useAuthors([accountId], true);
  const game = useGames([gameId || null]);

  const typed: GameContentType | "" = !forumId && mode === "game" ? contentType : "";
  const needs = contentTypeNeeds(typed);
  const titleIssue: FieldIssue | null = needs.title ? validateTitle(heading) : null;
  const bodyIssue = validateText(body, LIMITS.postBody);
  const gameIssue = needs.game && !gameId ? "required" : null;
  const invalid = titleIssue !== null || bodyIssue !== null || gameIssue !== null;
  const locked = created !== null;
  const bodyLength = body.trim().length;

  const publish = useAction(async () => {
    let postId = created?.postId;
    const pending = created ? created.pending : images;
    if (!postId) {
      setPublishStage({ kind: "post" });
      const post = await createPost({
        body: body.trim(),
        ...(forumId ? { forumId } : {}),
        ...(!forumId && gameId ? { gameId } : {}),
        ...(typed ? { contentType: typed, title: heading.trim() } : {}),
        ...(!forumId && !typed && category !== "GENERAL" ? { category } : {})
      });
      postId = post.id;
      setCreated({ postId, pending });
    }
    const failed: LocalImage[] = [];
    let uploadError: unknown;
    // Ekrandaki sıra = yükleme sırası = sunucudaki `sortOrder`.
    for (const [position, image] of pending.entries()) {
      setPublishStage({ kind: "image", current: position + 1, total: pending.length });
      try {
        await uploadPostImage(postId, image.file);
        releaseLocalImage(image);
      } catch (error) {
        failed.push(image);
        uploadError = error;
      }
    }
    setCreated({ postId, pending: failed, uploadError });
    setImages(failed);
    if (failed.length === 0) {
      // Başarı yalnız tüm istekler sunucu tarafından onaylandıktan sonra. Yükleme katmanı, liste yeni gönderiyi gösterecek
      // hâle gelene dek sürer (liste ekranı `usePublishHandoff` ile katmanı kaldırır).
      completePublish(postId);
      router.push(forumId ? { pathname: "/community/forums/[forumId]", params: { forumId } } : "/community");
    } else {
      // Gönderi oluştu ama bazı görseller yüklenemedi: katman kalkar, uyarı formda gösterilir; başarı bildirimi yok.
      failPublish();
    }
    return postId;
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (publish.pending) return;
    setTouched(true);
    if (invalid && !locked) return;
    // Doğrulamalar geçti: tam ekran yükleme katmanı açılır; zaten sürüyorsa ikinci paylaşım başlamaz.
    if (!startPublish()) return;
    const result = await publish.run();
    if (!result.ok && !result.skipped) failPublish();
  }

  const showError = (issue: FieldIssue | null) => touched && issue !== null;
  const activeIndex = Math.max(0, images.findIndex((image) => image.key === activeKey));
  const mediaItems: MediaItem[] = images.map((image, index) => ({
    key: image.key,
    src: image.url,
    alt: t("images.previewAlt", { number: index + 1, name: image.file.name }),
    width: image.width,
    height: image.height
  }));
  const gameTitle = gameId ? (game(gameId)?.title ?? null) : null;
  const percent = Math.min(100, Math.round((bodyLength / LIMITS.postBody) * 100));
  const nearLimit = percent >= 90;
  const busy = publish.pending;
  const done = locked && created?.pending.length === 0;
  const submitLabel = busy ? t("form.publishing") : locked ? t("form.retryImages") : publish.error ? t("form.retry") : t("form.submit");

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,25rem)]">
      <form
        onSubmit={submit}
        noValidate
        aria-busy={busy}
        aria-describedby={locked ? `${id}-created` : undefined}
        className="relative overflow-hidden rounded-panel border border-border bg-surface"
      >
        <div aria-hidden="true" className="h-1 bg-gradient-to-r from-primary via-secondary to-transparent" />
        <fieldset disabled={busy} className="m-0 min-w-0 space-y-8 border-0 p-4 sm:p-6">
          <legend className="sr-only">{forumId ? t("form.titleTopic") : t("form.titlePost")}</legend>

          {forumTitle ? (
            <p className="flex flex-wrap items-center gap-2 text-sm text-text-secondary">
              {t("form.forumContext")}
              <span className="rounded-control border border-border bg-surface-elevated px-2 py-0.5 text-xs font-medium text-text-primary">{forumTitle}</span>
            </p>
          ) : null}

          {!forumId ? (
            <FormSection title={t("form.sectionKind")} hint={t("form.sectionKindHint")}>
              <fieldset disabled={locked} className="m-0 min-w-0 border-0 p-0">
                <legend className="sr-only">{t("form.type")}</legend>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className={choiceClass}>
                    <input type="radio" name={`${id}-mode`} className="sr-only" checked={mode === "post"} onChange={() => setMode("post")} />
                    <Icon name="messages" className="mt-0.5 size-5 shrink-0 text-brand-text" />
                    <span>
                      <span className="block font-display text-sm font-semibold text-text-primary">{t("form.modePost")}</span>
                      <span className="mt-0.5 block text-sm leading-5 text-text-secondary">{t("form.modePostHelp")}</span>
                    </span>
                  </label>
                  <label className={choiceClass}>
                    <input type="radio" name={`${id}-mode`} className="sr-only" checked={mode === "game"} onChange={() => setMode("game")} />
                    <Icon name="gamepad" className="mt-0.5 size-5 shrink-0 text-brand-text" />
                    <span>
                      <span className="block font-display text-sm font-semibold text-text-primary">{t("form.modeGame")}</span>
                      <span className="mt-0.5 block text-sm leading-5 text-text-secondary">{t("form.modeGameHelp")}</span>
                    </span>
                  </label>
                </div>

                {mode === "game" ? (
                  <div className="mt-4">
                    <p className="mb-2 text-sm font-medium text-text-primary">{t("form.contentTypeLabel")}</p>
                    <div role="radiogroup" aria-label={t("form.contentTypeLabel")} className="flex flex-wrap gap-2">
                      {gameContentTypes.map((value) => (
                        <label key={value} className={chipChoiceClass}>
                          <input type="radio" name={`${id}-ctype`} className="sr-only" checked={contentType === value} onChange={() => setContentType(value)} />
                          {t(`post.contentType.${value}`)}
                        </label>
                      ))}
                    </div>
                    <p className="mt-2 text-sm text-text-secondary">{t(`form.contentTypeHelp.${contentType}`)}</p>
                  </div>
                ) : (
                  <div className="mt-4">
                    <p className="mb-2 text-sm font-medium text-text-primary">{t("form.category")}</p>
                    <div role="radiogroup" aria-label={t("form.category")} className="flex flex-wrap gap-2">
                      {postCategories.map((value) => (
                        <label key={value} className={chipChoiceClass}>
                          <input type="radio" name={`${id}-category`} className="sr-only" checked={category === value} onChange={() => setCategory(value)} />
                          {t(`category.${value}`)}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </fieldset>
            </FormSection>
          ) : null}

          <FormSection title={t("form.sectionContent")}>
            {needs.title ? (
              <div>
                <label htmlFor={`${id}-title`} className="mb-1.5 block text-sm font-medium text-text-primary">
                  {t("form.titleLabel")} <span aria-hidden="true" className="text-brand-text">*</span>
                </label>
                <input
                  id={`${id}-title`}
                  value={heading}
                  disabled={locked}
                  maxLength={LIMITS.title + 40}
                  onChange={(event) => setHeading(event.target.value)}
                  aria-required="true"
                  aria-invalid={showError(titleIssue) ? true : undefined}
                  aria-describedby={showError(titleIssue) ? `${id}-title-error` : undefined}
                  placeholder={t("form.titlePlaceholder")}
                  className={cn(inputClass, "font-display text-base font-semibold")}
                />
                {showError(titleIssue) ? <FieldError id={`${id}-title-error`}>{t(`form.error.${titleIssue}`, { max: LIMITS.title })}</FieldError> : null}
              </div>
            ) : null}
            <div>
              <label htmlFor={`${id}-body`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("form.bodyLabel")} <span aria-hidden="true" className="text-brand-text">*</span>
              </label>
              <textarea
                id={`${id}-body`}
                rows={8}
                value={body}
                disabled={locked}
                onChange={(event) => setBody(event.target.value)}
                aria-required="true"
                aria-invalid={showError(bodyIssue) ? true : undefined}
                aria-describedby={`${id}-body-count${showError(bodyIssue) ? ` ${id}-body-error` : ""}`}
                placeholder={t("form.bodyPlaceholder")}
                className={cn(inputClass, "min-h-44 resize-y text-[0.9375rem] leading-7")}
              />
              <div id={`${id}-body-count`} className="mt-2 flex items-center gap-3 text-xs text-text-secondary">
                <span className="min-w-0 flex-1">{t("form.plainText")}</span>
                <span aria-hidden="true" className="h-1 w-20 shrink-0 overflow-hidden bg-border">
                  <span className={cn("block h-full", nearLimit ? "bg-warning" : "bg-primary")} style={{ width: `${percent}%` }} />
                </span>
                <span className={cn("tabular-nums", nearLimit && "font-semibold text-text-primary")}>
                  {bodyLength}/{LIMITS.postBody}
                </span>
              </div>
              {showError(bodyIssue) ? <FieldError id={`${id}-body-error`}>{t(`form.error.${bodyIssue}`, { max: LIMITS.postBody })}</FieldError> : null}
            </div>
          </FormSection>

          {!forumId ? (
            <FormSection title={t("form.sectionGame")} hint={mode === "game" ? t("form.sectionGameRequiredHint") : t("form.sectionGameHint")}>
              <GamePicker
                id={`${id}-game`}
                label={needs.game ? t("form.gameRequired") : t("form.gameOptional")}
                value={gameId}
                onChange={setGameId}
                disabled={locked}
                describedBy={showError(gameIssue) ? `${id}-game-error` : undefined}
              />
              {showError(gameIssue) ? <FieldError id={`${id}-game-error`}>{t("form.error.gameRequired")}</FieldError> : null}
            </FormSection>
          ) : null}

          <FormSection title={t("form.sectionMedia")} hint={t("form.sectionMediaHint")}>
            <ImagePicker
              items={images}
              setItems={setImages}
              addDisabled={locked}
              activeKey={activeKey}
              onActivate={setActiveKey}
            />
          </FormSection>
        </fieldset>

        <div className="space-y-4 border-t border-border bg-surface-elevated/40 p-4 sm:px-6 max-lg:sticky max-lg:bottom-0 max-lg:z-10 max-lg:bg-surface">
          {locked && created && !done && !busy ? (
            <div id={`${id}-created`} role="alert" className="space-y-3 rounded-control border border-warning/60 bg-warning/10 p-4 text-sm text-text-primary">
              <p>{t("form.partial", { count: created.pending.length })}</p>
              {created.uploadError ? <ActionError error={created.uploadError} /> : null}
              <Link
                href={{ pathname: "/community/posts/[postId]", params: { postId: created.postId } }}
                className="inline-flex min-h-11 items-center font-semibold text-brand-text underline-offset-2 hover:underline"
              >
                {t("form.openPost")}
              </Link>
            </div>
          ) : (
            <ActionError error={busy ? undefined : publish.error} />
          )}
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
            <div className="flex flex-wrap items-center justify-end gap-3">
              <Link
                href={forumId ? { pathname: "/community/forums/[forumId]", params: { forumId } } : "/community"}
                aria-disabled={busy}
                tabIndex={busy ? -1 : undefined}
                className={cn(buttonClass, busy && "pointer-events-none opacity-50")}
              >
                {t("form.cancel")}
              </Link>
              <button type="submit" className={cn(primaryButtonClass, "min-h-12 px-7 text-base")} disabled={busy || done}>
                <CommunityIcon name="send" className="size-5" />
                {submitLabel}
              </button>
            </div>
          </div>
        </div>
      </form>

      <aside aria-label={t("form.previewLabel")} className="lg:sticky lg:top-24 lg:self-start">
        <div className="mb-3 flex items-center gap-2.5">
          <span aria-hidden="true" className="h-4 w-1 bg-primary" />
          <h2 className="font-display text-base font-semibold text-text-primary">{t("form.preview")}</h2>
        </div>
        <p className="mb-3 text-sm leading-5 text-text-secondary">{t("form.previewHelp")}</p>
        <article aria-label={t("form.previewLabel")} className={cn(panel, "p-4 sm:p-5")}>
          <PostContent
            headingId={`${id}-preview-heading`}
            lookups={{ author, game: (gid) => (gid ? (game(gid) ?? null) : null) }}
            authorId={accountId}
            createdAt={now}
            gameId={gameId || null}
            contentType={typed || null}
            category={!forumId && !typed ? category : "GENERAL"}
            title={needs.title && heading.trim() ? heading.trim() : null}
            body={body.trim() || null}
            media={
              mediaItems.length > 0 ? (
                <div className="mt-3">
                  <MediaCarousel
                    items={mediaItems}
                    variant="preview"
                    label={t("images.galleryLabel", { count: mediaItems.length })}
                    index={activeIndex}
                    onIndexChange={(index) => setActiveKey(mediaItems[index]?.key ?? null)}
                  />
                </div>
              ) : null
            }
          />
          {!body.trim() ? <p className="mt-3 text-sm text-text-secondary">{t("form.previewEmpty")}</p> : null}
          {gameId && !gameTitle ? <p className="sr-only">{t("game.selected")}</p> : null}
        </article>
      </aside>
    </div>
  );
}
