"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { useReviewAction } from "../hooks/use-review-action";
import { useReviewResource } from "../hooks/use-review-resource";
import { reviewMutations } from "../services/review-api";
import type { RatingResponse } from "../types/review";
import { RatingPicker } from "./rating-picker";
import { Panel, ReviewAlert, Skeleton, focusRing } from "./review-ui";

/**
 * Kullanıcının kendi 1–10 puanı. Puan metinden ve tavsiyeden BAĞIMSIZDIR; kayıt yokluğu (404) "henüz puan verilmedi" demektir
 * (0 puan değildir). Seçim taslaktır; "Kaydet" sunucu yanıtı gelene kadar başarı göstermez.
 */
export function RatingPanel({ gameId, onChanged }: { gameId: string; onChanged?: () => void }) {
  const t = useTranslations("Review");
  const titleId = useId();
  const hintId = useId();
  const own = useReviewResource<RatingResponse | null>({ kind: "ownRating", gameId });
  const action = useReviewAction();
  const [draft, setDraft] = useState<number | null>(null);
  const [saved, setSaved] = useState<RatingResponse>();
  const [done, setDone] = useState(false);

  const current = saved ?? own.data ?? null;
  const selected = draft ?? current?.rating ?? null;
  const dirty = draft !== null && draft !== current?.rating;
  const ready = own.data !== undefined || saved !== undefined;

  async function save() {
    if (draft === null) return;
    const result = await action.run((signal) => reviewMutations.rate(gameId, draft, signal));
    if (result?.ok) {
      setSaved(result.data);
      setDraft(null);
      setDone(true);
      onChanged?.();
    }
  }

  return (
    <Panel title={t("rating.title")} titleId={titleId} description={t("rating.description")}>
      {!ready && !own.error ? (
        <Skeleton className="h-24" />
      ) : own.error && !ready ? (
        <ReviewAlert error={own.error} onRetry={own.reload} />
      ) : (
        <div className="space-y-4">
          <RatingPicker
            value={selected}
            disabled={action.pending}
            label={t("rating.label")}
            describedBy={hintId}
            optionLabel={(value) => t("rating.option", { value })}
            onChange={(value) => {
              setDraft(value);
              setDone(false);
              action.clearError();
            }}
          />
          <p id={hintId} className="text-sm text-text-secondary">
            {current ? t("rating.saved", { value: current.rating }) : t("rating.none")}
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" onClick={save} disabled={!dirty || action.pending} aria-busy={action.pending} className={buttonClass("primary", "md", `min-h-11 ${focusRing}`)}>
              {action.pending ? t("saving") : t("rating.save")}
            </button>
            {done ? <p role="status" className="text-sm text-success">{t("rating.done")}</p> : null}
          </div>
          <ReviewAlert error={action.error} context="rating" />
        </div>
      )}
    </Panel>
  );
}
