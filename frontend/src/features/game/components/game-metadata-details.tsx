"use client";

import Image from "next/image";
import { useLocale, useTranslations } from "next-intl";
import { safeExternalUrl } from "../services/discovery-query";
import type { GameDetail, Term } from "../types/game";

export function GameMetadataDetails({ game }: { game: GameDetail }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const metadata = game.metadata;
  const terms = (values: Term[] | undefined) => values?.map((value) => locale === "tr" ? value.nameTr : value.nameEn).join(", ") || t("unknown");
  const support = (value: string) => t(`support.${["SUPPORTED", "UNSUPPORTED"].includes(value) ? value : "UNKNOWN"}`);
  const fieldName = (value: string) => {
    const key = value.toLowerCase().replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
    return t.has(`fields.${key}`) ? t(`fields.${key}`) : value === "LOCALIZATION" ? t("languageSupport") : t("gameInformation");
  };
  const media = [...(safeExternalUrl(game.game.coverUrl) ? [{ type: "COVER", url: game.game.coverUrl!, provider: "", sortOrder: -1 }] : []), ...(metadata.media ?? [])];
  return <section aria-labelledby="game-metadata" className="mt-10 border-t border-border pt-8">
    <h2 id="game-metadata" className="font-display text-2xl font-semibold">{t("gameInformation")}</h2>
    <dl className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {[["genres", terms(metadata.genres)], ["categories", terms(metadata.categories)], ["tags", terms(metadata.tags)], ["publishers", metadata.publishers.join(", ") || t("unknown")], ["languages", metadata.languages.join(", ") || t("unknown")], ["earlyAccess", metadata.earlyAccess == null ? t("unknown") : t(metadata.earlyAccess ? "yes" : "no")]].map(([key, value]) => <div key={key}><dt className="text-sm text-text-secondary">{t(`fields.${key}`)}</dt><dd className="mt-1 break-words">{value}</dd></div>)}
    </dl>
    {!!metadata.languageSupport?.length && <div className="mt-8">
      <h3 className="font-display text-xl font-semibold">{t("languageSupport")}</h3>
      <ul className="mt-3 divide-y divide-border">{metadata.languageSupport.map((value) => <li key={value.languageCode} className="flex flex-wrap gap-x-6 gap-y-2 py-4 text-sm"><strong>{value.languageCode}</strong><span>{t("interface")}: {support(value.interfaceSupport)}</span><span>{t("audio")}: {support(value.audioSupport)}</span><span>{t("subtitles")}: {support(value.subtitleSupport)}</span></li>)}</ul>
    </div>}
    {!!metadata.systemRequirements?.length && <div className="mt-8">
      <h3 className="font-display text-xl font-semibold">{t("requirements")}</h3>
      <div className="mt-4 grid gap-6 lg:grid-cols-2">{metadata.systemRequirements.map((value, index) => <section key={`${value.platform}/${value.level}/${index}`} className="rounded-panel border border-border p-5">
        <h4 className="font-semibold">{value.platform} · {t(value.level === "RECOMMENDED" ? "recommended" : "minimum")}</h4>
        <dl className="mt-4 space-y-2 text-sm">{[["os", value.os], ["cpu", value.cpu], ["gpu", value.gpu], ["ram", value.ramMb == null ? null : `${value.ramMb} MB`], ["storage", value.storageMb == null ? null : `${value.storageMb} MB`]].filter((entry) => entry[1]).map(([key, text]) => <div key={key}><dt className="inline text-text-secondary">{t(`fields.${key}`)}: </dt><dd className="inline">{text}</dd></div>)}</dl>
        {value.rawText && <p className="mt-4 whitespace-pre-wrap break-words text-sm text-text-secondary">{value.rawText}</p>}
      </section>)}</div>
    </div>}
    {!!media.length && <div className="mt-8"><h3 className="font-display text-xl font-semibold">{t("media")}</h3><ul className="mt-4 grid gap-5 sm:grid-cols-2">{media.filter((value) => safeExternalUrl(value.url)).map((value, index) => <li key={`${value.url}/${index}`}>{value.type === "TRAILER" ? <a href={safeExternalUrl(value.url)} target="_blank" rel="noopener noreferrer" className="inline-block py-3 text-brand-text underline">{t("openVideo")}</a> : <Image unoptimized src={safeExternalUrl(value.url)!} alt={`${game.game.title} — ${t("media")}`} width={800} height={450} referrerPolicy="no-referrer" className="h-auto max-h-96 w-full rounded-panel object-contain" />}</li>)}</ul></div>}
    {!!metadata.provenance?.length && <div className="mt-8 text-sm text-text-secondary"><h3 className="font-semibold text-text-primary">{t("dataSources")}</h3><ul className="mt-3 space-y-2">{metadata.provenance.map((value, index) => <li key={`${value.field}/${index}`}>{fieldName(value.field)} · {value.source} · {t("verifiedAt", { date: value.verifiedAt ? new Date(value.verifiedAt).toLocaleString(locale) : t("notVerified") })}</li>)}</ul>{metadata.provenance.some((value) => value.source === "IGDB") && <a href="https://www.igdb.com/" target="_blank" rel="noopener noreferrer" className="mt-3 inline-block text-brand-text underline">{t("igdbAttribution")}</a>}</div>}
  </section>;
}
