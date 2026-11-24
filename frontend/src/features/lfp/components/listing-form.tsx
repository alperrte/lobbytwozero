"use client";

import { useEffect, useId, useMemo, useState, type KeyboardEvent, type ReactNode } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { GameCover } from "@/features/game/components/game-cover";
import { GamePicker } from "@/features/game/components/game-picker";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { Select } from "@/shared/components/ui/select";
import { ovalControlClass, ovalControlSize } from "@/shared/components/ui/styles";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useListingVisuals } from "../hooks/use-lfp-data";
import {
  DURATION_OPTIONS,
  LIMITS,
  addRole,
  emptyForm,
  formFromListing,
  length,
  localToIso,
  toCreateInput,
  toUpdateInput,
  validateForm,
  type FormField,
  type FormIssues,
  type ListingFormState
} from "../schemas/listing-form";
import { lfpApi } from "../services/lfp-api";
import { fieldErrorsOf, lfpErrorKey } from "../services/lfp-errors";
import { PLATFORMS, type Listing, type Platform } from "../types/lfp.types";
import { ListingCard } from "./listing-card";
import { LfpSessionGate, usePlatformLabel } from "./listing-parts";

export function CreateListingView() {
  return <LfpSessionGate>{(meId) => <ListingFormScreen meId={meId} />}</LfpSessionGate>;
}

export function EditListingView({ listingId }: Readonly<{ listingId: string }>) {
  return <LfpSessionGate>{(meId) => <EditLoader meId={meId} listingId={listingId} />}</LfpSessionGate>;
}

/** Düzenlenecek ilanı okur. Yalnız sahibi ve yalnız açık ilan düzenlenebilir (LFP-12, LFP-14). */
function EditLoader({ meId, listingId }: Readonly<{ meId: string; listingId: string }>) {
  const t = useTranslations("Lfp");
  const [state, setState] = useState<{ listing?: Listing; error?: unknown; retry: number }>({ retry: 0 });
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    lfpApi.get(listingId, controller.signal).then(
      (listing) => setState({ listing, retry }),
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ error, retry });
      }
    );
    return () => controller.abort();
  }, [listingId, retry]);

  if (state.retry !== retry || (!state.listing && !state.error)) {
    return <p role="status" className="py-16 text-center text-sm text-text-secondary">{t("states.loading")}</p>;
  }
  let blocked: string | null = null;
  if (state.error) blocked = t(`errors.${lfpErrorKey(state.error)}`);
  else if (state.listing && state.listing.ownerId !== meId) blocked = t("errors.notOwner");
  else if (state.listing && state.listing.status !== "OPEN") blocked = t("form.notEditable");
  if (blocked || !state.listing) {
    return (
      <div role="alert" className="mx-auto max-w-lg rounded-panel border border-primary/50 bg-surface px-6 py-10 text-center">
        <Icon name="info" className="mx-auto size-8 text-brand-text" />
        <p className="mt-3 text-sm text-text-secondary">{blocked}</p>
        <div className="mt-5 flex justify-center gap-2">
          {state.error ? (
            <button type="button" onClick={() => setRetry((value) => value + 1)} className={buttonClass("secondary")}>
              {t("states.retry")}
            </button>
          ) : null}
          <Link href="/find-team/mine" className={buttonClass("primary")}>{t("tabs.mine")}</Link>
        </div>
      </div>
    );
  }
  return <ListingFormScreen meId={meId} listing={state.listing} />;
}

/**
 * İlan oluşturma ve düzenleme (11.02; LFP-01..09, LFP-12). Sağda canlı önizleme vardır (`lg` altında formun altına iner).
 * Düzenlemede oyun ve ilan süresi değişmez (sunucu kuralı); sürüm (`version`) gönderilir, eşzamanlı değişiklik `409` olur.
 */
