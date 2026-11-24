"use client";

import { useId, useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { OptionGroup } from "@/shared/components/ui/option-group";
import { SearchField } from "@/shared/components/ui/search-field";
import { Select } from "@/shared/components/ui/select";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { cn } from "@/shared/utils/cn";
import { taskFields, useAssistant, type AssistantInputs } from "../../hooks/use-assistant";
import { useCatalogSearch } from "../../hooks/use-catalog-search";
import { useHardwareProfiles } from "../../hooks/use-hardware-recommendation";
import { useLabels } from "../../hooks/use-labels";
import { isRequirementPlatform } from "../../schemas/filters";
import { assistantTasks, requirementPlatforms, type AssistantMissingField, type AssistantStep, type AssistantTask, type CatalogGame } from "../../types/recommendation";
import { AssessmentDialog, type AssessmentTarget } from "../hardware/assessment-dialog";
import { GenerateLabel } from "../games/games-screen";
import { useTermOptions } from "../games/game-filters";
import { RecommendationError, panelClass } from "../recommendation-ui";

const TASK_ICONS: Record<AssistantTask, IconName> = { SELECT_GAME: "gamepad", ASSESS_GAME: "cpu", VIEW_HISTORY: "clock", GIVE_FEEDBACK: "star" };

const FIELD_OF: Record<AssistantMissingField, keyof AssistantInputs> = {
  GENRE: "genre",
  HARDWARE_PROFILE_ID: "hardwareProfileId",
  GAME_ID: "gameId",
  PLATFORM: "platform"
};

/** Rehber adım kodu → uygulamadaki gerçek hedef. Donanım adımları değerlendirme penceresine (alanlar tamsa) veya donanım bölümüne gider. */
const STEP_TARGET: Record<AssistantStep, AppPathname | "assessment"> = {
  OPEN_GAME_DISCOVERY: "/games",
  FILTER_GENRE: "/for-you/games",
  VERIFY_GAME_REQUIREMENTS: "assessment",
  REVIEW_MINIMUM_DEFICIT: "assessment",
  COMPARE_RECOMMENDED_LEVEL: "assessment",
  CHECK_UNKNOWN_REQUIREMENTS: "assessment",
  MEASURE_IN_GAME: "/performance",
  OPEN_RECOMMENDATION_HISTORY: "/for-you/history",
  SELECT_OWN_ITEM: "/for-you/history",
  SEND_FEEDBACK: "/for-you/history"
};

/**
 * LTZ Rehber akışı: kullanıcı yapmak istediği işi seçer, gereken alanları doldurur, Recommendation Service kural tabanlı
 * yönlendirme döner. Serbest metin yoktur; yanıt `message` (sunucuda yerelleştirilmiş) + adım kodları + eksik alanlardır.
 */
export function GuideFlow({ onNavigate }: { onNavigate: () => void }) {
  const t = useTranslations("Recommendation.guide");
  const locale = useLocale();
  const id = useId();
  const assistant = useAssistant();
  const [task, setTask] = useState<AssistantTask>("SELECT_GAME");
  const [inputs, setInputs] = useState<AssistantInputs>({});
  const [game, setGame] = useState<CatalogGame | null>(null);
  const [assessment, setAssessment] = useState<AssessmentTarget | null>(null);
  const response = assistant.response?.task === task ? assistant.response.value : undefined;
  const missing = new Set((response?.missingFields ?? []).map((field) => FIELD_OF[field]));
  const fields = taskFields(task);

  function changeTask(next: AssistantTask) {
    setTask(next);
    assistant.reset();
    assistant.clearError();
  }
  function update(patch: AssistantInputs) {
    setInputs((current) => ({ ...current, ...patch }));
  }
  const assessmentTarget: AssessmentTarget | null = inputs.hardwareProfileId && game && inputs.platform && isRequirementPlatform(inputs.platform)
    ? { hardwareProfileId: inputs.hardwareProfileId, gameId: game.gameId, platform: inputs.platform, gameTitle: game.title }
    : null;

  return <div className="space-y-6">
    <OptionGroup
      legend={t("taskLegend")}
      name={`${id}-task`}
      value={task}
      onChange={changeTask}
      disabled={assistant.pending}
      options={assistantTasks.map((value) => ({ value, label: t(`tasks.${value}.label`), hint: t(`tasks.${value}.hint`), leading: <Icon name={TASK_ICONS[value]} className="size-4" /> }))}
    />

    {fields.length ? <fieldset className="min-w-0 space-y-4" disabled={assistant.pending}>
      <legend className="mb-2 font-display text-sm font-semibold">{t("fieldsLegend")}</legend>
      {fields.includes("genre") ? <GenreField id={`${id}-genre`} value={inputs.genre ?? ""} missing={missing.has("genre")} onChange={(genre) => update({ genre })} /> : null}
      {fields.includes("hardwareProfileId") ? <ProfileField id={`${id}-profile`} value={inputs.hardwareProfileId ?? ""} missing={missing.has("hardwareProfileId")} onChange={(hardwareProfileId) => update({ hardwareProfileId })} onNavigate={onNavigate} /> : null}
      {fields.includes("gameId") ? <GameField id={`${id}-game`} value={game} missing={missing.has("gameId")} onChange={(next) => { setGame(next); update({ gameId: next?.gameId }); }} /> : null}
      {fields.includes("platform") ? <PlatformField id={`${id}-platform`} value={inputs.platform ?? ""} missing={missing.has("platform")} onChange={(platform) => update({ platform })} /> : null}
    </fieldset> : null}

    <div className="space-y-2">
      <button type="button" className={buttonClass("primary", "md", "w-full")} disabled={assistant.pending || assistant.cooldown > 0} aria-busy={assistant.pending} onClick={() => void assistant.ask(task, locale, { ...inputs, gameId: game?.gameId })}>
        <GenerateLabel pending={assistant.pending} cooldown={assistant.cooldown} idle={t("ask")} />
      </button>
      <p className="text-xs leading-5 text-text-secondary">{t("ruleBasedNote")}</p>
    </div>

    {assistant.error ? <RecommendationError error={assistant.error} compact /> : null}

    {response ? <section aria-live="polite" aria-labelledby={`${id}-answer`} className={cn(panelClass, "space-y-4 p-4")}>
      <div>
        <p className="text-xs font-semibold text-brand-text">{t(`status.${response.status}`)}</p>
        <h3 id={`${id}-answer`} className="sr-only">{t("answerTitle")}</h3>
        <p className="mt-1 text-sm leading-6">{response.message}</p>
      </div>
      {response.missingFields.length ? <p className="flex items-start gap-2 text-sm text-text-secondary">
        <Icon name="info" className="mt-0.5 size-4 shrink-0" />
        {t("missingFields", { fields: response.missingFields.map((field) => t(`fields.${field}`)).join(", ") })}
      </p> : null}
      {response.steps.length ? <ol className="space-y-2">
        {response.steps.map((step, index) => <li key={`${step}-${index}`}>
          <StepAction step={step} index={index + 1} onNavigate={onNavigate} onAssess={assessmentTarget ? () => setAssessment(assessmentTarget) : undefined} />
        </li>)}
      </ol> : null}
    </section> : null}

    {assessment ? <AssessmentDialog target={assessment} onClose={() => setAssessment(null)} /> : null}
  </div>;
}

function StepAction({ step, index, onNavigate, onAssess }: { step: AssistantStep; index: number; onNavigate: () => void; onAssess?: () => void }) {
  const t = useTranslations("Recommendation.guide");
  const target = STEP_TARGET[step];
  const rowClass = "flex w-full items-center gap-3 rounded-control border border-border px-3 py-2.5 text-start text-sm transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";
  const content: ReactNode = <>
    <span aria-hidden="true" className="flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-elevated text-xs font-semibold text-text-secondary">{index}</span>
    <span className="min-w-0 flex-1">{t(`steps.${step}`)}</span>
    <Icon name="chevron" className="size-4 shrink-0 -rotate-90 text-text-secondary" />
  </>;
  if (target === "assessment") {
    return onAssess
      ? <button type="button" className={rowClass} aria-haspopup="dialog" onClick={onAssess}>{content}</button>
      : <Link href="/for-you/hardware" className={rowClass} onClick={onNavigate}>{content}</Link>;
  }
  return <Link href={target} className={rowClass} onClick={onNavigate}>{content}</Link>;
}

function FieldShell({ id, label, missing, hint, children }: { id: string; label: string; missing: boolean; hint?: ReactNode; children: ReactNode }) {
  const t = useTranslations("Recommendation.guide");
  return <div className="min-w-0">
    <label htmlFor={id} className="mb-1.5 block text-sm font-medium">{label}</label>
    {children}
    {missing ? <p id={`${id}-missing`} className="mt-1.5 flex items-center gap-1.5 text-xs text-brand-text"><Icon name="info" className="size-3.5 shrink-0" />{t("fieldMissing")}</p> : null}
    {hint ? <div className="mt-1.5 text-xs leading-5 text-text-secondary">{hint}</div> : null}
  </div>;
}

function GenreField({ id, value, missing, onChange }: { id: string; value: string; missing: boolean; onChange: (value: string) => void }) {
  const t = useTranslations("Recommendation.guide");
  const genres = useTermOptions("GENRE", t("notSelected"));
  return <FieldShell id={id} label={t("fields.GENRE")} missing={missing} hint={genres.failed ? t("catalogUnavailable") : undefined}>
    <Select id={id} options={genres.options} value={value} onChange={onChange} className="w-full" aria-invalid={missing || undefined} aria-describedby={missing ? `${id}-missing` : undefined} />
  </FieldShell>;
}

function ProfileField({ id, value, missing, onChange, onNavigate }: { id: string; value: string; missing: boolean; onChange: (value: string) => void; onNavigate: () => void }) {
  const t = useTranslations("Recommendation.guide");
  const profiles = useHardwareProfiles();
  const options = [{ value: "", label: t("notSelected") }, ...(profiles.data ?? []).map((profile) => ({ value: profile.id, label: profile.name }))];
  const empty = profiles.data !== undefined && profiles.data.length === 0;
  return <FieldShell id={id} label={t("fields.HARDWARE_PROFILE_ID")} missing={missing} hint={empty
    ? <Link href="/profile/computers" className="font-medium text-brand-text underline-offset-2 hover:underline" onClick={onNavigate}>{t("addProfile")}</Link>
    : profiles.error ? t("profilesUnavailable") : undefined}>
    <Select id={id} options={options} value={value} onChange={onChange} disabled={profiles.loading && !profiles.data} className="w-full" aria-invalid={missing || undefined} aria-describedby={missing ? `${id}-missing` : undefined} />
  </FieldShell>;
}

function PlatformField({ id, value, missing, onChange }: { id: string; value: string; missing: boolean; onChange: (value: string) => void }) {
  const t = useTranslations("Recommendation.guide");
  const labels = useLabels();
  const options = [{ value: "", label: t("notSelected") }, ...requirementPlatforms.map((platform) => ({ value: platform, label: labels.requirementPlatform(platform) }))];
  return <FieldShell id={id} label={t("fields.PLATFORM")} missing={missing}>
    <Select id={id} options={options} value={value} onChange={onChange} className="w-full" aria-invalid={missing || undefined} aria-describedby={missing ? `${id}-missing` : undefined} />
  </FieldShell>;
}

/** Oyun seçimi: katalogda ada göre arama; seçilen oyun kimliği yalnız istekte kullanılır. */
function GameField({ id, value, missing, onChange }: { id: string; value: CatalogGame | null; missing: boolean; onChange: (value: CatalogGame | null) => void }) {
  const t = useTranslations("Recommendation.guide");
  const [query, setQuery] = useState("");
  const search = useCatalogSearch(value ? "" : query);

  if (value) {
    return <FieldShell id={id} label={t("fields.GAME_ID")} missing={missing}>
      <div className="flex min-h-10 items-center justify-between gap-3 rounded-control border border-border bg-surface-elevated px-3 py-2">
        <span id={id} className="flex min-w-0 items-center gap-2 text-sm font-medium"><Icon name="gamepad" className="size-4 shrink-0 text-text-secondary" /><span className="truncate">{value.title}</span></span>
        <button type="button" className={buttonClass("ghost", "sm")} onClick={() => { onChange(null); setQuery(""); }}>{t("changeGame")}</button>
      </div>
    </FieldShell>;
  }
  return <div className="min-w-0">
    <SearchField id={id} label={t("fields.GAME_ID")} showLabel value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t("gameSearchPlaceholder")} maxLength={128}
      aria-invalid={missing || undefined} aria-describedby={`${id}-status`} />
    {missing ? <p className="mt-1.5 flex items-center gap-1.5 text-xs text-brand-text"><Icon name="info" className="size-3.5 shrink-0" />{t("fieldMissing")}</p> : null}
    <p id={`${id}-status`} role="status" className="mt-1.5 text-xs text-text-secondary">
      {search.tooShort ? t("gameSearchHint") : search.loading ? t("searching") : search.failed ? t("catalogUnavailable") : search.items.length ? t("gameResults", { count: search.items.length }) : t("gameNoResults")}
    </p>
    {search.items.length ? <ul className="mt-2 max-h-56 space-y-1 overflow-y-auto overscroll-contain" aria-label={t("gameResultsLabel")}>
      {search.items.map((item) => <li key={item.gameId}>
        <button type="button" className="flex w-full items-center gap-2 rounded-control px-3 py-2 text-start text-sm transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus" onClick={() => onChange(item)}>
          <Icon name="gamepad" className="size-4 shrink-0 text-text-secondary" />
          <span className="min-w-0 truncate">{item.title}</span>
        </button>
      </li>)}
    </ul> : null}
  </div>;
}
