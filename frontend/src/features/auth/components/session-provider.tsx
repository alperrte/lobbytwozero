"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import {
  ShellSessionContext,
  type ShellSession
} from "@/shared/providers/shell-session-context";
import { SessionContext, useSessionState } from "../hooks/use-session";
import type { SessionApi } from "../services/auth-api";

type SessionProviderProps = {
  children: ReactNode;
  /** Test için sahte API adaptörü. */
  api?: SessionApi;
};

/**
 * Oturum bağlamlarını kurar: `SessionContext` (auth sayfaları için) ve kabuğun `ShellSessionContext`'i. Yükleme ve
 * çıkış mantığı `useSessionState` hook'tadır; bileşen yalnız bağlar. Kabuğa yalnız genel bir etiket verilir: `/me` ad
 * döndürmez, ad ve avatar User servisinden gelince eklenecek; sahte ad üretilmez.
 */
export function SessionProvider({ children, api }: SessionProviderProps) {
  const t = useTranslations("Auth.session");
  const session = useSessionState(api);

  const shell: ShellSession = {
    user: session.status === "authenticated" ? { displayName: t("account"), hideInitials: true } : null,
    loading: session.status === "loading",
    logout: session.logout
  };

  return (
    <SessionContext.Provider value={session}>
      <ShellSessionContext.Provider value={shell}>{children}</ShellSessionContext.Provider>
    </SessionContext.Provider>
  );
}
