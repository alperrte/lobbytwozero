"use client";

import { useId, useState } from "react";
import { useMessages, useTranslations } from "next-intl";
import { Select, type SelectOption } from "@/shared/components/ui/select";
import { cn } from "@/shared/utils/cn";
import { PLATFORM_PATTERN, SIZE_LIMITS, optionalCode } from "../../schemas/filters";
import { SectionHeading, panelClass } from "../recommendation-ui";
import { PeopleSuggestions } from "./people-suggestions";

type CatalogMessages = { Profile?: { catalog?: { genre?: Record<string, string>; platform?: Record<string, string> } } };

/** Oyuncu tercih kataloğu (kullanıcı profilindeki tür/platform kodları); benzer oyuncu filtresi bu kodlarla çalışır. */
function usePreferenceOptions(kind: "genre" | "platform", allLabel: string): SelectOption[] {
  const messages = useMessages() as CatalogMessages;
  const entries = Object.entries(messages.Profile?.catalog?.[kind] ?? {})
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label));
  return [{ value: "", label: allLabel }, ...entries];
}

/**
 * Benzer oyuncular: oyun tercihleri ve oyuncu tipi örtüşen kişiler. Arkadaş önerisinden farklı bir sıralamadır; filtre
 * değişince yeni liste istenir (aynı filtre 2 dk önbellekten gelir).
 */
export function SimilarPlayersScreen() {
  const t = useTranslations("Recommendation.social");
  const filters = useTranslations("Recommendation.filters");
  const id = useId();
  const genres = usePreferenceOptions("genre", filters("allGenres"));
  const platforms = usePreferenceOptions("platform", filters("allPlatforms"));
  const [genre, setGenre] = useState("");
  const [platform, setPlatform] = useState("");

  return <div className="space-y-6">
    <section aria-labelledby={`${id}-heading`}>
      <SectionHeading id={`${id}-heading`} title={t("players.title")} description={t("players.description")} />
      <div className={cn(panelClass, "grid gap-4 p-4 sm:grid-cols-2 sm:p-5")}>
        <div className="min-w-0">
          <label htmlFor={`${id}-genre`} className="mb-1.5 block text-sm font-medium">{filters("genre")}</label>
          <Select id={`${id}-genre`} options={genres} value={genre} onChange={setGenre} className="w-full" />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-platform`} className="mb-1.5 block text-sm font-medium">{t("players.platformFilter")}</label>
          <Select id={`${id}-platform`} options={platforms} value={platform} onChange={setPlatform} className="w-full" />
        </div>
      </div>
    </section>
    <PeopleSuggestions kind="SIMILAR_PLAYERS" filters={{ size: SIZE_LIMITS.people.fallback, genre: optionalCode(genre), platform: optionalCode(platform, PLATFORM_PATTERN) }} />
  </div>;
}
