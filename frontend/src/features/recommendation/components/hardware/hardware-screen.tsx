"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Select } from "@/shared/components/ui/select";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useCatalogTerms, useGameCards } from "../../hooks/use-enrichment";
import { useHardwareCompatible, useHardwareProfiles, type CompatibleQuery } from "../../hooks/use-hardware-recommendation";
import { useLabels } from "../../hooks/use-labels";
import { COMPATIBLE_SIZE_OPTIONS, clampSize, isRequirementPlatform, optionalCode } from "../../schemas/filters";
import { requirementPlatforms, type RequirementPlatform } from "../../types/recommendation";
import { useTermOptions } from "../games/game-filters";
import { LimitationsNotice, RecommendationEmpty, RecommendationError, RecommendationSkeleton, SectionHeading, panelClass } from "../recommendation-ui";
import { AssessmentDialog, type AssessmentTarget } from "./assessment-dialog";
import { CompatibleGameCard } from "./compatible-game-card";

/**
 * Donanıma uygun oyunlar. Liste yalnız minimum gereksinimin altında kaldığı KANITLANAN oyunları dışarıda bırakır; geri kalanlar
 * "minimum işlem gücü beyanı karşılanıyor" veya "doğrulanamadı" olarak gösterilir. İstek yalnız "Önerileri getir" ile atılır.
 */
export function HardwareScreen() {
  const t = useTranslations("Recommendation.hardware");
  const filters = useTranslations("Recommendation.filters");
  const id = useId();
  const profiles = useHardwareProfiles();
  const labels = useLabels();
  const genres = useTermOptions("GENRE", filters("allGenres"));
  const genreTerms = useCatalogTerms("GENRE");
  const [profileId, setProfileId] = useState("");
  const [platform, setPlatform] = useState<RequirementPlatform>("windows");
  const [genre, setGenre] = useState("");
  const [size, setSize] = useState(10);
  const [query, setQuery] = useState<CompatibleQuery | null>(null);
  const [target, setTarget] = useState<AssessmentTarget | null>(null);

  const list = profiles.data ?? [];
  const selected = list.some((profile) => profile.id === profileId) ? profileId : (list[0]?.id ?? "");
  const compatible = useHardwareCompatible(query);
  const items = compatible.data?.items ?? [];
  const cards = useGameCards(items.map((item) => item.gameId));

  if (profiles.loading && !profiles.data) return <RecommendationSkeleton count={2} variant="row" />;
  if (profiles.error) return <RecommendationError error={profiles.error} retry={profiles.reload} />;
  if (!list.length) {
    return <RecommendationEmpty icon="cpu" title={t("noProfileTitle")} description={t("noProfileDescription")}>
      <Link href="/profile/computers" className={buttonClass("primary", "md")}>{t("addProfile")}</Link>
    </RecommendationEmpty>;
  }

  const profileOptions = list.map((profile) => ({ value: profile.id, label: profile.name }));
  const platformOptions = requirementPlatforms.map((value) => ({ value, label: labels.requirementPlatform(value) }));
  const sizeOptions = COMPATIBLE_SIZE_OPTIONS.map((value) => ({ value: String(value), label: filters("sizeOption", { count: value }) }));
  const profileName = list.find((profile) => profile.id === query?.profileId)?.name;

  function fetchList() {
    if (!selected) return;
    setQuery({ profileId: selected, platform, genre: optionalCode(genre), size: clampSize("compatible", size) });
  }

  return <div className="space-y-10">
    <section aria-labelledby={`${id}-filters`} className={cn(panelClass, "p-5 sm:p-6")}>
      <SectionHeading id={`${id}-filters`} title={t("filtersTitle")} description={t("filtersDescription")} />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="min-w-0">
          <label htmlFor={`${id}-profile`} className="mb-1.5 block text-sm font-medium">{t("profile")}</label>
          <Select id={`${id}-profile`} options={profileOptions} value={selected} onChange={setProfileId} className="w-full" />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-platform`} className="mb-1.5 block text-sm font-medium">{t("platform")}</label>
          <Select id={`${id}-platform`} options={platformOptions} value={platform} onChange={(value) => isRequirementPlatform(value) && setPlatform(value)} className="w-full" />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-genre`} className="mb-1.5 block text-sm font-medium">{filters("genre")}</label>
          <Select id={`${id}-genre`} options={genres.options} value={genre} onChange={setGenre} className="w-full" />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-size`} className="mb-1.5 block text-sm font-medium">{filters("size")}</label>
          <Select id={`${id}-size`} options={sizeOptions} value={String(size)} onChange={(value) => setSize(Number(value))} className="w-full" />
        </div>
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button type="button" className={buttonClass("primary", "md")} disabled={compatible.loading} aria-busy={compatible.loading} onClick={fetchList}>
          <Icon name="cpu" className="size-4" />{t("fetch")}
        </button>
        <Link href="/profile/computers" className={buttonClass("ghost", "sm")}>{t("manageProfiles")}</Link>
      </div>
      <p className="mt-3 text-xs leading-5 text-text-secondary">{t("fetchNote")}</p>
    </section>

    <section aria-labelledby={`${id}-results`} aria-busy={compatible.loading}>
      <SectionHeading id={`${id}-results`} title={t("resultsTitle")} description={profileName ? t("resultsFor", { profile: profileName, platform: labels.requirementPlatform(query?.platform ?? platform) }) : undefined} />
      {query === null
        ? <RecommendationEmpty icon="cpu" title={t("idleTitle")} description={t("idleDescription")} />
        : compatible.loading
          ? <RecommendationSkeleton count={3} />
          : compatible.error
            ? <RecommendationError error={compatible.error} retry={compatible.reload} />
            : compatible.data
              ? <div className="space-y-5">
                {compatible.data.excludedBelowMinimum > 0 ? <p className="text-sm leading-6 text-text-secondary">{t("excluded", { count: compatible.data.excludedBelowMinimum })}</p> : null}
                <LimitationsNotice codes={compatible.data.limitations} />
                {items.length === 0
                  ? <RecommendationEmpty icon="gamepad" title={t("emptyTitle")} description={t("emptyDescription")} />
                  : <>
                    {cards.failed ? <p className="text-sm text-text-secondary">{t("detailsUnavailable")}</p> : null}
                    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label={t("listLabel")}>
                      {items.map((item, index) => {
                        const card = cards.data?.get(item.gameId.toLowerCase()) ?? null;
                        const available = card !== null || cards.data === undefined || cards.failed;
                        return <li key={item.gameId} className="min-w-0">
                          <CompatibleGameCard item={item} rank={index + 1} card={card} available={available}
                            genreLabel={(code) => labels.term(genreTerms.data, code)}
                            onAssess={() => setTarget({ hardwareProfileId: query.profileId, gameId: item.gameId, platform: query.platform, gameTitle: card?.title ?? t("unknownTitle") })} />
                        </li>;
                      })}
                    </ul>
                  </>}
              </div>
              : null}
    </section>

    {target ? <AssessmentDialog target={target} onClose={() => setTarget(null)} /> : null}
  </div>;
}
