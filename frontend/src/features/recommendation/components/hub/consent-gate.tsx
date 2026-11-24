"use client";

import { useId, useMemo, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useSession } from "@/features/auth";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { RecommendationScopeContext } from "../../hooks/recommendation-scope";
import { useConsentGate } from "../../hooks/use-consent-gate";
import { InlineError, RecommendationError, RecommendationSkeleton, panelClass } from "../recommendation-ui";

type ConsentGateProps = {
  children: ReactNode;
  /** Kapının üstünde her durumda çizilen alan (ör. hub alt menüsü); `granted` rıza açıkken `true`. */
  header?: (granted: boolean) => ReactNode;
  /** Arkadaşlar sayfası gibi başka ekranlara gömülen küçük görünüm. */
  compact?: boolean;
};

/**
 * Kişiselleştirilmiş öneri rızası. Sunucu rızayı doğrulamadan hiçbir öneri isteği atılmaz: alt bileşenlerin kullandığı
 * `RecommendationScopeContext` yalnız rıza açıkken sağlanır. Oturum yoksa hiçbir şey çizilmez (sayfa çerçevesi girişe yönlendirir).
 */
export function ConsentGate({ children, header, compact = false }: ConsentGateProps) {
  const session = useSession();
  const accountId = session.status === "authenticated" ? session.me?.accountId ?? null : null;
  const gate = useConsentGate(accountId);
  const { consentRequired } = gate;
  const scope = useMemo(() => (accountId ? { accountId, consentRequired } : null), [accountId, consentRequired]);
  const granted = gate.state.status === "granted" && scope !== null;

  if (session.status === "guest") return null;
  return <>
    {header?.(granted)}
    {session.status === "loading" || gate.state.status === "loading"
      ? <RecommendationSkeleton count={compact ? 2 : 3} variant={compact ? "row" : "card"} />
      : gate.state.status === "error"
        ? <RecommendationError error={gate.state.error} retry={gate.reload} compact={compact} />
        : gate.state.status === "required" || !scope
          ? <ConsentRequired compact={compact} saving={gate.saving} saveError={gate.saveError} onGrant={gate.grant} />
          : <RecommendationScopeContext.Provider value={scope}>{children}</RecommendationScopeContext.Provider>}
  </>;
}

function ConsentRequired({ compact, saving, saveError, onGrant }: { compact: boolean; saving: boolean; saveError: unknown; onGrant: () => void }) {
  const t = useTranslations("Recommendation.consent");
  const titleId = useId();
  const Heading = compact ? "h3" : "h2";
  return <section aria-labelledby={titleId} className={cn(panelClass, "border-s-2 border-s-primary", compact ? "p-4" : "p-5 sm:p-7")}>
    <div className="flex items-start gap-4">
      {compact ? null : <span className="hidden size-11 shrink-0 items-center justify-center rounded-panel border border-border bg-surface-elevated sm:flex"><Icon name="shield" className="size-5 text-text-secondary" /></span>}
      <div className="min-w-0 max-w-2xl">
        <Heading id={titleId} className={cn("font-display font-semibold", compact ? "text-base" : "text-xl")}>{t("title")}</Heading>
        <p className="mt-2 text-sm leading-6 text-text-secondary">{t("description")}</p>
        {compact ? null : <ul className="mt-3 list-disc space-y-1 ps-5 text-sm leading-6 text-text-secondary">
          <li>{t("usesPreferences")}</li>
          <li>{t("usesActivity")}</li>
          <li>{t("revocable")}</li>
        </ul>}
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <button type="button" className={buttonClass("primary", compact ? "sm" : "md")} disabled={saving} aria-busy={saving} onClick={onGrant}>
            {saving ? t("saving") : t("grant")}
          </button>
          <Link href="/profile/settings" className={buttonClass("ghost", compact ? "sm" : "md")}>{t("settings")}</Link>
        </div>
        {saveError ? <div className="mt-3"><InlineError error={saveError} /></div> : null}
      </div>
    </div>
  </section>;
}
