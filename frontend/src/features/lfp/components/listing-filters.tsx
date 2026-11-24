"use client";

import { useEffect, useId, useState } from "react";
import { useTranslations } from "next-intl";
import { GamePicker } from "@/features/game/components/game-picker";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { Select } from "@/shared/components/ui/select";
import { ovalControlClass, ovalControlSize } from "@/shared/components/ui/styles";
import { cn } from "@/shared/utils/cn";
import { LIMITS } from "../schemas/listing-form";
import { PLATFORMS, WHEN_PRESETS, type ListingFilters, type Platform, type WhenPreset } from "../types/lfp.types";
import { usePlatformLabel } from "./listing-parts";

type ListingFiltersProps = {
  filters: ListingFilters;
  /** Seçili oyunun adı (adres çubuğunda yalnız kimlik tutulur). */
  gameTitle: string | null;
  onChange: (next: ListingFilters, gameTitle?: string | null) => void;
};

const ANY = "";
const DEBOUNCE_MS = 400;

/** Metin süzgeci: yazdıkça değil, kısa bir duraksamadan sonra uygulanır (web-rules §7.1 debounce). */
function TextFilter({ label, value, max, placeholder, onCommit }: Readonly<{ label: string; value: string; max: number; placeholder: string; onCommit: (value: string) => void }>) {
  const id = useId();
  const [draft, setDraft] = useState(value);
  const [shownFor, setShownFor] = useState(value);
  if (shownFor !== value) {
    setShownFor(value);
    setDraft(value);
  }
  useEffect(() => {
    if (draft.trim() === value) return;
    const timer = setTimeout(() => onCommit(draft.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [draft, value, onCommit]);
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-text-primary">
        {label}
      </label>
      <input
        id={id}
        value={draft}
        maxLength={max}
        placeholder={placeholder}
        onChange={(event) => setDraft(event.target.value)}
        className={cn(ovalControlClass, ovalControlSize.md, "placeholder:text-text-secondary")}
      />
    </div>
  );
}

/**
 * Süzgeç paneli (LFP-11): oyun, platform, seviye, rol, oyun zamanı ve mikrofon. Süzgeçler birlikte (VE) uygulanır ve adres
 * çubuğunda tutulur. Seviye süzgecinde seviyesi belirtilmemiş ilanlar da eşleşir (her seviyeye açık ilan).
 */
export function ListingFiltersPanel({ filters, gameTitle, onChange }: ListingFiltersProps) {
  const t = useTranslations("Lfp.filters");
  const platform = usePlatformLabel();
  const [picking, setPicking] = useState(false);
  const ids = { platform: useId(), when: useId(), mic: useId() };
  const set = (patch: Partial<ListingFilters>) => onChange({ ...filters, ...patch });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium text-text-primary">{t("game")}</span>
        {filters.gameId ? (
          <div className="flex items-center gap-2 rounded-xl bg-surface-elevated px-3.5 py-2">
            <Icon name="gamepad" className="size-4 shrink-0 text-text-secondary" />
            <span className="min-w-0 flex-1 truncate text-sm">{gameTitle ?? t("selectedGame")}</span>
            <button
              type="button"
              onClick={() => onChange({ ...filters, gameId: undefined }, null)}
              aria-label={t("clearGame")}
              className="inline-flex size-7 items-center justify-center rounded-full text-text-secondary hover:bg-surface hover:text-text-primary focus-visible:outline-2 focus-visible:outline-focus"
            >
              <Icon name="close" className="size-4" />
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => setPicking(true)} className={cn(ovalControlClass, ovalControlSize.md, "flex items-center gap-2 text-start text-text-secondary")}>
            <Icon name="gamepad" className="size-4 shrink-0" />
            <span className="flex-1">{t("allGames")}</span>
            <Icon name="search" className="size-4 shrink-0" />
          </button>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.platform} className="text-sm font-medium">{t("platform")}</label>
        <Select
          id={ids.platform}
          value={filters.platform ?? ANY}
          onChange={(value) => set({ platform: value ? (value as Platform) : undefined })}
          options={[{ value: ANY, label: t("all") }, ...PLATFORMS.map((value) => ({ value, label: platform(value) }))]}
        />
      </div>

      <TextFilter label={t("skill")} value={filters.skillLevel ?? ""} max={LIMITS.skillLevel} placeholder={t("skillPlaceholder")} onCommit={(value) => set({ skillLevel: value || undefined })} />
      <TextFilter label={t("role")} value={filters.role ?? ""} max={LIMITS.role} placeholder={t("rolePlaceholder")} onCommit={(value) => set({ role: value || undefined })} />

      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.when} className="text-sm font-medium">{t("when")}</label>
        <Select
          id={ids.when}
          value={filters.when ?? ANY}
          onChange={(value) => set({ when: value ? (value as WhenPreset) : undefined })}
          options={[{ value: ANY, label: t("anyTime") }, ...WHEN_PRESETS.map((value) => ({ value, label: t(`whenOptions.${value}`) }))]}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.mic} className="text-sm font-medium">{t("mic")}</label>
        <Select
          id={ids.mic}
          value={filters.micRequired === undefined ? ANY : filters.micRequired ? "required" : "optional"}
          onChange={(value) => set({ micRequired: value === ANY ? undefined : value === "required" })}
          options={[
            { value: ANY, label: t("micAny") },
            { value: "required", label: t("micRequired") },
            { value: "optional", label: t("micOptional") }
          ]}
        />
      </div>

      {picking ? (
        <ModalDialog title={t("pickGame")} onClose={() => setPicking(false)} closeLabel={t("close")}>
          <GamePicker
            onPick={(game) => {
              setPicking(false);
              onChange({ ...filters, gameId: game.gameId }, game.title);
            }}
          />
          <div className="flex justify-end">
            <button type="button" onClick={() => setPicking(false)} className={buttonClass("ghost")}>
              {t("close")}
            </button>
          </div>
        </ModalDialog>
      ) : null}
    </div>
  );
}
