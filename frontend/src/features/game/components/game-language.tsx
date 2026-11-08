"use client";

import Image from "next/image";
import { useState } from "react";
import { useLocale } from "next-intl";
import { languageLabel, languageRegion } from "../services/language-options";

/**
 * Dil kodunun bayrağı: bölge kodundan (tr-tr → TR) bayrak görseli. Emoji bayraklar Windows'ta harf olarak göründüğünden
 * görsel kullanılır. Bölge yoksa (ar) veya görsel açılmazsa nötr dil simgesi gösterilir; bayrak uydurulmaz.
 * Süs amaçlıdır (alt=""): anlam yanındaki dil adındadır.
 */
export function LanguageFlag({ code }: { code: string }) {
  const region = languageRegion(code);
  const [failed, setFailed] = useState(false);
  if (!region || failed) return <span aria-hidden="true" className="inline-flex h-4 w-6 shrink-0 items-center justify-center rounded-sm bg-surface-elevated text-text-secondary">🌐</span>;
  const lower = region.toLowerCase();
  return <Image unoptimized src={`https://flagcdn.com/w80/${lower}.png`} alt="" width={24} height={16} loading="lazy" referrerPolicy="no-referrer"
    onError={() => setFailed(true)} className="h-4 w-6 shrink-0 rounded-sm border border-border object-cover" />;
}

/** Bayrak + dil adı. */
export function GameLanguage({ code }: { code: string }) {
  const locale = useLocale();
  return <span className="inline-flex items-center gap-2"><LanguageFlag code={code} /><span>{languageLabel(code, locale)}</span></span>;
}

/** Dil listesi: her dil kendi satır öğesinde bayrak ve adıyla. */
export function GameLanguageList({ codes }: { codes: readonly string[] }) {
  return <ul className="flex flex-wrap gap-x-5 gap-y-2">{codes.map((code) => <li key={code}><GameLanguage code={code} /></li>)}</ul>;
}