function ListingFormScreen({ meId, listing }: Readonly<{ meId: string; listing?: Listing }>) {
  const t = useTranslations("Lfp");
  const format = useFormatter();
  const router = useRouter();
  const platformLabel = usePlatformLabel();
  const mode = listing ? "edit" : "create";
  const visuals = useListingVisuals(useMemo(() => (listing ? [listing] : []), [listing]));
  const [form, setForm] = useState<ListingFormState>(() => (listing ? formFromListing(listing, null) : emptyForm()));
  const [issues, setIssues] = useState<FormIssues>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);
  const [roleDraft, setRoleDraft] = useState("");
  const [roleIssue, setRoleIssue] = useState<string | null>(null);
  const id = useId();
  const field = (name: FormField) => `${id}-${name}`;
  const coverUrl = form.gameCoverUrl ?? (listing ? visuals.game(listing).coverUrl : null);
  // Önizlemedeki ilan bitişi "şimdi + süre"dir; saat dakikada bir ilerletilir (çizim sırasında saat okunmaz).
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const update = (patch: Partial<ListingFormState>) => {
    setForm((current) => ({ ...current, ...patch }));
    const keys = Object.keys(patch) as FormField[];
    setIssues((current) => {
      const next = { ...current };
      for (const key of keys) delete next[key];
      return next;
    });
  };

  function commitRole() {
    if (!roleDraft.trim()) return;
    const result = addRole(form.roles, roleDraft);
    if (result.issue) {
      setRoleIssue(t(`form.roleIssue.${result.issue}`, { max: LIMITS.role, count: LIMITS.roles }));
      return;
    }
    update({ roles: result.roles });
    setRoleDraft("");
    setRoleIssue(null);
  }

  function onRoleKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      commitRole();
    } else if (event.key === "Backspace" && !roleDraft && form.roles.length > 0) {
      update({ roles: form.roles.slice(0, -1) });
    }
  }

  async function submit() {
    const draftRole = roleDraft.trim() ? addRole(form.roles, roleDraft) : null;
    const candidate = draftRole && !draftRole.issue ? { ...form, roles: draftRole.roles } : form;
    if (draftRole && !draftRole.issue) {
      setForm(candidate);
      setRoleDraft("");
    }
    const found = validateForm(candidate, { mode, expiresAt: listing?.expiresAt, originalStart: listing?.playTimeStart });
    setIssues(found);
    setSubmitError(null);
    const first = Object.keys(found)[0] as FormField | undefined;
    if (first) {
      document.getElementById(field(first))?.focus();
      return;
    }
    setBusy(true);
    try {
      if (listing) await lfpApi.update(listing.id, toUpdateInput(candidate, listing.version));
      else await lfpApi.create(toCreateInput(candidate));
      router.push("/find-team/mine");
    } catch (error) {
      const fields = fieldErrorsOf(error) as FormField[];
      setIssues(Object.fromEntries(fields.map((name) => [name, "server"])) as FormIssues);
      setSubmitError(t(`errors.${lfpErrorKey(error)}`));
      setBusy(false);
    }
  }

  const issueText = (name: FormField) => (issues[name] ? t(`form.issue.${issues[name]}`, { max: limitFor(name) }) : null);
  const describe = (name: FormField, hint?: string) => cn(issues[name] && `${field(name)}-issue`, hint) || undefined;

  const expiresAt = listing?.expiresAt ?? new Date(now + form.durationHours * 3_600_000).toISOString();
  const preview: Listing = {
    id: listing?.id ?? "preview",
    ownerId: meId,
    gameId: form.gameId || "preview",
    gameTitle: form.gameTitle || t("form.previewGame"),
    platform: (form.platform || "PC") as Platform,
    skillLevel: form.skillLevel.trim() || null,
    roles: form.roles.length ? form.roles : [t("form.previewRole")],
    playersNeeded: form.playersNeeded,
    playTimeStart: localToIso(form.playTimeStart),
    playTimeEnd: localToIso(form.playTimeStart) ? localToIso(form.playTimeEnd) : null,
    micRequired: form.micRequired,
    description: form.description.trim() || t("form.previewDescription"),
    status: "OPEN",
    expiresAt,
    createdAt: listing?.createdAt ?? new Date(now).toISOString(),
    updatedAt: new Date(now).toISOString(),
    closedAt: null,
    version: listing?.version ?? 0
  };

  return (
    <div className="space-y-5">
      <nav aria-label={t("form.breadcrumb")} className="text-sm text-text-secondary">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li><Link href="/find-team" className="hover:text-text-primary hover:underline">{t("active.title")}</Link></li>
          <li aria-hidden="true">/</li>
          <li aria-current="page" className="text-text-primary">{mode === "edit" ? t("form.editTitle") : t("form.createTitle")}</li>
        </ol>
      </nav>
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight sm:text-4xl">{mode === "edit" ? t("form.editTitle") : t("form.createTitle")}</h1>
        <p className="mt-1.5 text-text-secondary">{mode === "edit" ? t("form.editSubtitle") : t("form.createSubtitle")}</p>
      </header>

      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_22rem] xl:grid-cols-[minmax(0,1fr)_25rem]">
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
          className="min-w-0 space-y-4"
        >
          <FormSection icon="gamepad" title={t("form.sections.game")}>
            <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
              <div className="flex flex-col gap-1.5">
                <span id={`${field("gameId")}-label`} className="text-sm font-medium">{t("form.game")} <Required /></span>
                {form.gameId ? (
                  <div className="flex items-center gap-3 rounded-xl bg-surface-elevated p-2">
                    <GameCover url={coverUrl} title={form.gameTitle} className="w-16 shrink-0 rounded-control" />
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold">{form.gameTitle}</span>
                    {mode === "create" ? (
                      <button type="button" onClick={() => setPicking(true)} className={buttonClass("ghost", "sm")}>{t("form.changeGame")}</button>
                    ) : null}
                  </div>
                ) : (
                  <button
                    id={field("gameId")}
                    type="button"
                    onClick={() => setPicking(true)}
                    aria-describedby={describe("gameId")}
                    className={cn(ovalControlClass, ovalControlSize.md, "flex items-center gap-2 text-start text-text-secondary", issues.gameId && "border-primary")}
                  >
                    <Icon name="search" className="size-4" />
                    {t("form.pickGame")}
                  </button>
                )}
                {mode === "edit" ? <p className="text-xs text-text-secondary">{t("form.gameLocked")}</p> : null}
                <Issue id={`${field("gameId")}-issue`} text={issueText("gameId")} />
              </div>
              <fieldset className="flex min-w-0 flex-col gap-1.5">
                <legend className="mb-1.5 text-sm font-medium">{t("form.platform")} <Required /></legend>
                <div id={field("platform")} tabIndex={-1} role="radiogroup" aria-invalid={issues.platform ? true : undefined} aria-describedby={describe("platform")} className="grid grid-cols-2 gap-2 2xl:grid-cols-3">
                  {PLATFORMS.map((value) => (
                    <ChoiceChip key={value} name={`${id}-platform`} checked={form.platform === value} onChange={() => update({ platform: value })} label={platformLabel(value)} />
                  ))}
                </div>
                <Issue id={`${field("platform")}-issue`} text={issueText("platform")} />
              </fieldset>
            </div>
          </FormSection>

          <FormSection icon="users" title={t("form.sections.players")}>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="flex flex-col gap-1.5 md:col-span-2">
                <label htmlFor={field("roles")} className="text-sm font-medium">{t("form.roles")} <Required /></label>
                <div className={cn("flex min-h-10 flex-wrap items-center gap-1.5 rounded-xl border border-transparent bg-surface-elevated px-2 py-1.5 focus-within:border-primary", issues.roles && "border-primary")}>
                  {form.roles.map((role) => (
                    <span key={role} className="inline-flex items-center gap-1 rounded-control bg-surface px-2 py-1 text-sm">
                      {role}
                      <button type="button" onClick={() => update({ roles: form.roles.filter((item) => item !== role) })} aria-label={t("form.removeRole", { role })} className="inline-flex size-5 items-center justify-center rounded-full text-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-focus">
                        <Icon name="close" className="size-3.5" />
                      </button>
                    </span>
                  ))}
                  {form.roles.length < LIMITS.roles ? (
                    <input
                      id={field("roles")}
                      value={roleDraft}
                      maxLength={LIMITS.role}
                      placeholder={form.roles.length ? t("form.rolesMore") : t("form.rolesPlaceholder")}
                      aria-invalid={issues.roles || roleIssue ? true : undefined}
                      aria-describedby={cn(describe("roles", `${field("roles")}-hint`), roleIssue && `${field("roles")}-roleissue`)}
                      onChange={(event) => {
                        setRoleDraft(event.target.value);
                        setRoleIssue(null);
                      }}
                      onKeyDown={onRoleKey}
                      onBlur={commitRole}
                      className="h-7 min-w-32 flex-1 bg-transparent px-1.5 text-sm outline-none placeholder:text-text-secondary"
                    />
                  ) : null}
                </div>
                <p id={`${field("roles")}-hint`} className="text-xs text-text-secondary">{t("form.rolesHint", { count: LIMITS.roles })}</p>
                <Issue id={`${field("roles")}-roleissue`} text={roleIssue} />
                <Issue id={`${field("roles")}-issue`} text={issueText("roles")} />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={field("playersNeeded")} className="text-sm font-medium">{t("form.players")} <Required /></label>
                <div className="flex h-10 items-stretch overflow-hidden rounded-xl bg-surface-elevated">
                  <button type="button" onClick={() => update({ playersNeeded: Math.max(LIMITS.playersMin, form.playersNeeded - 1) })} disabled={form.playersNeeded <= LIMITS.playersMin} aria-label={t("form.playersLess")} className="inline-flex w-11 items-center justify-center text-text-secondary hover:bg-surface hover:text-text-primary disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-focus">
                    <Icon name="minus" className="size-4" />
                  </button>
                  <input
                    id={field("playersNeeded")}
                    type="number"
                    inputMode="numeric"
                    min={LIMITS.playersMin}
                    max={LIMITS.playersMax}
                    value={form.playersNeeded}
                    aria-invalid={issues.playersNeeded ? true : undefined}
                    aria-describedby={describe("playersNeeded")}
                    onChange={(event) => update({ playersNeeded: Number.parseInt(event.target.value, 10) || 0 })}
                    className="w-full min-w-0 flex-1 bg-transparent text-center text-sm font-semibold tabular-nums outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
                  />
                  <button type="button" onClick={() => update({ playersNeeded: Math.min(LIMITS.playersMax, form.playersNeeded + 1) })} disabled={form.playersNeeded >= LIMITS.playersMax} aria-label={t("form.playersMore")} className="inline-flex w-11 items-center justify-center text-text-secondary hover:bg-surface hover:text-text-primary disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-focus">
                    <Icon name="plus" className="size-4" />
                  </button>
                </div>
                <Issue id={`${field("playersNeeded")}-issue`} text={issueText("playersNeeded")} />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={field("skillLevel")} className="text-sm font-medium">
                  {t("form.skill")} <span className="font-normal text-text-secondary">{t("form.optional")}</span>
                </label>
                <input
                  id={field("skillLevel")}
                  value={form.skillLevel}
                  maxLength={LIMITS.skillLevel}
                  placeholder={t("form.skillPlaceholder")}
                  aria-invalid={issues.skillLevel ? true : undefined}
                  aria-describedby={describe("skillLevel", `${field("skillLevel")}-hint`)}
                  onChange={(event) => update({ skillLevel: event.target.value })}
                  className={cn(ovalControlClass, ovalControlSize.md, "placeholder:text-text-secondary aria-invalid:border-primary")}
                />
                <p id={`${field("skillLevel")}-hint`} className="text-xs text-text-secondary">{t("form.skillHint")}</p>
                <Issue id={`${field("skillLevel")}-issue`} text={issueText("skillLevel")} />
              </div>
            </div>
          </FormSection>

          <FormSection icon="calendar" title={t("form.sections.time")}>
            <div className="grid gap-4 md:grid-cols-2">
              <DateTimeField id={field("playTimeStart")} label={t("form.playStart")} optional={t("form.optional")} value={form.playTimeStart} issue={issueText("playTimeStart")} onChange={(value) => update({ playTimeStart: value, ...(value ? {} : { playTimeEnd: "" }) })} />
              <DateTimeField id={field("playTimeEnd")} label={t("form.playEnd")} optional={t("form.optional")} value={form.playTimeEnd} issue={issueText("playTimeEnd")} disabled={!form.playTimeStart} onChange={(value) => update({ playTimeEnd: value })} />
              <p className="-mt-2 text-xs text-text-secondary md:col-span-2">{t("form.playHint")}</p>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={field("durationHours")} className="text-sm font-medium">{t("form.duration")} <Required /></label>
                {mode === "create" ? (
                  <Select
                    id={field("durationHours")}
                    value={String(form.durationHours)}
                    onChange={(value) => update({ durationHours: Number(value) })}
                    options={DURATION_OPTIONS.map((hours) => ({ value: String(hours), label: t("form.durationOption", { hours }) }))}
                    aria-describedby={`${field("durationHours")}-hint`}
                  />
                ) : (
                  <p id={field("durationHours")} className="flex h-10 items-center rounded-xl bg-surface-elevated px-3.5 text-sm">{format.dateTime(new Date(expiresAt), { dateStyle: "medium", timeStyle: "short" })}</p>
                )}
                <p id={`${field("durationHours")}-hint`} className="text-xs text-text-secondary">
                  {mode === "create" ? t("form.durationHint", { time: format.dateTime(new Date(expiresAt), { dateStyle: "medium", timeStyle: "short" }) }) : t("form.durationLocked")}
                </p>
              </div>

              <fieldset className="flex min-w-0 flex-col gap-1.5">
                <legend className="mb-1.5 text-sm font-medium">{t("form.mic")} <Required /></legend>
                <div role="radiogroup" className="grid grid-cols-2 gap-2">
                  <ChoiceChip name={`${id}-mic`} icon="volume" checked={form.micRequired} onChange={() => update({ micRequired: true })} label={t("form.micRequired")} />
                  <ChoiceChip name={`${id}-mic`} icon="volumeOff" checked={!form.micRequired} onChange={() => update({ micRequired: false })} label={t("form.micOptional")} />
                </div>
              </fieldset>
            </div>
          </FormSection>

          <FormSection icon="fileText" title={t("form.sections.description")}>
            <div className="flex flex-col gap-1.5">
              <label htmlFor={field("description")} className="sr-only">{t("form.description")}</label>
              <textarea
                id={field("description")}
                value={form.description}
                rows={5}
                placeholder={t("form.descriptionPlaceholder")}
                aria-invalid={issues.description ? true : undefined}
                aria-describedby={describe("description", `${field("description")}-count`)}
                onChange={(event) => update({ description: event.target.value })}
                className="w-full resize-y rounded-xl border border-transparent bg-surface-elevated px-3.5 py-2.5 text-sm placeholder:text-text-secondary hover:border-control-border focus-visible:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus aria-invalid:border-primary"
              />
              <div className="flex justify-between gap-3">
                <Issue id={`${field("description")}-issue`} text={issueText("description")} />
                <p id={`${field("description")}-count`} className={cn("ms-auto text-xs tabular-nums", length(form.description.trim()) > LIMITS.description ? "text-brand-text" : "text-text-secondary")}>
                  {t("form.counter", { count: length(form.description.trim()), max: LIMITS.description })}
                </p>
              </div>
            </div>
          </FormSection>

          {submitError ? (
            <p role="alert" className="flex items-center gap-2 rounded-panel border border-primary/50 bg-surface px-4 py-3 text-sm">
              <Icon name="info" className="size-4 shrink-0 text-brand-text" />
              {submitError}
            </p>
          ) : null}

          <div className="flex flex-wrap justify-end gap-2">
            <Link href={mode === "edit" ? "/find-team/mine" : "/find-team"} className={buttonClass("secondary", "md", "h-11 px-6")}>{t("form.cancel")}</Link>
            <button type="submit" disabled={busy} aria-busy={busy || undefined} className={buttonClass("primary", "md", "h-11 px-6")}>
              <Icon name="send" className="size-4" />
              {busy ? t("form.saving") : mode === "edit" ? t("form.save") : t("form.publish")}
            </button>
          </div>
        </form>

        <aside aria-labelledby={`${id}-preview`} className="space-y-3 lg:sticky lg:top-header-offset">
          <h2 id={`${id}-preview`} className="flex items-center gap-2 font-display text-lg font-semibold">
            <Icon name="eye" className="size-5 text-brand-text" />
            {t("form.preview")}
          </h2>
          <ListingCard
            listing={preview}
            owner={{ userId: meId, name: t("form.you"), handle: null, avatarUrl: null }}
            game={{ title: preview.gameTitle, coverUrl }}
            mine={false}
            contacting={false}
            contactError={null}
            onContact={() => undefined}
            variant="preview"
          />
        </aside>
      </div>

      {picking ? (
        <ModalDialog title={t("form.pickGame")} onClose={() => setPicking(false)} closeLabel={t("filters.close")}>
          <GamePicker
            onPick={(game) => {
              setPicking(false);
              update({ gameId: game.gameId, gameTitle: game.title, gameCoverUrl: game.coverUrl ?? null });
            }}
          />
        </ModalDialog>
      ) : null}
    </div>
  );
}

