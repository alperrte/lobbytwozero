"use client";

import { useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import { useOverflow } from "../hooks/use-overflow";

/**
 * Kart metni: uzun metin temiz bir satır sınırıyla (varsayılan 6 satır) kesilir, hemen altında açıkça görünen "Devamını gör"
 * düğmesi çıkar; tıklanınca metin tamamen açılır ("Daha az göster" geri toplar). Soluklaştırma/gradient yoktur. Düğme yalnız
 * metin gerçekten kesildiğinde görünür. Uzun kelimeler ve kesintisiz karakterler satır sonunda bölünür (kartın dışına taşmaz).
 */
export function ExpandableText({ text, lines = 6, className }: { text: string; lines?: 4 | 6 | 8; className?: string }) {
  const t = useTranslations("Community.post");
  const id = useId();
  const ref = useRef<HTMLParagraphElement>(null);
  const [expanded, setExpanded] = useState(false);
  const clamped = useOverflow(ref);
  const clampClass = lines === 4 ? "line-clamp-4" : lines === 8 ? "line-clamp-8" : "line-clamp-6";

  return (
    <div className="min-w-0">
      <p
        ref={ref}
        id={id}
        className={cn("whitespace-pre-wrap break-words [overflow-wrap:anywhere] text-[0.9375rem] leading-7 text-text-primary", !expanded && clampClass, className)}
      >
        {text}
      </p>
      {clamped || expanded ? (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={id}
          onClick={() => setExpanded((value) => !value)}
          className="mt-1 inline-flex min-h-11 items-center text-sm font-semibold text-brand-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          {expanded ? t("readLess") : t("readMore")}
        </button>
      ) : null}
    </div>
  );
}
