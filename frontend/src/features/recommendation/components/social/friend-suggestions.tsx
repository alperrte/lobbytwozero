"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { SIZE_LIMITS } from "../../schemas/filters";
import { ConsentGate } from "../hub/consent-gate";
import { SectionHeading, panelClass } from "../recommendation-ui";
import { PeopleSuggestions } from "./people-suggestions";

const COMPACT_LIMIT = 3;

/**
 * Arkadaş önerileri. Mevcut arkadaşlar backend'de zaten çıkarılmıştır. `compact`: Arkadaşlar sayfasının yan panelinde
 * kendi rıza kapısıyla küçük liste (ilk 3) ve tümünü görme bağlantısı; hub'da tam liste.
 */
export function FriendSuggestions({ compact = false }: { compact?: boolean }) {
  const t = useTranslations("Recommendation.social");
  const headingId = useId();
  if (compact) {
    return <section aria-labelledby={headingId} className={cn(panelClass, "p-4")}>
      <h2 id={headingId} className="font-display text-base font-semibold">{t("friends.compactTitle")}</h2>
      <p className="mt-1 text-xs leading-5 text-text-secondary">{t("friends.compactDescription")}</p>
      <div className="mt-3">
        <ConsentGate compact>
          <PeopleSuggestions kind="FRIEND_SUGGESTIONS" filters={{ size: SIZE_LIMITS.people.fallback }} compact limit={COMPACT_LIMIT} />
        </ConsentGate>
      </div>
      <Link href="/for-you/friends" className={buttonClass("ghost", "sm", "mt-3 w-full")}>{t("friends.seeAll")}</Link>
    </section>;
  }
  return <section aria-labelledby={headingId}>
    <SectionHeading id={headingId} title={t("friends.title")} description={t("friends.description")} />
    <PeopleSuggestions kind="FRIEND_SUGGESTIONS" filters={{ size: SIZE_LIMITS.people.fallback }} />
  </section>;
}
