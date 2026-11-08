"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { useBlocks } from "../hooks/use-social-data";
import { EmptyState, ErrorState, ListSkeleton, LoadMore } from "./list-states";
import { PersonActionDialog, type PersonAction } from "./person-action-dialog";
import { BlockingInfoPanel } from "./side-panels";
import { SocialPage } from "./social-page";
import { usePerson } from "./use-person";

/**
 * Engellenenler sekmesi (SOC-10, SOC-11): liste ve onaylı "Engeli Kaldır".
 * Arama yoktur: user-service engel ilişkisinde ad vermediği için her satır yalnız yedek ad taşır, aranacak bir şey yoktur.
 * Önizleme rozeti de yoktur: bu sayfa durum/oyun/ortak arkadaş göstermez, gösterilen her şey gerçek kayıttır.
 */
export function BlockedView() {
  const t = useTranslations("Social");
  return (
    <SocialPage
      section="blocked"
      icon="shield"
      figures={1}
      title={t("hero.blockedTitle")}
      subtitle={t("hero.blockedSubtitle")}
      preview={false}
      asideLabel={t("howBlocking.title")}
      aside={<BlockingInfoPanel />}
    >
      <BlockedList />
    </SocialPage>
  );
}

function BlockedList() {
  const t = useTranslations("Social");
  const format = useFormatter();
  const blocks = useBlocks();
  const describe = usePerson();
  const [action, setAction] = useState<PersonAction | null>(null);

  return (
    <section aria-label={t("blocked.listLabel")} className="space-y-4">
      {blocks.loading ? (
        <ListSkeleton variant="rows" count={3} />
      ) : blocks.error && blocks.items.length === 0 ? (
        <ErrorState error={blocks.error} onRetry={blocks.retry} />
      ) : blocks.items.length === 0 ? (
        <EmptyState icon="shield" title={t("blocked.emptyTitle")} body={t("blocked.emptyBody")} />
      ) : (
        <ul aria-label={t("blocked.listLabel")} className="space-y-3">
          {blocks.items.map((view) => {
            const person = describe(view.profile, "blocked");
            return (
              <li
                key={view.block.blockedUserId}
                className="flex flex-wrap items-center gap-4 rounded-panel border border-border bg-surface p-4 transition-colors duration-150 hover:border-primary/35 motion-reduce:transition-none"
              >
                <Avatar seed={view.profile.avatarSeed} imageUrl={person.avatarUrl} size="lg" ring />
                <div className="min-w-0 flex-1 basis-40">
                  <p className="truncate font-display text-xl font-semibold text-text-primary">{person.name}</p>
                  {person.handle ? <p className="truncate text-sm text-text-secondary">{person.handle}</p> : null}
                  <p className="mt-1 flex items-center gap-1.5 text-sm text-text-secondary">
                    <Icon name="ban" className="size-4 text-brand-text" />
                    <time dateTime={view.block.createdAt}>
                      {t("blocked.blockedOn", {
                        date: format.dateTime(new Date(view.block.createdAt), { dateStyle: "long" })
                      })}
                    </time>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setAction({ kind: "unblock", person: view })}
                  aria-label={t("blocked.unblockAria", { name: person.name })}
                  className={buttonClass("secondary", "md", "w-full sm:w-auto sm:px-6")}
                >
                  {t("blocked.unblock")}
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {blocks.hasMore ? (
        <LoadMore
          label={t("blocked.loadMore")}
          loading={blocks.loadingMore}
          failed={Boolean(blocks.loadMoreError)}
          onLoad={blocks.loadMore}
        />
      ) : null}

      <PersonActionDialog action={action} onClose={() => setAction(null)} />
    </section>
  );
}
