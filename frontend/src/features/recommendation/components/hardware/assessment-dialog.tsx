"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { Select } from "@/shared/components/ui/select";
import { useAssessment } from "../../hooks/use-hardware-recommendation";
import { RESOLUTION_OPTIONS, isGraphicsPreset, isResolution } from "../../schemas/filters";
import { graphicsPresets, type AssessmentRequest } from "../../types/recommendation";
import { RecommendationError } from "../recommendation-ui";
import { GenerateLabel } from "../games/games-screen";
import { AssessmentResult } from "./assessment-result";

export type AssessmentTarget = { hardwareProfileId: string; gameId: string; platform: string; gameTitle: string };

/**
 * Tek oyun için ayrıntılı donanım değerlendirmesi. Çözünürlük ve grafik ayarı isteğe bağlıdır; verilmezse FPS tahmini
 * yapılmaz (backend bağlamsız FPS vermez). İstek yalnız "Değerlendir" ile gönderilir (generation kotası).
 */
export function AssessmentDialog({ target, onClose }: { target: AssessmentTarget; onClose: () => void }) {
  const t = useTranslations("Recommendation.assessment");
  const common = useTranslations("Recommendation.common");
  const id = useId();
  const assessment = useAssessment();
  const [resolution, setResolution] = useState("");
  const [preset, setPreset] = useState("");

  const resolutionOptions = [{ value: "", label: t("notSpecified") }, ...RESOLUTION_OPTIONS.map((value) => ({ value, label: value.replace("x", " × ") }))];
  const presetOptions = [{ value: "", label: t("notSpecified") }, ...graphicsPresets.map((value) => ({ value, label: t(`preset.${value}`) }))];

  function submit() {
    const request: AssessmentRequest = { hardwareProfileId: target.hardwareProfileId, gameId: target.gameId, platform: target.platform };
    if (isResolution(resolution)) request.resolution = resolution;
    if (isGraphicsPreset(preset)) request.graphicsPreset = preset;
    // Tek desteklenen çizim modu; yalnız bağlam (çözünürlük + ayar) verildiğinde anlamlıdır.
    if (request.resolution && request.graphicsPreset) request.renderingMode = "NATIVE_RASTER";
    void assessment.assess(request);
  }

  return <ModalDialog title={t("title", { game: target.gameTitle })} description={t("description")} onClose={onClose} closeLabel={common("close")}>
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="min-w-0">
          <label htmlFor={`${id}-resolution`} className="mb-1.5 block text-sm font-medium">{t("resolution")}</label>
          <Select id={`${id}-resolution`} options={resolutionOptions} value={resolution} onChange={setResolution} disabled={assessment.pending} className="w-full" />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-preset`} className="mb-1.5 block text-sm font-medium">{t("graphicsPreset")}</label>
          <Select id={`${id}-preset`} options={presetOptions} value={preset} onChange={setPreset} disabled={assessment.pending} className="w-full" />
        </div>
      </div>
      <p className="text-xs leading-5 text-text-secondary">{t("contextNote")}</p>
      <button type="button" className={buttonClass("primary", "md", "w-full sm:w-auto")} disabled={assessment.pending || assessment.cooldown > 0} aria-busy={assessment.pending} onClick={submit}>
        <GenerateLabel pending={assessment.pending} cooldown={assessment.cooldown} idle={assessment.result ? t("reassess") : t("submit")} />
      </button>
      {assessment.error ? <RecommendationError error={assessment.error} compact /> : null}
      {assessment.result ? <div aria-live="polite"><AssessmentResult assessment={assessment.result.assessment} /></div> : null}
    </div>
  </ModalDialog>;
}
