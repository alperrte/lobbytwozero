"use client";

import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useIncomingRequests } from "../hooks/use-social-data";
import { AddByCodeForm } from "./add-by-code-form";
import { InviteCodeCard } from "./invite-code-card";
import { PanelCard } from "./panel-card";
import { RequestList } from "./request-list";

const PREVIEW_COUNT = 3;

/** Arkadaşlar sayfasının yan paneli: bekleyen ilk üç istek ve "Tümünü Gör". İstek yoksa panel hiç gösterilmez. */
export function IncomingRequestsPanel() {
  const t = useTranslations("Social");
  const incoming = useIncomingRequests(PREVIEW_COUNT);
  if (incoming.loading || incoming.error || incoming.items.length === 0) {
    return null;
  }
  return (
    <PanelCard
      id="social-incoming-preview"
      icon="userPlus"
      title={t("requests.incomingTitle")}
      aside={
        <Link
          href="/friends/requests"
          className="inline-flex items-center gap-1 rounded-control text-sm font-semibold text-brand-text underline-offset-4 hover:underline"
        >
          {t("requests.seeAll")}
          <Icon name="arrowRight" className="size-4 rtl:rotate-180" />
        </Link>
      }
    >
      <RequestList
        views={incoming.items.slice(0, PREVIEW_COUNT)}
        direction="incoming"
        layout="compact"
        label={t("requests.incomingListLabel")}
      />
    </PanelCard>
  );
}

/** Davet kodu kartı + kodla ekleme formu (Arkadaşlar sayfasında aynı kartta, taslaktaki gibi). */
export function InviteAndAddPanel() {
  return (
    <div className="space-y-5">
      <InviteCodeCard />
      <section className="rounded-panel border border-border bg-surface p-4 sm:p-5">
        <AddByCodeForm heading="h2" />
      </section>
    </div>
  );
}

/** İstek sayfalarının "Arkadaş Ekle" kartı. */
export function AddFriendPanel() {
  const t = useTranslations("Social.addByCode");
  return (
    <PanelCard icon="userPlus" title={t("panelTitle")}>
      <AddByCodeForm />
    </PanelCard>
  );
}

/** Sosyal gizlilik ayarları user-service'e aittir; o ekran yayınlanana kadar pasif ve "Yakında" işaretlidir. */
export function PrivacyCard() {
  const t = useTranslations("Social.privacy");
  return (
    <section className="flex items-start gap-4 rounded-panel border border-dashed border-border bg-surface p-4 sm:p-5"
    >
      <Icon name="shield" className="mt-0.5 size-6 shrink-0 text-text-secondary" />
      <div className="min-w-0 flex-1">
        <h2 className="flex flex-wrap items-center gap-2 font-display text-base font-semibold text-text-primary">
          {t("title")}
          <span className="rounded-full bg-surface-elevated px-2 py-0.5 font-sans text-[0.6875rem] font-semibold text-text-secondary">
            {t("soon")}
          </span>
        </h2>
        <p className="mt-1 text-sm text-text-secondary">{t("body")}</p>
      </div>
    </section>
  );
}

const blockingRules: ReadonlyArray<{ icon: IconName; title: string; body: string }> = [
  { icon: "userPlus", title: "requestsTitle", body: "requestsBody" },
  { icon: "message", title: "messagesTitle", body: "messagesBody" },
  { icon: "gamepad", title: "lfpTitle", body: "lfpBody" }
];

/** "Engelleme nasıl çalışır?": engelin etkilerini işlem yapmadan önce açıkça anlatır. */
export function BlockingInfoPanel() {
  const t = useTranslations("Social.howBlocking");
  const privacy = useTranslations("Social.privacy");
  return (
    <PanelCard icon="info" title={t("title")}>
      <p className="text-sm leading-relaxed text-text-secondary">{t("intro")}</p>
      <ul className="mt-4 space-y-2.5">
        {blockingRules.map((rule) => (
          <li key={rule.title} className="flex gap-3.5 rounded-panel border border-border bg-surface-elevated/50 p-3.5">
            <Icon name={rule.icon} className="mt-0.5 size-6 shrink-0 text-brand-text" />
            <div>
              <h3 className="text-sm font-semibold text-text-primary">{t(rule.title)}</h3>
              <p className="mt-0.5 text-sm text-text-secondary">{t(rule.body)}</p>
            </div>
          </li>
        ))}
      </ul>
      <div className="mt-4 flex items-start gap-3 border-t border-border pt-4">
        <Icon name="settings" className="mt-0.5 size-5 shrink-0 text-text-secondary" />
        <div>
          <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
            {t("privacyTitle")}
            <span className="rounded-full bg-surface-elevated px-2 py-0.5 text-[0.6875rem] font-semibold text-text-secondary">
              {privacy("soon")}
            </span>
          </p>
          <p className="mt-0.5 text-sm text-text-secondary">{t("privacyBody")}</p>
        </div>
      </div>
    </PanelCard>
  );
}
