"use client";

import Image from "next/image";
import { useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { safeExternalUrl } from "../services/discovery-query";
import { controlClass } from "./game-status";

export type GalleryImage = { url: string; alt: string };
export type GalleryLink = { url: string; label: string };

/**
 * Görsel galerisi: büyük görünüm + küçük resimler. Ok tuşları, ileri/geri düğmeleri ve dokunmatik kaydırma desteklenir.
 * Yalnız güvenli HTTPS adresleri gösterilir; video/demo yalnız harici bağlantıdır (gömülü oynatıcı yok).
 */
export function GameGallery({ images, links = [], label }: { images: GalleryImage[]; links?: GalleryLink[]; label?: string }) {
  const t = useTranslations("Game");
  const safeImages = images.flatMap((image) => { const url = safeExternalUrl(image.url); return url ? [{ ...image, url }] : []; });
  const safeLinks = links.flatMap((link) => { const url = safeExternalUrl(link.url); return url ? [{ ...link, url }] : []; });
  const [selected, setSelected] = useState(0);
  const start = useRef<number | null>(null);
  if (!safeImages.length && !safeLinks.length) return null;
  const index = Math.min(selected, Math.max(0, safeImages.length - 1));
  const go = (delta: number) => setSelected((index + delta + safeImages.length) % safeImages.length);
  const key = (event: KeyboardEvent) => { if (event.key === "ArrowLeft") { event.preventDefault(); go(-1); } if (event.key === "ArrowRight") { event.preventDefault(); go(1); } };
  const up = (event: PointerEvent) => {
    if (start.current !== null && Math.abs(event.clientX - start.current) > 48) go(event.clientX < start.current ? 1 : -1);
    start.current = null;
  };
  const current = safeImages[index];
  return <div className="space-y-4">
    {current && <div role="group" aria-roledescription="carousel" aria-label={label ?? t("gallery")} tabIndex={0} onKeyDown={key} className="space-y-3 rounded-panel focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">
      <div className="relative aspect-video touch-pan-y overflow-hidden rounded-panel bg-surface-elevated" onPointerDown={(event) => { start.current = event.clientX; }} onPointerUp={up} onPointerCancel={() => { start.current = null; }}>
        <Image unoptimized key={current.url} src={current.url} alt={current.alt} fill sizes="(min-width: 1024px) 60vw, 100vw" referrerPolicy="no-referrer" className="object-contain" priority={index === 0} />
      </div>
      {safeImages.length > 1 && <div className="flex items-center justify-between gap-3">
        <button type="button" className={controlClass} onClick={() => go(-1)}>{t("galleryPrevious")}</button>
        <p aria-live="polite" className="text-sm tabular-nums text-text-secondary">{t("galleryPosition", { index: index + 1, count: safeImages.length })}</p>
        <button type="button" className={controlClass} onClick={() => go(1)}>{t("galleryNext")}</button>
      </div>}
    </div>}
    {safeImages.length > 1 && <ul className="flex gap-2 overflow-x-auto pb-2 [scrollbar-width:thin]">
      {safeImages.map((image, position) => <li key={image.url} className="shrink-0">
        <button type="button" aria-label={t("galleryShow", { index: position + 1 })} aria-current={position === index} onClick={() => setSelected(position)} className={cn("relative block h-16 w-28 overflow-hidden rounded-control border-2 bg-surface-elevated", position === index ? "border-primary" : "border-transparent hover:border-control-border")}>
          <Image unoptimized src={image.url} alt="" fill sizes="112px" referrerPolicy="no-referrer" loading="lazy" className="object-cover" />
        </button></li>)}
    </ul>}
    {safeLinks.length > 0 && <ul className="flex flex-wrap gap-3">{safeLinks.map((link) => <li key={link.url}><a href={link.url} target="_blank" rel="noopener noreferrer" className={`${controlClass} inline-flex items-center`}>{link.label}<span className="sr-only"> ({t("opensExternal")})</span></a></li>)}</ul>}
  </div>;
}
