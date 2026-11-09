"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { Link } from "@/shared/lib/i18n/navigation";
import { PageContainer } from "@/shared/components/layout/page-container";
import { buttonClass } from "@/shared/components/ui/button-styles";
import type { AppPathname } from "@/shared/lib/i18n/routing";
import { LibraryLoading } from "./library-status";

export type LibrarySection = "all" | "favorites" | "completed" | "add" | "privacy" | "profile";
export const libraryPaths = { all: "/library", favorites: "/library/favorites", completed: "/library/completed", add: "/library/add", privacy: "/library/privacy" } as const satisfies Record<string, AppPathname>;

export function LibraryBoundary({ children }: { children: ReactNode }) {
  const session = useRequireSession();
  if (session.status !== "authenticated") return <LibraryLoading />;
  return <div key={session.me.accountId}>{children}</div>;
}

export function LibraryShell({ section, children }: { section: LibrarySection; children: ReactNode }) {
  const t = useTranslations("Library");
  return <PageContainer className="space-y-6 py-3 sm:space-y-8 sm:py-5">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="max-w-prose space-y-2"><h1 className="font-display text-3xl font-semibold text-balance sm:text-4xl">{t(`titles.${section}`)}</h1><p className="text-sm leading-6 text-text-secondary">{t(`descriptions.${section}`)}</p></div>
      {section !== "profile" && section !== "add" && <Link href="/library/add" className={buttonClass("primary")}>{t("addGame")}</Link>}
    </header>
    <nav aria-label={t("navigation")} className="flex flex-wrap gap-1 border-b border-border pb-3">
      {Object.entries(libraryPaths).map(([key, href]) => <Link key={key} href={href} aria-current={key === section ? "page" : undefined} className={`${buttonClass("ghost")} ${key === section ? "bg-surface-elevated text-brand-text" : ""}`}>{t(`tabs.${key}`)}</Link>)}
    </nav>
    {children}
  </PageContainer>;
}
