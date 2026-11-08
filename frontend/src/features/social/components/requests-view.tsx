"use client";

import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useIncomingRequests, useOutgoingRequests } from "../hooks/use-social-data";
import type { PagedList } from "../hooks/use-paged-social-list";
import { SOCIAL_PREVIEW_DATA } from "../services/social-service";
import type { FriendRequestView } from "../types/social.types";
import { EmptyState, ErrorState, ListSkeleton, LoadMore } from "./list-states";
import { RequestList } from "./request-list";
import { AddFriendPanel, PrivacyCard } from "./side-panels";
import { InviteCodeCard } from "./invite-code-card";
import { SocialPage } from "./social-page";

const SENT_PREVIEW = 3;

function RequestsAside() {
  return (
    <>
      <AddFriendPanel />
      <InviteCodeCard />
      <PrivacyCard />
    </>
  );
}

/** Gelen İstekler sekmesi: tam liste + altında gönderilen isteklerin kısa özeti (taslaktaki gibi). */
export function RequestsView() {
  const t = useTranslations("Social");
  const incoming = useIncomingRequests();
  const outgoing = useOutgoingRequests(SENT_PREVIEW);
  return (
    <SocialPage
      section="incoming"
      icon="users"
      title={t("hero.requestsTitle")}
      subtitle={t("hero.requestsSubtitle")}
      preview={SOCIAL_PREVIEW_DATA}
      asideLabel={t("addByCode.panelTitle")}
      aside={<RequestsAside />}
    >
      <RequestSection
        icon="userPlus"
        title={t("requests.incomingTitle")}
        list={incoming}
        direction="incoming"
        listLabel={t("requests.incomingListLabel")}
        emptyTitle={t("requests.incomingEmptyTitle")}
        emptyBody={t("requests.incomingEmptyBody")}
      />
      {!outgoing.loading && !outgoing.error && outgoing.items.length > 0 ? (
        <section aria-labelledby="social-sent-preview" className="space-y-3 rounded-panel border border-border bg-surface/60 p-3 sm:p-4">
          <div className="flex items-center justify-between gap-3 px-1">
            <h2 id="social-sent-preview" className="flex items-center gap-2.5 font-display text-xl font-semibold">
              <Icon name="send" className="size-5 text-brand-text" />
              {t("requests.sentTitle")}
            </h2>
            {outgoing.hasMore ? (
              <Link
                href="/friends/sent"
                className="inline-flex items-center gap-1 text-sm font-semibold text-brand-text underline-offset-4 hover:underline"
              >
                {t("requests.seeAll")}
                <Icon name="arrowRight" className="size-4 rtl:rotate-180" />
              </Link>
            ) : null}
          </div>
          <RequestList views={outgoing.items} direction="outgoing" label={t("requests.sentListLabel")} />
        </section>
      ) : null}
    </SocialPage>
  );
}

/** Gönderilenler sekmesi: yanıt bekleyen tüm istekler, iptal edilebilir. */
export function SentRequestsView() {
  const t = useTranslations("Social");
  const outgoing = useOutgoingRequests();
  return (
    <SocialPage
      section="sent"
      icon="send"
      title={t("hero.sentTitle")}
      subtitle={t("hero.sentSubtitle")}
      preview={SOCIAL_PREVIEW_DATA}
      asideLabel={t("addByCode.panelTitle")}
      aside={<RequestsAside />}
    >
      <RequestSection
        icon="send"
        title={t("requests.sentTitle")}
        list={outgoing}
        direction="outgoing"
        listLabel={t("requests.sentListLabel")}
        emptyTitle={t("requests.sentEmptyTitle")}
        emptyBody={t("requests.sentEmptyBody")}
      />
    </SocialPage>
  );
}

type RequestSectionProps = {
  icon: IconName;
  title: string;
  list: PagedList<FriendRequestView>;
  direction: "incoming" | "outgoing";
  listLabel: string;
  emptyTitle: string;
  emptyBody: string;
};

function RequestSection({ icon, title, list, direction, listLabel, emptyTitle, emptyBody }: RequestSectionProps) {
  const t = useTranslations("Social");
  const headingId = `social-${direction}-title`;
  return (
    <section aria-labelledby={headingId} className="space-y-3 rounded-panel border border-border bg-surface/60 p-3 sm:p-4">
      <h2 id={headingId} className="flex items-center gap-2.5 px-1 font-display text-xl font-semibold">
        <Icon name={icon} className="size-5 text-brand-text" />
        {title}
        {!list.loading && list.items.length > 0 ? (
          <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-primary px-1.5 font-sans text-xs font-bold text-primary-foreground tabular-nums">
            {list.items.length}
            {list.hasMore ? "+" : ""}
          </span>
        ) : null}
      </h2>
      {list.loading ? (
        <ListSkeleton variant="rows" count={3} />
      ) : list.error && list.items.length === 0 ? (
        <ErrorState error={list.error} onRetry={list.retry} />
      ) : list.items.length === 0 ? (
        <EmptyState icon={icon} title={emptyTitle} body={emptyBody} />
      ) : (
        <>
          <RequestList views={list.items} direction={direction} label={listLabel} />
          {list.hasMore ? (
            <LoadMore
              label={t("requests.loadMore")}
              loading={list.loadingMore}
              failed={Boolean(list.loadMoreError)}
              onLoad={list.loadMore}
            />
          ) : null}
        </>
      )}
    </section>
  );
}
