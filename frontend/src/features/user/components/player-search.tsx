"use client";

import { useCallback, useId, useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useRemote } from "../hooks/use-remote";
import {
  MAX_SEARCH_LENGTH,
  MIN_SEARCH_LENGTH,
  normalizeSearch,
  searchPlayers
} from "../services/players-api";
import type { PlayerCard } from "../types/activity.types";
import { ProfileAvatar } from "./profile-avatar";
import { RemoteBody } from "./remote-section";

const inputClass =
  "min-h-11 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary";

/**
 * Oyuncu arama (USER-11): kullanıcı adı ya da görünen ad önekiyle en çok 20 sonuç. Arama metni adreste (`?q=`) tutulur;
 * böylece paylaşılabilir, geri/ileri çalışır. Misafir arama yapamaz (sunucu 401 verir), oturumsuz ziyaretçi girişe yönlenir.
 * Sonuç sayfalaması yoktur; çok sonuç varsa metni daraltmak gerekir.
 */
export function PlayerSearch() {
  const session = useRequireSession();
  const t = useTranslations("Players.search");
  const router = useRouter();
  const params = useSearchParams();
  const urlQuery = params.get("q") ?? "";
  const active = normalizeSearch(urlQuery);
  const [text, setText] = useState(urlQuery);
  const [problem, setProblem] = useState(false);
  const inputId = useId();
  const hintId = useId();

  const load = useCallback((signal: AbortSignal) => searchPlayers(active ?? "", signal), [active]);
  const remote = useRemote(session.status === "authenticated" && active ? active : null, load);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const value = normalizeSearch(text);
    if (value === null) {
      setProblem(true);
      return;
    }
    setProblem(false);
    router.replace({ pathname: "/players", query: { q: value } });
  }

  if (session.status !== "authenticated") {
    return (
      <PageContainer>
        <p role="status" className="text-text-secondary">
          {t("loading")}
        </p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="font-display text-2xl font-semibold sm:text-3xl">{t("title")}</h1>
          <p className="mt-1 max-w-2xl text-text-secondary">{t("description")}</p>
        </div>

        <form onSubmit={onSubmit} role="search" noValidate className="flex max-w-2xl flex-col gap-2">
          <label htmlFor={inputId} className="text-sm font-medium">
            {t("label")}
          </label>
          <div className="flex flex-wrap gap-3">
            <input
              id={inputId}
              type="search"
              value={text}
              maxLength={MAX_SEARCH_LENGTH}
              autoComplete="off"
              aria-describedby={hintId}
              aria-invalid={problem}
              onChange={(event) => setText(event.target.value)}
              className={`${inputClass} min-w-0 flex-1`}
            />
            <button
              type="submit"
              className="inline-flex min-h-11 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary"
            >
              {t("submit")}
            </button>
          </div>
          <p id={hintId} role={problem ? "alert" : undefined} className={problem ? "text-sm text-brand-text" : "text-xs text-text-secondary"}>
            {problem ? t("tooShort", { min: MIN_SEARCH_LENGTH, max: MAX_SEARCH_LENGTH }) : t("hint", { min: MIN_SEARCH_LENGTH })}
          </p>
        </form>

        {active === null ? (
          urlQuery !== "" ? (
            <p role="status" className="text-sm text-text-secondary">
              {t("tooShort", { min: MIN_SEARCH_LENGTH, max: MAX_SEARCH_LENGTH })}
            </p>
          ) : null
        ) : (
          <section aria-label={t("results")}>
            <RemoteBody remote={remote}>{(cards) => <Results cards={cards} />}</RemoteBody>
          </section>
        )}
      </div>
    </PageContainer>
  );
}

function Results({ cards }: { cards: PlayerCard[] }) {
  const t = useTranslations("Players.search");
  if (cards.length === 0) {
    return <p className="text-sm text-text-secondary">{t("empty")}</p>;
  }
  return (
    <>
      <p aria-live="polite" className="mb-4 text-sm text-text-secondary">
        {t("count", { count: cards.length })}
      </p>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {cards.map((card) => {
          const name = card.displayName ?? card.username ?? t("unnamed");
          return (
            <li key={card.userId}>
              <Link
                href={{ pathname: "/players/[userId]", params: { userId: card.userId } }}
                aria-label={t("open", { name })}
                className="flex items-center gap-3 rounded-control border border-border bg-surface p-3 transition-colors duration-150 hover:border-control-border"
              >
                <ProfileAvatar name={name} src={card.avatar} className="size-11 shrink-0" initialClassName="text-base" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-text-primary">{name}</span>
                  {card.username && card.displayName ? (
                    <span className="block truncate text-xs text-text-secondary">@{card.username}</span>
                  ) : null}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );
}
