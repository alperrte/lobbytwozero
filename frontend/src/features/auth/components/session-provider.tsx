"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { getMyProfileSummary, onProfileChanged, type ProfileSummary, type UserRequest } from "@/features/user";
import {
  ShellSessionContext,
  type ShellSession
} from "@/shared/providers/shell-session-context";
import { SessionContext, useSessionState } from "../hooks/use-session";
import type { SessionApi } from "../services/auth-api";
import { apiRequest } from "@/shared/lib/api/client";

type SessionProviderProps = {
  children: ReactNode;
  /** Test için sahte API adaptörü. */
  api?: SessionApi;
  /** Test için sahte profil isteği. */
  profileRequest?: UserRequest;
};

/** Profil isteği ortak `apiRequest`'ten geçer; access token kısa ömürlüdür, 401'de oturum otomatik yenilenir. */
const defaultProfileRequest: UserRequest = (path, signal) => apiRequest<unknown>(path, { signal });

/**
 * Oturum bağlamlarını kurar: `SessionContext` (auth sayfaları için) ve kabuğun `ShellSessionContext`'i. Yükleme ve
 * çıkış mantığı `useSessionState` hook'tadır; bileşen yalnız bağlar. Kabuğa yalnız genel bir etiket verilir: `/me` ad
 * döndürmez; oturum açılınca ad ve küçük avatar User servisinden (`GET /user/me`) alınır. Profil gelene kadar ya da
 * alınamazsa genel etiket kalır; sahte ad üretilmez ve profil hatası oturumu etkilemez.
 */
export function SessionProvider({ children, api, profileRequest = defaultProfileRequest }: SessionProviderProps) {
  const t = useTranslations("Auth.session");
  const session = useSessionState(api);
  const accountId = session.status === "authenticated" ? session.me.accountId : null;
  const [loaded, setLoaded] = useState<{ accountId: string; summary: ProfileSummary } | null>(null);
  const [profileRevision, setProfileRevision] = useState(0);

  // Profil sayfasında ad/fotoğraf değişince kabuktaki ad ve avatar yeniden alınır.
  useEffect(() => onProfileChanged(() => setProfileRevision((value) => value + 1)), []);

  useEffect(() => {
    if (accountId === null) {
      return;
    }
    const controller = new AbortController();
    getMyProfileSummary(profileRequest, controller.signal)
      .then((summary) => setLoaded({ accountId, summary }))
      .catch(() => undefined);
    return () => controller.abort();
  }, [accountId, profileRequest, profileRevision]);

  const profile = loaded !== null && loaded.accountId === accountId ? loaded.summary : null;

  const shell: ShellSession = {
    user:
      session.status !== "authenticated"
        ? null
        : profile?.name
          ? { displayName: profile.name, ...(profile.avatarUrl ? { avatarUrl: profile.avatarUrl } : {}) }
          : { displayName: t("account"), hideInitials: true, ...(profile?.avatarUrl ? { avatarUrl: profile.avatarUrl } : {}) },
    loading: session.status === "loading",
    logout: session.logout
  };

  return (
    <SessionContext.Provider value={session}>
      <ShellSessionContext.Provider value={shell}>{children}</ShellSessionContext.Provider>
    </SessionContext.Provider>
  );
}
