"use client";

import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { ApiHttpError } from "@/shared/lib/api/client";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useLibrary } from "../hooks/use-library";
import { isLibraryId, libraryQuery } from "../schemas/library-query";
import type { LibraryPage, LibraryStatistics } from "../types/library";
import { LibraryBoundary, LibraryShell } from "./library-shell";
import { LibraryLoading, LibraryStatus } from "./library-status";
import { LibraryStats } from "./library-stats";
import { LibraryGameCard } from "./library-game-card";
import { LibraryEmptyState } from "./library-empty-state";
import { LibraryPagination } from "./library-filters";

function Profile({ userId }: { userId: string }) {
  const t = useTranslations("Library");
  const params = useSearchParams();
  const router = useRouter();
  const { page, size } = libraryQuery(new URLSearchParams(params.toString()));
  const result = useLibrary<{ collection: LibraryPage; statistics: LibraryStatistics }>({ kind: "profile", userId, page, size });
  function navigate(next: number) { router.push({ pathname: "/library/users/[userId]", params: { userId }, query: { page: next, size } }, { scroll: false }); }
  return <LibraryShell section="profile">
    {result.loading && <LibraryLoading />}
    <LibraryStatus error={result.error} onRetry={result.reload} profile />
    {result.data && <>
      <LibraryStats data={result.data.statistics} />
      <p className="text-sm text-text-secondary" role="status">{t("total", { count: result.data.collection.totalElements })}</p>
      {result.data.collection.metadataPartial && <p role="status" className="text-sm text-text-secondary">{t("metadataPartial")}</p>}
      {result.data.collection.items.length === 0 && page > 0
        ? <div className="space-y-3"><p>{t("pageEmpty")}</p><button type="button" className={buttonClass("secondary")} onClick={() => navigate(Math.max(0, Math.ceil(result.data!.collection.totalElements / size) - 1))}>{t("returnToResults")}</button></div>
        : result.data.collection.items.length === 0 ? <LibraryEmptyState readOnly /> : <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{result.data.collection.items.map((item) => <LibraryGameCard key={item.entry.id} item={item} readOnly />)}</div>}
      <LibraryPagination page={page} size={size} total={result.data.collection.totalElements} onChange={navigate} />
    </>}
  </LibraryShell>;
}
export function LibraryProfileView({ userId }: { userId: string }) {
  return <LibraryBoundary>{isLibraryId(userId) ? <Profile key={userId} userId={userId} /> : <LibraryShell section="profile"><LibraryStatus error={new ApiHttpError(400)} /></LibraryShell>}</LibraryBoundary>;
}
