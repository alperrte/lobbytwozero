"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useAction } from "../hooks/use-action";
import { useGames } from "../hooks/use-directory";
import { usePagedList } from "../hooks/use-paged-list";
import { useViewer } from "../hooks/use-viewer";
import { createForum, fetchForums } from "../services/community-api";
import { statusOf } from "../services/community-errors";
import { LIMITS, validateSlug, validateText, validateTitle } from "../schemas/community.schema";
import type { Forum } from "../types/community.types";
import { CommunityFrame } from "./community-frame";
import { CommunityIcon } from "./community-icons";
import {
  ActionError,
  buttonClass,
  EmptyBlock,
  ErrorBlock,
  FieldError,
  inputClass,
  LoadMore,
  LoadingBlock,
  ModalDialog,
  panel,
  primaryButtonClass
} from "./community-ui";
import { GamePicker } from "./game-picker";

/** Türkçe karakterleri sadeleştirip `[a-z0-9]` + tekil tire biçimine çevirir (forum slug önerisi). */
export function slugify(value: string): string {
  return value
    .toLocaleLowerCase("tr")
    .replace(/ı/g, "i")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, LIMITS.slugMax);
}

/**
 * Oyun forumları: Community'nin kendi forum kayıtları (herkese açık okuma). Forum oluşturma yalnız moderatör/yönetici
 * içindir (arayüz ipucu; yetkiyi sunucu `403` ile uygular). Oyun görseli/adı Game Service toplu kartından gelir.
 */
export function ForumsScreen() {
  const t = useTranslations("Community");
  const viewer = useViewer();
  const list = usePagedList("forums", (cursor, signal) => fetchForums(cursor, signal));
  const game = useGames(list.items.map((forum) => forum.gameId));
  const [creating, setCreating] = useState(false);

  return (
    <CommunityFrame
      title={t("forums.title")}
      description={t("forums.description")}
      action={
        viewer.canModerate ? (
          <button type="button" className={primaryButtonClass} onClick={() => setCreating(true)}>
            <CommunityIcon name="plus" className="size-4" />
            {t("forums.create")}
          </button>
        ) : undefined
      }
    >
      {list.loading ? <LoadingBlock rows={3} /> : null}
      {list.error ? <ErrorBlock error={list.error} retry={list.reload} /> : null}
      {!list.loading && !list.error ? (
        list.items.length === 0 ? (
          <EmptyBlock title={t("forums.emptyTitle")} description={t("forums.emptyBody")} />
        ) : (
          <>
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {list.items.map((forum) => (
                <li key={forum.id}>
                  <ForumCard forum={forum} gameTitle={forum.gameId ? (game(forum.gameId)?.title ?? null) : null} coverUrl={forum.gameId ? (game(forum.gameId)?.coverUrl ?? null) : null} />
                </li>
              ))}
            </ul>
            <LoadMore hasMore={list.hasMore} loading={list.loadingMore} error={list.moreError} onMore={list.loadMore} />
          </>
        )
      ) : null}

      <ModalDialog open={creating} onClose={() => setCreating(false)} title={t("forums.createTitle")}>
        {creating ? (
          <ForumForm
            onCancel={() => setCreating(false)}
            onCreated={() => {
              setCreating(false);
              list.reload();
            }}
          />
        ) : null}
      </ModalDialog>
    </CommunityFrame>
  );
}

function ForumCard({ forum, gameTitle, coverUrl }: { forum: Forum; gameTitle: string | null; coverUrl: string | null }) {
  const t = useTranslations("Community.forums");
  return (
    <Link
      href={{ pathname: "/community/forums/[forumId]", params: { forumId: forum.id } }}
      className={cn(panel, "group flex h-full flex-col overflow-hidden transition-colors hover:border-text-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus")}
    >
      <div className="relative flex h-24 items-center justify-center overflow-hidden border-b border-border bg-surface-elevated">
        {coverUrl ? (
          // Game Service'in https kapak adresi; boyut CSS ile sabit, optimizasyon vekili uygulanamaz.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={coverUrl} alt="" loading="lazy" referrerPolicy="no-referrer" className="size-full object-cover" />
        ) : (
          <Icon name="messages" className="size-9 text-text-secondary" />
        )}
      </div>
      <div className="flex flex-1 flex-col p-4">
        <h2 className="font-display text-lg font-semibold leading-snug text-text-primary group-hover:underline">{forum.title}</h2>
        {gameTitle ? <p className="mt-1 text-xs font-medium text-text-secondary">{gameTitle}</p> : null}
        <p className="mt-2 line-clamp-3 text-sm leading-6 text-text-secondary">{forum.description ?? t("noDescription")}</p>
        <span className="mt-auto pt-3 text-sm font-semibold text-brand-text">{t("open")}</span>
      </div>
    </Link>
  );
}

function ForumForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: () => void }) {
  const t = useTranslations("Community");
  const id = useId();
  const [title, setTitle] = useState("");
  const [slugInput, setSlugInput] = useState<string | null>(null);
  const [description, setDescription] = useState("");
  const [gameId, setGameId] = useState("");
  const [touched, setTouched] = useState(false);
  const slug = slugInput ?? slugify(title);
  const create = useAction(() => createForum({ slug, title: title.trim(), ...(description.trim() ? { description: description.trim() } : {}), ...(gameId ? { gameId } : {}) }));

  const titleIssue = validateTitle(title);
  const slugIssue = validateSlug(slug);
  const descriptionIssue = validateText(description, LIMITS.forumDescription, { required: false });

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (titleIssue || slugIssue || descriptionIssue) return;
    const result = await create.run();
    if (result.ok) onCreated();
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <div>
        <label htmlFor={`${id}-title`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("forums.fieldTitle")}
        </label>
        <input id={`${id}-title`} value={title} onChange={(event) => setTitle(event.target.value)} aria-invalid={touched && titleIssue ? true : undefined} aria-describedby={touched && titleIssue ? `${id}-title-error` : undefined} className={inputClass} />
        {touched && titleIssue ? <FieldError id={`${id}-title-error`}>{t(`form.error.${titleIssue}`, { max: LIMITS.forumTitle })}</FieldError> : null}
      </div>
      <div>
        <label htmlFor={`${id}-slug`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("forums.fieldSlug")}
        </label>
        <input id={`${id}-slug`} value={slug} onChange={(event) => setSlugInput(event.target.value)} aria-invalid={touched && slugIssue ? true : undefined} aria-describedby={`${id}-slug-help${touched && slugIssue ? ` ${id}-slug-error` : ""}`} className={inputClass} />
        <p id={`${id}-slug-help`} className="mt-1 text-xs text-text-secondary">{t("forums.slugHelp")}</p>
        {touched && slugIssue ? <FieldError id={`${id}-slug-error`}>{t(`form.error.${slugIssue}`, { max: LIMITS.slugMax, min: LIMITS.slugMin })}</FieldError> : null}
      </div>
      <div>
        <label htmlFor={`${id}-description`} className="mb-1.5 block text-sm font-medium text-text-primary">
          {t("forums.fieldDescription")} <span className="font-normal text-text-secondary">({t("form.optional")})</span>
        </label>
        <textarea id={`${id}-description`} rows={3} value={description} onChange={(event) => setDescription(event.target.value)} aria-invalid={descriptionIssue ? true : undefined} className={`${inputClass} resize-y`} />
        {descriptionIssue ? <FieldError id={`${id}-description-error`}>{t(`form.error.${descriptionIssue}`, { max: LIMITS.forumDescription })}</FieldError> : null}
      </div>
      <GamePicker id={`${id}-game`} label={t("forums.fieldGame")} value={gameId} onChange={setGameId} />
      {statusOf(create.error) === 409 ? (
        <p role="alert" className="rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-sm text-text-primary">
          {t("forums.slugTaken")}
        </p>
      ) : (
        <ActionError error={create.error} />
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <button type="button" className={buttonClass} onClick={onCancel} disabled={create.pending}>
          {t("state.cancel")}
        </button>
        <button type="submit" className={primaryButtonClass} disabled={create.pending}>
          {create.pending ? t("state.working") : t("forums.createSubmit")}
        </button>
      </div>
    </form>
  );
}
