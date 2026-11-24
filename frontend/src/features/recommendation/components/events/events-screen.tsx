"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { SIZE_LIMITS } from "../../schemas/filters";
import { SectionHeading } from "../recommendation-ui";
import { EventSuggestions } from "./event-suggestions";

/** Yaklaşan ve katılıma açık etkinliklerden, oyun tercihleriyle örtüşenler. */
export function EventsScreen() {
  const t = useTranslations("Recommendation.events");
  const headingId = useId();
  return <section aria-labelledby={headingId}>
    <SectionHeading id={headingId} title={t("title")} description={t("description")} />
    <EventSuggestions size={SIZE_LIMITS.events.fallback} />
  </section>;
}
