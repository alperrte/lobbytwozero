"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useLibrary } from "../hooks/use-library";
import { useLibraryMutation } from "../hooks/use-library-mutation";
import { libraryQuery } from "../schemas/library-query";
import type { LibraryItem, LibraryMutation, LibraryPage, LibraryStatistics, LibraryView } from "../types/library";
import { LibraryBoundary, LibraryShell, libraryPaths } from "./library-shell";
import { LibraryStats } from "./library-stats";
import { LibraryLoading, LibraryStatus } from "./library-status";
import { LibraryFilters, LibraryPagination } from "./library-filters";
import { LibraryGameCard } from "./library-game-card";
import { LibraryEmptyState } from "./library-empty-state";

function Overview({ view }: { view: LibraryView }) {
  const t = useTranslations("Library");
  const params = useSearchParams();
  const router = useRouter();
  const query = libraryQuery(new URLSearchParams(params.toString()), view);
  const collection = useLibrary<LibraryPage>({ kind: "collection", query });
  const statistics = useLibrary<LibraryStatistics>({ kind: "statistics" });
  const mutation = useLibraryMutation();
  const [removing, setRemoving] = useState<LibraryItem>();
  function navigate(changes: Record<string, string>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) { if (value) next.set(key, value); else next.delete(key); }
    router.push({ pathname: libraryPaths[view], query: Object.fromEntries(next) }, { scroll: false });
  }
  function reload() { collection.reload(); statistics.reload(); }
  async function act(action: LibraryMutation) {
    const ok = await mutation.run(action);
    if (ok && action.kind === "remove") setRemoving(undefined);
    // Belirsiz hata da sunucu durumunu yeniden okur; mutasyon otomatik tekrarlanmaz.
    reload();
  }
  const data = collection.data;
  const filtered = query.favorite !== undefined || query.completed !== undefined;
  const reset = () => navigate({ favorite: "", completed: "", page: "" });
  return <LibraryShell section={view}>
    {statistics.data && <LibraryStats data={statistics.data} />}
    <LibraryStatus error={statistics.error} onRetry={statistics.reload} />
    <LibraryFilters query={query} view={view} onChange={navigate} />
    <div className="space-y-3" aria-live="polite" aria-atomic="true">
      {mutation.pending && <p role="status" className="text-sm text-text-secondary">{t("saving")}</p>}
      {mutation.saved && <p role="status" className="text-sm text-success">{t("saved")}</p>}
      <LibraryStatus error={mutation.error} />
    </div>
    {collection.loading && <LibraryLoading />}
    <LibraryStatus error={collection.error} onRetry={reload} />
    {data && <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><p role="status" className="text-sm text-text-secondary">{t("total", { count: data.totalElements })}</p><button type="button" onClick={reload} disabled={mutation.pending} className={buttonClass("ghost", "sm")}>{t("refresh")}</button></div>
      {data.metadataPartial && <p role="status" className="rounded-panel bg-surface-elevated p-3 text-sm leading-6 text-text-secondary">{t("metadataPartial")}</p>}
      {data.items.length === 0 && query.page > 0
        ? <div className="space-y-3 py-8"><p>{t("pageEmpty")}</p><button type="button" className={buttonClass("secondary")} onClick={() => navigate({ page: String(Math.max(0, Math.ceil(data.totalElements / data.size) - 1)) })}>{t("returnToResults")}</button></div>
        : data.items.length === 0 ? <LibraryEmptyState filtered={filtered} onReset={reset} />
          : <div className="grid items-stretch gap-4 sm:grid-cols-2 xl:grid-cols-3">{data.items.map((item) => <LibraryGameCard key={item.entry.id} item={item} pending={mutation.pending} onAction={act} onRemove={() => setRemoving(item)} />)}</div>}
      <LibraryPagination page={query.page} size={data.size} total={data.totalElements} onChange={(page) => navigate({ page: String(page) })} />
    </div>}
    {removing && <ConfirmDialog tone="danger" title={t("removeTitle")} description={t("removeHint", { title: removing.game.title || t("unknownTitle") })} confirmLabel={t("remove")} cancelLabel={t("cancel")} busy={mutation.pending} onCancel={() => setRemoving(undefined)} onConfirm={() => act({ kind: "remove", gameId: removing.entry.gameId })}><LibraryStatus error={mutation.error} /></ConfirmDialog>}
  </LibraryShell>;
}
export function LibraryOverview({ view = "all" }: { view?: LibraryView }) {
  return <LibraryBoundary><Overview key={view} view={view} /></LibraryBoundary>;
}
