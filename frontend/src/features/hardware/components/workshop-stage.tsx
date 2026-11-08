"use client";

import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Select } from "@/shared/components/ui/select";
import {
  assemblySteps, buildSignature, isCompleteBuild, pickQuality, playableSteps, qualityPreferences, workshopFallbackPath, workshopModelPath,
  type AssemblyStep, type QualityPreference, type SceneQuality, type WorkshopBuild, type WorkshopSlot
} from "../schemas/workshop";
import type { WorkshopScene } from "./workshop-scene";
import "./workshop.css";

type StageMode = "poster" | "loading" | "3d" | "image";
type Assembly = "idle" | "playing" | "done";
type Capability = { quality: SceneQuality; interactive: boolean } | undefined;

function probeWebgl(attributes?: WebGLContextAttributes): boolean {
  const canvas = document.createElement("canvas");
  const context = (canvas.getContext("webgl2", attributes) ?? canvas.getContext("webgl", attributes)) as WebGLRenderingContext | null;
  context?.getExtension("WEBGL_lose_context")?.loseContext();
  return Boolean(context);
}

/**
 * WebGL yoksa, veri tasarrufu açıksa veya cihaz belleği çok düşükse sabit görsele düşülür. Aksi hâlde otomatik kalite:
 * yazılım render (failIfMajorPerformanceCaveat) veya ≤4 GB / ≤4 çekirdek Low, dokunmatik ya da dar ekran Balanced, diğerleri High.
 */
function detectCapability(): Capability {
  const navigatorInfo = navigator as Navigator & { connection?: { saveData?: boolean }; deviceMemory?: number };
  if (navigatorInfo.connection?.saveData || (navigatorInfo.deviceMemory !== undefined && navigatorInfo.deviceMemory <= 2)) return undefined;
  let majorPerformanceCaveat = false;
  try {
    if (!probeWebgl({ failIfMajorPerformanceCaveat: true })) {
      if (!probeWebgl()) return undefined;
      majorPerformanceCaveat = true;
    }
  } catch {
    return undefined;
  }
  const coarse = window.matchMedia("(pointer: coarse)").matches;
  const narrow = window.matchMedia("(max-width: 767px)").matches;
  const quality = pickQuality({ majorPerformanceCaveat, deviceMemory: navigatorInfo.deviceMemory, cores: navigator.hardwareConcurrency || undefined, coarse, narrow });
  return { quality, interactive: !coarse };
}

const qualityKey = "ltz-workshop-quality";
function readQualityPreference(): QualityPreference {
  try {
    const value = window.localStorage.getItem(qualityKey);
    return qualityPreferences.includes(value as QualityPreference) ? value as QualityPreference : "auto";
  } catch { return "auto"; }
}
function writeQualityPreference(value: QualityPreference) {
  try { window.localStorage.setItem(qualityKey, value); } catch { /* depolama kapalıysa seçim yalnız bu sayfada geçerlidir */ }
}

const playedKey = (profileKey: string) => `ltz-workshop-played:${profileKey}`;
function readPlayed(profileKey: string) {
  try { return window.sessionStorage.getItem(playedKey(profileKey)) === "1"; } catch { return true; }
}
function writePlayed(profileKey: string) {
  try { window.sessionStorage.setItem(playedKey(profileKey), "1"); } catch { /* depolama kapalıysa yalnız otomatik oynatma tekrarlanır */ }
}

export type WorkshopStageProps = {
  build: WorkshopBuild;
  /** Her artışta montaj baştan oynatılır (ör. kayıttan sonra veya "Montajı yeniden oynat"). */
  replayToken: number;
  /** Oturum başına bir kez otomatik oynatma anahtarı. */
  profileKey: string;
  missing: WorkshopSlot[];
  onPickParts: () => void;
};

/**
 * LTZ Atölyesi sahnesi. Sunucu çıktısı ve ilk boyama Blender önizleme görselidir; istemcide yetenek denetimi geçerse
 * three.js modülü dinamik yüklenir ve görselin üzerine 3D sahne açılır. Sahne yüklenemezse görsel kalır.
 */
