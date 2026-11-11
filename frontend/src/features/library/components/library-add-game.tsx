"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { GamePicker, type PickedGame } from "@/features/game/components/game-picker";
import { GameCover } from "@/features/game/components/game-cover";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useLibraryMutation } from "../hooks/use-library-mutation";
import type { LibraryMutation } from "../types/library";
import { LibraryBoundary, LibraryShell } from "./library-shell";
import { LibraryStatus } from "./library-status";

function AddGame() {
  const t = useTranslations("Library");
  const router = useRouter();
  const [game, setGame] = useState<PickedGame>();
  const [favorite, setFavorite] = useState(false);
  const [completed, setCompleted] = useState(false);
  const mutation = useLibraryMutation();
  async function submit() {
    if (!game) return;
    const actions: LibraryMutation[] = [];
    if (favorite) actions.push({ kind: "favorite", gameId: game.gameId, enabled: true });
    if (completed) actions.push({ kind: "completed", gameId: game.gameId, enabled: true });
    if (!actions.length) actions.push({ kind: "add", gameId: game.gameId });
    if (await mutation.run(actions)) router.push("/library");
  }
  return <LibraryShell section="add"><div className="max-w-2xl space-y-5">
    <p className="text-sm leading-6 text-text-secondary">{t("collectionHint")}</p>
    <fieldset disabled={mutation.pending} className="space-y-5">
      <legend className="sr-only">{t("chooseGame")}</legend>
      <GamePicker onPick={setGame} label={t("chooseGame")} />
      {game && <div className="flex items-center gap-4 rounded-panel border border-border bg-surface p-3"><GameCover url={game.coverUrl} title={game.title} className="w-28 shrink-0 rounded-control" /><p className="min-w-0 font-display font-semibold [overflow-wrap:anywhere]">{game.title}</p></div>}
      <div className="flex flex-wrap gap-5">
        <label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={favorite} onChange={(event) => setFavorite(event.target.checked)} className="size-4 accent-primary" />{t("markFavorite")}</label>
        <label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={completed} onChange={(event) => setCompleted(event.target.checked)} className="size-4 accent-primary" />{t("markCompleted")}</label>
      </div>
      <p className="text-xs leading-5 text-text-secondary">{t("automaticCollection")}</p>
      <button type="button" disabled={!game || mutation.pending} onClick={submit} className={buttonClass("primary")}>{t(mutation.pending ? "saving" : "addGame")}</button>
    </fieldset>
    <LibraryStatus error={mutation.error} />
    {!!mutation.error && <p className="text-sm leading-6 text-text-secondary">{t("addFailureHint")} <Link href="/library" className="underline underline-offset-4">{t("checkCollection")}</Link></p>}
  </div></LibraryShell>;
}
export function LibraryAddGame() { return <LibraryBoundary><AddGame /></LibraryBoundary>; }
