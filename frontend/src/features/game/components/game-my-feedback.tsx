"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameDetail, GameSession } from "../types/game";
import { FeedbackForm, FeedbackList } from "./game-feedback";
import { GamePicker, type PickedGame } from "./game-picker";
import { controlClass, GameStatus } from "./game-status";
import { panelClass } from "./game-ui";

function Composer({ game, onClear, onSaved }: { game: PickedGame; onClear: () => void; onSaved: () => void }) {
  const t = useTranslations("Game");
  const detail = useGameResource<GameDetail>({ kind: "detail", gameId: game.gameId }, { refreshOnFocus: false });
  return <div className="space-y-3">
    <p className="flex flex-wrap items-center gap-3 text-sm">{t("feedbackFor")} <strong className="[overflow-wrap:anywhere]">{game.title}</strong>
      <button type="button" className={controlClass} onClick={onClear}>{t("changeGame")}</button></p>
    <GameStatus {...detail} onRetry={detail.reload} />
    {detail.data && <FeedbackForm gameId={game.gameId} earlyAccess={detail.data.metadata.earlyAccess === true} onSaved={onSaved} />}
  </div>;
}

/** Oyuncunun tüm özel geri bildirimleri: oyun seç, gönder, durumu izle. Review incelemesi değildir. */
export function GameMyFeedback() {
  const t = useTranslations("Game");
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true });
  const [game, setGame] = useState<PickedGame>();
  const [signal, setSignal] = useState(0);
  if (session.error) return <GameStatus {...session} onRetry={session.reload} />;
  if (session.loading && !session.data) return <p role="status" className="text-text-secondary">{t("loading")}</p>;
  if (!session.data) return <p className={panelClass}>{t("feedbackSignIn")}</p>;
  return <div key={session.data.accountId} className="space-y-8">
    <p className="max-w-prose text-sm text-text-secondary">{t("feedbackDistinct")}</p>
    <section aria-label={t("sendFeedback")} className={panelClass}>
      {game ? <Composer game={game} onClear={() => setGame(undefined)} onSaved={() => setSignal(signal + 1)} /> : <GamePicker onPick={setGame} label={t("feedbackPickGame")} />}
    </section>
    <section aria-label={t("myFeedback")}>
      <h2 className="font-display text-2xl font-semibold">{t("myFeedback")}</h2>
      <FeedbackList key={signal} />
    </section>
  </div>;
}
