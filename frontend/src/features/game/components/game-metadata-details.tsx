"use client";

import type { ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { safeExternalUrl } from "../services/discovery-query";
import { formatMegabytes, termLabel } from "../services/format";
import type { GameDetail, Term } from "../types/game";
import { GameGallery } from "./game-gallery";
import { GameLanguage, GameLanguageList } from "./game-language";
import { Badge } from "./game-ui";

const supportTone = { SUPPORTED: "success", UNSUPPORTED: "neutral", UNKNOWN: "neutral" } as const;

export function GameMetadataDetails({ game }: { game: GameDetail }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const metadata = game.metadata;
  const termName = (value: Term) => termLabel(value, locale);
  const support = (value: string) => ["SUPPORTED", "UNSUPPORTED"].includes(value) ? value as "SUPPORTED" | "UNSUPPORTED" : "UNKNOWN";
  const fieldName = (value: string) => {
    const key = value.toLowerCase().replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
    return t.has(`fields.${key}`) ? t(`fields.${key}`) : value === "LOCALIZATION" ? t("languageSupport") : t("gameInformation");
  };
  const coverUrl = safeExternalUrl(game.game.coverUrl);
  // Kapak sayfanın üstünde zaten gösterilir; galeri yalnız ek görselleri (ekran görüntüsü vb.) içerir. API kapağı `media[]` içinde de COVER
  // türüyle döndürür; aynı görselin galeride yinelenmesi engellenir.
  const images = (metadata.media ?? [])
    .filter((value) => value.type !== "TRAILER" && value.type !== "COVER" && safeExternalUrl(value.url) !== coverUrl)
    .map((value) => ({ url: value.url, alt: `${game.game.title} — ${t("media")}` }))
    .filter((image, index, all) => all.findIndex((other) => other.url === image.url) === index);
  const trailers = (metadata.media ?? []).filter((value) => value.type === "TRAILER").map((value) => ({ url: value.url, label: t("openVideo") }));
  const terms = (label: string, values: Term[] | undefined, link?: "genre" | "platform") => <div key={label} className="min-w-0">
    <dt className="text-sm text-text-secondary">{label}</dt>
    <dd className="mt-2 flex flex-wrap gap-2">{values?.length ? values.map((term) => link && term.code
      ? <Link key={term.id} href={{ pathname: "/games", query: { [link]: term.code } }} className="inline-flex min-h-9 items-center rounded-full border border-control-border px-3 text-sm hover:border-primary hover:text-brand-text">{termName(term)}</Link>
      : <Badge key={term.id}>{termName(term)}</Badge>) : <span>{t("unknown")}</span>}</dd>
  </div>;
  const requirementsByPlatform = new Map<string, NonNullable<GameDetail["metadata"]["systemRequirements"]>>();
  for (const value of metadata.systemRequirements ?? []) requirementsByPlatform.set(value.platform, [...(requirementsByPlatform.get(value.platform) ?? []), value]);
  const freshness = ["LOCAL", "UNSUPPORTED", "MISSING", "FRESH", "STALE"].includes(game.freshness?.status) ? game.freshness.status : "MISSING";
  return <section aria-labelledby="game-metadata" className="space-y-10">
    {(images.length > 0 || trailers.length > 0) && <div><h2 className="mb-4 font-display text-2xl font-semibold">{t("media")}</h2><GameGallery images={images} links={trailers} /></div>}
    <div>
      <h2 id="game-metadata" className="font-display text-2xl font-semibold">{t("gameInformation")}</h2>
      <dl className="mt-6 grid gap-6 sm:grid-cols-2">
        {terms(t("fields.genres"), metadata.genres, "genre")}
        {terms(t("fields.platforms"), metadata.platforms, "platform")}
        {terms(t("fields.categories"), metadata.categories)}
        {terms(t("fields.tags"), metadata.tags)}
        {([["publishers", metadata.publishers.join(", ") || t("unknown")], ["languages", metadata.languages.length ? <GameLanguageList key="languages-list" codes={metadata.languages} /> : t("unknown")], ["earlyAccess", metadata.earlyAccess == null ? t("unknown") : t(metadata.earlyAccess ? "yes" : "no")]] as [string, ReactNode][]).map(([key, value]) => <div key={key} className="min-w-0"><dt className="text-sm text-text-secondary">{t(`fields.${key}`)}</dt><dd className="mt-1 break-words">{value}</dd></div>)}
        <div className="min-w-0"><dt className="text-sm text-text-secondary">{t("freshnessLabel")}</dt><dd className="mt-1">{t(`freshness.${freshness}`)}{game.freshness?.fetchedAt && <> · <time dateTime={game.freshness.fetchedAt}>{new Date(game.freshness.fetchedAt).toLocaleString(locale)}</time></>}</dd></div>
      </dl>
    </div>
    {!!metadata.languageSupport?.length && <div>
      <h3 className="font-display text-xl font-semibold">{t("languageSupport")}</h3>
      <div role="region" tabIndex={0} aria-label={t("languageSupport")} className="mt-4 overflow-x-auto rounded-panel border border-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
        <table className="w-full min-w-[28rem] text-left text-sm">
          <thead className="bg-surface-elevated text-text-secondary"><tr><th scope="col" className="px-4 py-3 font-medium">{t("fields.language")}</th><th scope="col" className="px-4 py-3 font-medium">{t("interface")}</th><th scope="col" className="px-4 py-3 font-medium">{t("audio")}</th><th scope="col" className="px-4 py-3 font-medium">{t("subtitles")}</th></tr></thead>
          <tbody className="divide-y divide-border">{metadata.languageSupport.map((value) => <tr key={value.languageCode}>
            <th scope="row" className="px-4 py-3 font-semibold"><GameLanguage code={value.languageCode} /></th>
            {[value.interfaceSupport, value.audioSupport, value.subtitleSupport].map((state, column) => <td key={column} className="px-4 py-3"><Badge tone={supportTone[support(state)]}>{t(`support.${support(state)}`)}</Badge></td>)}
          </tr>)}</tbody>
        </table>
      </div>
      <p className="mt-2 text-xs text-text-secondary">{t("unknownSupportNote")}</p>
    </div>}
    {!!metadata.systemRequirements?.length && <div>
      <h3 className="font-display text-xl font-semibold">{t("requirements")}</h3>
      {[...requirementsByPlatform].map(([platform, levels]) => <div key={platform} className="mt-5">
        <h4 className="font-semibold">{platform}</h4>
        <div className="mt-3 grid gap-4 lg:grid-cols-2">{levels.map((value, index) => <section key={`${value.level}/${index}`} aria-label={`${platform} — ${t(value.level === "RECOMMENDED" ? "recommended" : "minimum")}`} className="rounded-panel border border-border bg-surface p-5">
          <h5 className="font-semibold text-brand-text">{t(value.level === "RECOMMENDED" ? "recommended" : "minimum")}</h5>
          <dl className="mt-4 space-y-2 text-sm">{[["os", value.os], ["cpu", value.cpu], ["gpu", value.gpu], ["ram", formatMegabytes(value.ramMb, locale)], ["storage", formatMegabytes(value.storageMb, locale)]].filter((entry) => entry[1]).map(([key, text]) => <div key={key} className="flex flex-wrap gap-x-2"><dt className="text-text-secondary">{t(`fields.${key}`)}:</dt><dd className="break-words">{text}</dd></div>)}</dl>
          {value.rawText && <p className="mt-4 whitespace-pre-wrap break-words text-sm text-text-secondary">{value.rawText}</p>}
        </section>)}</div>
      </div>)}
    </div>}
    {!!metadata.provenance?.length && <div className="text-sm text-text-secondary"><h3 className="font-semibold text-text-primary">{t("dataSources")}</h3><ul className="mt-3 space-y-2">{metadata.provenance.map((value, index) => <li key={`${value.field}/${index}`}>{fieldName(value.field)} · {value.source} · {value.verifiedAt ? t("verifiedAt", { date: new Date(value.verifiedAt).toLocaleString(locale) }) : value.recordedAt ? t("recordedAt", { date: new Date(value.recordedAt).toLocaleString(locale) }) : t("notVerified")}</li>)}</ul>{metadata.provenance.some((value) => value.source === "IGDB") && <a href="https://www.igdb.com/" target="_blank" rel="noopener noreferrer" className="mt-3 inline-block py-2 text-brand-text underline">{t("igdbAttribution")}</a>}</div>}
  </section>;
}