export function WorkshopStage({ build, replayToken, profileKey, missing, onPickParts }: WorkshopStageProps) {
  const t = useTranslations("Hardware");
  const hostRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<WorkshopScene | undefined>(undefined);
  const buildRef = useRef(build);
  const profileKeyRef = useRef(profileKey);
  const playedToken = useRef(replayToken);
  const appliedSignature = useRef("");
  const [mode, setMode] = useState<StageMode>("poster");
  const [preferImage, setPreferImage] = useState(false);
  const [assembly, setAssembly] = useState<Assembly>("idle");
  const [step, setStep] = useState<AssemblyStep>();
  const [spinning, setSpinning] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [interactive, setInteractive] = useState(false);
  const [preference, setPreference] = useState<QualityPreference>("auto");
  const [autoQuality, setAutoQuality] = useState<SceneQuality>("high");
  const [quality, setQuality] = useState<SceneQuality>();
  const complete = isCompleteBuild(build);
  const signature = buildSignature(build);
  const steps = playableSteps(build);

  useEffect(() => { buildRef.current = build; profileKeyRef.current = profileKey; });

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let cancelled = false;
    let scene: WorkshopScene | undefined;
    void (async () => {
      try {
        const capability = detectCapability();
        const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        setReducedMotion(reduced);
        if (!capability) { setMode("image"); return; }
        setMode("loading");
        setInteractive(capability.interactive);
        const chosen = readQualityPreference();
        setPreference(chosen);
        setAutoQuality(capability.quality);
        const { WorkshopScene } = await import("./workshop-scene");
        if (cancelled) return;
        scene = new WorkshopScene({
          host, url: workshopModelPath, quality: chosen === "auto" ? capability.quality : chosen, adaptive: chosen === "auto",
          reducedMotion: reduced, interactive: capability.interactive, onQuality: setQuality,
          onStep: (next) => {
            if (next === "done") { setAssembly("done"); setStep(undefined); }
            else { setAssembly("playing"); setStep(next); }
          },
          onMotion: setSpinning
        });
        scene.setBuild(buildRef.current);
        appliedSignature.current = buildSignature(buildRef.current);
        await scene.load();
        if (cancelled) return;
        sceneRef.current = scene;
        setMode("3d");
        const current = buildRef.current;
        if (!isCompleteBuild(current)) return;
        if (!reduced && !readPlayed(profileKeyRef.current)) {
          writePlayed(profileKeyRef.current);
          scene.play();
        }
      } catch {
        scene?.dispose();
        scene = undefined;
        if (!cancelled) setMode("image");
      }
    })();
    return () => { cancelled = true; sceneRef.current = undefined; scene?.dispose(); };
  }, []);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene || appliedSignature.current === signature) return;
    appliedSignature.current = signature;
    scene.setBuild(buildRef.current);
    scene.showFinal();
  }, [signature, mode]);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene || replayToken <= playedToken.current) return;
    playedToken.current = replayToken;
    if (isCompleteBuild(buildRef.current)) { writePlayed(profileKeyRef.current); scene.play(); }
  }, [replayToken, mode]);

  function chooseQuality(value: string) {
    const next = value as QualityPreference;
    setPreference(next);
    writeQualityPreference(next);
    sceneRef.current?.setQuality(next === "auto" ? autoQuality : next, next === "auto");
  }

  const showImage = mode === "image" || mode === "poster" || mode === "loading" || preferImage;
  const canPlay = mode === "3d" && !preferImage && complete;
  const status = !complete ? t("workshop.stage.incomplete", { count: missing.length })
    : mode === "loading" ? t("workshop.stage.loading")
    : assembly === "playing" && step ? t("workshop.stage.playing", { step: t(`workshop.steps.${step}`) })
    : reducedMotion && mode === "3d" ? t("workshop.stage.reduced")
    : assembly === "done" ? t("workshop.stage.done") : t("workshop.stage.ready");
  const caseColor = t(`workshop.colors.${build.caseColor}`);

  function play() {
    if (!sceneRef.current || !complete) return;
    playedToken.current = Math.max(playedToken.current, replayToken);
    writePlayed(profileKey);
    sceneRef.current.play();
  }

  return <section aria-labelledby="workshop-stage-title" className="min-w-0 space-y-3">
    <h2 id="workshop-stage-title" className="sr-only">{t("workshop.stage.title")}</h2>
    <div className="workshopStage" data-testid="workshop-stage" data-stage-mode={preferImage && mode === "3d" ? "image" : mode} data-quality={mode === "3d" ? quality : undefined}
      data-assembly={complete ? assembly : "incomplete"} data-case-color={build.caseColor} data-ram-sticks={build.ramSticks}
      data-cpu-vendor={build.cpuVendor ?? ""} data-gpu-vendor={build.gpuVendor ?? ""}>
      <Image src={workshopFallbackPath(build.caseColor)} alt={complete || mode !== "image" ? t("workshop.stage.imageAlt", { color: caseColor }) : ""}
        width={1200} height={900} priority sizes="(min-width: 1024px) 60vw, 100vw"
        className={`workshopStage__poster ${showImage ? "" : "workshopStage__poster--hidden"} ${!complete && mode === "image" ? "workshopStage__poster--dim" : ""}`} />
      <div ref={hostRef} className={`workshopStage__canvas ${mode === "3d" && !preferImage ? "" : "workshopStage__canvas--hidden"}`} />
      <div className="workshopStage__chips" aria-hidden="true">
        <span className="workshopStage__chip">{t("workshop.vendor", { category: t("categories.CPU"), vendor: build.cpuVendor ?? (build.parts.CPU ? t("common.unknown") : "—") })}</span>
        <span className="workshopStage__chip">{t("workshop.vendor", { category: t("categories.GPU"), vendor: build.gpuVendor ?? (build.parts.GPU ? t("common.unknown") : "—") })}</span>
        <span className="workshopStage__chip">{t("workshop.vendor", { category: t("workshop.caseColor"), vendor: caseColor })}</span>
      </div>
      {!complete ? <div className="workshopStage__overlay" data-testid="workshop-missing">
        <div className="workshopStage__card">
          <p className="font-display text-lg font-semibold">{t("workshop.missing.title")}</p>
          <p className="mt-1 text-sm leading-6 text-on-stage-muted">{t("workshop.missing.description")}</p>
          <ul className="mt-3 flex flex-wrap gap-2">{missing.map((slot) => <li key={slot} className="workshopStage__chip workshopStage__chip--missing">{t(`categories.${slot}`)}</li>)}</ul>
          <button type="button" className="workshopStage__button workshopStage__button--primary mt-4" onClick={onPickParts}>{t("workshop.missing.pick")}</button>
        </div>
      </div> : null}
      {mode === "3d" && interactive && !preferImage && complete && assembly !== "playing" ? <p className="workshopStage__hint" aria-hidden="true">{t("workshop.stage.dragHint")}</p> : null}
    </div>

    <div className="flex flex-wrap items-center gap-2">
      {assembly === "playing" ? <button type="button" className="workshopStage__control" onClick={() => sceneRef.current?.showFinal()}>
        <Icon name="arrowRight" className="size-4" />{t("workshop.stage.skip")}
      </button> : <button type="button" className="workshopStage__control workshopStage__control--primary" disabled={!canPlay} onClick={play}>
        <Icon name="sparkles" className="size-4" />{t(assembly === "done" ? "workshop.stage.replay" : "workshop.stage.play")}
      </button>}
      {canPlay && !reducedMotion && assembly !== "playing" ? <button type="button" className="workshopStage__control" aria-pressed={spinning} onClick={() => sceneRef.current?.setMotion(!spinning)}>
        <Icon name="settings" className="size-4" />{t(spinning ? "workshop.stage.spinOff" : "workshop.stage.spinOn")}
      </button> : null}
      {mode === "3d" ? <button type="button" className="workshopStage__control" aria-pressed={preferImage} onClick={() => setPreferImage((value) => !value)}>
        <Icon name="desktop" className="size-4" />{t(preferImage ? "workshop.stage.view3d" : "workshop.stage.viewImage")}
      </button> : null}
      {mode === "3d" && !preferImage ? <Select size="sm" variant="oval" className="w-44" aria-label={t("workshop.stage.quality")} value={preference} onChange={chooseQuality}
        options={qualityPreferences.map((value) => ({ value, label: value === "auto" && quality ? t("workshop.stage.qualityAuto", { level: t(`workshop.stage.qualityLevels.${quality}`) }) : t(`workshop.stage.qualityLevels.${value}`) }))} /> : null}
      <p role="status" aria-live="polite" className="ms-auto min-h-11 content-center text-sm text-text-secondary" data-testid="workshop-status">{status}</p>
    </div>
    {mode === "image" ? <p className="text-xs leading-5 text-text-secondary">{t("workshop.stage.imageNotice")}</p> : null}

    <ol className="workshopSteps" aria-label={t("workshop.stepsTitle")}>
      {assemblySteps.map((item) => {
        const included = steps.includes(item);
        const index = steps.indexOf(item);
        const current = step ? steps.indexOf(step) : -1;
        const state = !included ? "skipped" : assembly === "playing" ? (index < current ? "done" : index === current ? "current" : "pending") : complete && assembly === "done" ? "done" : "pending";
        return <li key={item} className="workshopSteps__item" data-state={state} aria-current={state === "current" ? "step" : undefined}>
          <span className="workshopSteps__dot" aria-hidden="true">{state === "done" ? <Icon name="check" className="size-3" /> : null}</span>
          <span>{t(`workshop.steps.${item}`)}</span>
          <span className="sr-only">{t(`workshop.stepState.${state}`)}</span>
        </li>;
      })}
    </ol>
  </section>;
}
