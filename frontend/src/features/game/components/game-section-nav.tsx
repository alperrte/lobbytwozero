"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameSession } from "../types/game";

export type GameSection = "all" | "picks" | "developer" | "admin" | "moderation";

const itemClass = "inline-flex min-h-11 shrink-0 items-center whitespace-nowrap border-b-2 px-4 text-sm font-semibold transition-colors";
const activeClass = "border-primary text-brand-text";
const idleClass = "border-transparent text-text-secondary hover:border-control-border hover:text-text-primary";

/** Rol → erişilebilen yönetim bölümleri. Yalnız arayüz görünürlüğüdür; yetkiyi her istekte sunucu denetler. */
export function staffSections(roles: readonly string[]): ("developer" | "admin" | "moderation")[] {
  return [roles.includes("GAME_DEVELOPER") ? "developer" : "", roles.includes("ADMIN") ? "admin" : "", roles.some((role) => role === "ADMIN" || role === "MODERATOR") ? "moderation" : ""].filter(Boolean) as ("developer" | "admin" | "moderation")[];
}

function NavView({ current, session }: { current: GameSection | null; session: GameSession | null }) {
  const t = useTranslations("Game");
  const staff = session ? staffSections(session.roles) : [];
  const link = (key: GameSection, label: string, href: Parameters<typeof Link>[0]["href"]) => <li key={key}>
    <Link href={href} aria-current={current === key ? "page" : undefined} className={cn(itemClass, current === key ? activeClass : idleClass)}>{label}</Link>
  </li>;
  return <nav aria-label={t("catalogue")} className="-mx-1 overflow-x-auto border-b border-border px-1 [scrollbar-width:thin]">
    <ul className="flex min-w-max items-stretch gap-1">
      {link("all", t("allGames"), { pathname: "/games" })}
      {link("picks", t("editorsPicks"), { pathname: "/games", query: { tab: "picks" } })}
      {staff.length > 0 && <li aria-hidden="true" className="mx-2 my-2 w-px self-stretch bg-border" />}
      {staff.map((section) => link(section, t(`workspace.${section}`), { pathname: "/games/manage", query: { section } }))}
    </ul>
  </nav>;
}

function NavWithSession({ current }: { current: GameSection | null }) {
  const session = useGameResource<GameSession | null>({ kind: "session" }, { retainWhileRefreshing: true, refreshOnFocus: false });
  return <NavView current={current} session={session.data ?? null} />;
}

/**
 * Game bölümünün tek gezinti çubuğu (katalog, detay ve yönetim sayfalarında ortak). Herkese açık bölümler her zaman görünür;
 * geliştirici/yönetici/moderasyon alanları ilgili rollerde görünür. Ortak sidebar'a dokunmaz.
 * Sayfa oturumu zaten yüklediyse `session` verilir (ikinci istek atılmaz; `null` = misafir); verilmezse gezinti kendi oturumunu okur.
 */
export function GameSectionNav({ current = null, session }: { current?: GameSection | null; session?: GameSession | null }) {
  return session === undefined ? <NavWithSession current={current} /> : <NavView current={current} session={session} />;
}
