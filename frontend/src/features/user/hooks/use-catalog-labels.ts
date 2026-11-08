"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { GENRE_CODES, PLATFORMS, PLATFORM_GROUPS } from "../catalog/games";
import type { PickerGroup, PickerOption } from "../components/option-picker";

/** Tür ve platform kodlarının o dildeki etiketleri; katalogda olmayan (eski, serbest yazılmış) kod olduğu gibi döner. */
export function useCatalogLabels() {
  const t = useTranslations("Profile.catalog");

  return useMemo(() => {
    const genre = (code: string) => (t.has(`genre.${code}`) ? t(`genre.${code}`) : code);
    const platform = (code: string) => (t.has(`platform.${code}`) ? t(`platform.${code}`) : code);
    const genreOptions: PickerOption[] = GENRE_CODES.map((code) => ({ code, label: genre(code) }));
    const platformOptions: PickerOption[] = PLATFORMS.map(({ code, group }) => ({ code, group, label: platform(code) }));
    const platformGroups: PickerGroup[] = PLATFORM_GROUPS.map((id) => ({ id, label: t(`platformGroup.${id}`) }));
    return { genre, platform, genreOptions, platformOptions, platformGroups };
  }, [t]);
}
