"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useReviewAction } from "../hooks/use-review-action";
import { useReviewResource } from "../hooks/use-review-resource";
import { reviewMutations } from "../services/review-api";
import type { RecommendationResponse } from "../types/review";
import { RecommendationPicker } from "./recommendation-picker";
import { Panel, ReviewAlert, Skeleton, focusRing } from "./review-ui";

/**
 * Kullanıcının kendi olumlu/olumsuz tavsiyesi. Sayısal puandan türetilmez ve puanı değiştirmez; kayıt yokluğu (404) "henüz
 * tavsiye verilmedi" demektir (`false`/olumsuz değildir).
 */
export function RecommendationPanel({ gameId, onChanged }: { gameId: string; onChanged?: () => void }) {
  const t = useTranslations("Review");
  const titleId = useId();
  const own = useReviewResource<RecommendationResponse | null>({ kind: "ownRecommendation", gameId });
  const action = useReviewAction();
  const [draft, setDraft] = useState<boolean | null>(null);
  const [saved, setSaved] = useState<RecommendationResponse>();
  const [done, setDone] = useState(false);

  const current = saved ?? own.data ?? null;
  const selected = draft ?? current?.recommended ?? null;
  const dirty = draft !== null && draft !== current?.recommended;
  const ready = own.data !== undefined || saved !== undefined;

  async function save() {
    if (draft === null) return;
    const result = await action.run((signal) => reviewMutations.recommend(gameId, draft, signal));
    if (result?.ok) {
      setSaved(result.data);
      setDraft(null);
      setDone(true);
      onChanged?.();
    }
  }

  return (
    <Panel title={t("recommendation.title")} titleId={titleId} description={t("recommendation.description")}>
      {!ready && !own.error ? (
        <Skeleton className="h-24" />
      ) : own.error && !ready ? (
        <ReviewAlert error={own.error} onRetry={own.reload} />
      ) : (
        <div className="space-y-4">
          <RecommendationPicker
            value={selected}
            disabled={action.pending}
            label={t("recommendation.label")}
            positiveLabel={t("recommendation.yes")}
            negativeLabel={t("recommendation.no")}
            onChange={(value) => {
              setDraft(value);
              setDone(false);
              action.clearError();
            }}
          />
          <p className="text-sm text-text-secondary">
            {current ? t(current.recommended ? "recommendation.savedYes" : "recommendation.savedNo") : t("recommendation.none")}
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" onClick={save} disabled={!dirty || action.pending} aria-busy={action.pending} className={buttonClass("primary", "md", `min-h-11 ${focusRing}`)}>
              {action.pending ? t("saving") : t("recommendation.save")}
            </button>
            {done ? <p role="status" className="text-sm text-success">{t("recommendation.done")}</p> : null}
          </div>
          <ReviewAlert error={action.error} context="recommendation" />
        </div>
      )}
    </Panel>
  );
}