function limitFor(name: FormField): number {
  if (name === "skillLevel") return LIMITS.skillLevel;
  if (name === "description") return LIMITS.description;
  if (name === "playTimeEnd") return LIMITS.playSpanHours;
  return LIMITS.playersMax;
}

function FormSection({ icon, title, children }: Readonly<{ icon: IconName; title: string; children: ReactNode }>) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="rounded-panel border border-border bg-surface p-4 sm:p-5">
      <h2 id={id} className="mb-4 flex items-center gap-2.5 font-display text-lg font-semibold">
        <Icon name={icon} className="size-5 text-brand-text" />
        {title}
      </h2>
      {children}
    </section>
  );
}

function Required() {
  return <span aria-hidden="true" className="text-brand-text">*</span>;
}

function Issue({ id, text }: Readonly<{ id: string; text: string | null }>) {
  if (!text) return null;
  return <p id={id} className="text-xs text-brand-text">{text}</p>;
}

/** Radyo düğmesi görünümünde seçim kutucuğu (yerel radyo: ok tuşları ve grup adı tarayıcıdan). */
function ChoiceChip({ name, checked, onChange, label, icon }: Readonly<{ name: string; checked: boolean; onChange: () => void; label: string; icon?: IconName }>) {
  return (
    <label
      className={cn(
        "relative flex h-10 cursor-pointer items-center justify-center gap-2 rounded-xl border px-3 text-sm font-semibold transition-colors duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus motion-reduce:transition-none",
        checked ? "border-primary bg-primary/12 text-text-primary" : "border-transparent bg-surface-elevated text-text-secondary hover:border-control-border"
      )}
    >
      <input type="radio" name={name} checked={checked} onChange={onChange} className="sr-only" />
      {icon ? <Icon name={icon} className={cn("size-4", checked && "text-brand-text")} /> : null}
      <span className="truncate">{label}</span>
    </label>
  );
}

function DateTimeField({ id, label, optional, value, issue, disabled = false, onChange }: Readonly<{ id: string; label: string; optional: string; value: string; issue: string | null; disabled?: boolean; onChange: (value: string) => void }>) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label} <span className="font-normal text-text-secondary">{optional}</span>
      </label>
      <input
        id={id}
        type="datetime-local"
        value={value}
        disabled={disabled}
        aria-invalid={issue ? true : undefined}
        aria-describedby={issue ? `${id}-issue` : undefined}
        onChange={(event) => onChange(event.target.value)}
        className={cn(ovalControlClass, ovalControlSize.md, "[color-scheme:light_dark] aria-invalid:border-primary")}
      />
      <Issue id={`${id}-issue`} text={issue} />
    </div>
  );
}
