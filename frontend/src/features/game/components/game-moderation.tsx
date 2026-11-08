"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import { reasonField, uuidPattern } from "../schemas/game-forms";
import type { DeveloperContent, ModerationTarget } from "../types/game";
import { GameActionForm } from "./game-action-form";
import { GamePicker, type PickedGame } from "./game-picker";
import { controlClass, GameStatus } from "./game-status";
import { Badge, panelClass } from "./game-ui";

/** Herkese açık (görünür) geliştirici içeriğinden gizleme hedefi seçtirir. Gizli içerik açık listede yoktur; geri alma kimlikle yapılır. */
function TargetShortcuts({ gameId, onPick }: { gameId: string; onPick: (target: ModerationTarget, id: string) => void }) {
  const t = useTranslations("Game");
  const result = useGameResource<DeveloperContent>({ kind: "publicDeveloper", gameId }, { refreshOnFocus: false });
  return <div className="space-y-3">
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && <>
      <p className="text-sm text-text-secondary">{t("moderationVisibleHelp")}</p>
      <ul className="space-y-2">
        {result.data.description && <li className="flex flex-wrap items-center justify-between gap-3 rounded-panel border border-border p-3"><span className="min-w-0 text-sm"><Badge>{t("choices.DESCRIPTION")}</Badge> <span className="[overflow-wrap:anywhere]">{result.data.description.text.slice(0, 80)}</span></span><button type="button" className={controlClass} onClick={() => onPick("DESCRIPTION", "")}>{t("pickerSelect")}</button></li>}
        {result.data.media.map((media) => <li key={media.id} className="flex flex-wrap items-center justify-between gap-3 rounded-panel border border-border p-3"><span className="min-w-0 text-sm"><Badge>{t("choices.MEDIA")}</Badge> <span className="[overflow-wrap:anywhere]">{media.altText || media.url}</span></span><button type="button" className={controlClass} onClick={() => onPick("MEDIA", media.id)}>{t("pickerSelect")}</button></li>)}
        {result.data.updateNotes.map((note) => <li key={note.id} className="flex flex-wrap items-center justify-between gap-3 rounded-panel border border-border p-3"><span className="min-w-0 text-sm"><Badge>{t("choices.UPDATE_NOTE")}</Badge> <span className="[overflow-wrap:anywhere]">{note.title}</span></span><button type="button" className={controlClass} onClick={() => onPick("UPDATE_NOTE", note.id)}>{t("pickerSelect")}</button></li>)}
      </ul>
      {!result.data.description && !result.data.media.length && !result.data.updateNotes.length && <p className="text-sm text-text-secondary">{t("emptyDeveloperContent")}</p>}
    </>}
  </div>;
}

function ModerationForm({ game, onClear }: { game: PickedGame; onClear: () => void }) {
  const t = useTranslations("Game");
  const [target, setTarget] = useState<{ target: ModerationTarget; id: string; revision: number }>({ target: "DESCRIPTION", id: "", revision: 0 });
  return <div className="space-y-5">
    <p className="flex flex-wrap items-center gap-3 text-sm">{t("moderationGame")}: <strong className="[overflow-wrap:anywhere]">{game.title}</strong><button type="button" className={controlClass} onClick={onClear}>{t("changeGame")}</button></p>
    <TargetShortcuts gameId={game.gameId} onPick={(next, id) => setTarget({ target: next, id, revision: target.revision + 1 })} />
    <GameActionForm key={target.revision} title={t("moderateContent")} destructive
      fields={[{ name: "target", required: true, options: ["DESCRIPTION", "MEDIA", "UPDATE_NOTE"] }, { name: "targetId", pattern: uuidPattern }, { name: "action", required: true, options: ["HIDE", "RESTORE"] }, reasonField]}
      initial={{ target: target.target, targetId: target.id }}
      build={(values) => ({ operation: "moderate", gameId: game.gameId, body: { target: values.target, ...(values.target === "DESCRIPTION" ? {} : { targetId: values.targetId }), action: values.action, reason: values.reason } })} />
  </div>;
}

export function GameModeration({ initialGameId = "" }: { initialGameId?: string }) {
  const t = useTranslations("Game");
  const [game, setGame] = useState<PickedGame | undefined>(initialGameId ? { gameId: initialGameId, title: initialGameId } : undefined);
  return <section aria-label={t("workspace.moderation")} className="space-y-4">
    <p className="max-w-prose text-sm text-text-secondary">{t("moderationScope")}</p>
    <div className={panelClass}>{game ? <ModerationForm key={game.gameId} game={game} onClear={() => setGame(undefined)} /> : <GamePicker onPick={setGame} />}</div>
  </section>;
}
