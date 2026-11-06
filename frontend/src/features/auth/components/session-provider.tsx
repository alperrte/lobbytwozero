"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ShellSessionContext, type ShellSession } from "@/shared/hooks/use-shell-user";
import { authApi } from "../services/auth-api";
import type { AuthMe } from "../types/auth.types";

type SessionApi = {
  getMe: (signal?: AbortSignal) => Promise<AuthMe>;
  logout: () => Promise<void>;
};

export type SessionState =
  | { status: "loading"; me: null }
  | { status: "guest"; me: null }
  | { status: "authenticated"; me: AuthMe };

export type SessionValue = SessionState & {
  /** Oturumu kapatır ve bellekteki kullanıcı verisini temizler. Sunucu hatasında da yerel durum misafire döner. */
  logout: () => Promise<void>;
};

const SessionContext = createContext<SessionValue | null>(null);

/** Oturum durumu: `loading` | `guest` | `authenticated`. `SessionProvider` dışında çağrılırsa hata verir. */
export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (value === null) {
    throw new Error("useSession yalnız SessionProvider içinde kullanılabilir.");
  }
  return value;
}

type SessionProviderProps = {
  children: ReactNode;
  /** Test için sahte API adaptörü. */
  api?: SessionApi;
};

/**
 * Oturumu açılışta bir kez `GET /auth/me` ile yükler (HTTP katmanı 401'de önce yenilemeyi dener; o da 401 ise misafir).
 * Token hiçbir yerde saklanmaz: bellekte yalnız hesap kimliği, roller ve bitiş zamanı durur. `roles` yalnız arayüz
 * içindir; yetki kararı sunucudadır. Kabuğa (`ShellSessionContext`) yalnız genel bir etiket verilir: `/me` ad döndürmez,
 * ad ve avatar User servisinden gelince eklenecek; sahte ad üretilmez.
 */
export function SessionProvider({ children, api = authApi }: SessionProviderProps) {
  const t = useTranslations("Auth.session");
  const [state, setState] = useState<SessionState>({ status: "loading", me: null });

  useEffect(() => {
    const controller = new AbortController();
    api
      .getMe(controller.signal)
      .then((me) => setState({ status: "authenticated", me }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
        // 401 = misafir; ağ/sunucu hatasında da kimlik varsayılmaz.
        setState({ status: "guest", me: null });
      });
    return () => controller.abort();
  }, [api]);

  async function logout() {
    try {
      await api.logout();
    } finally {
      setState({ status: "guest", me: null });
    }
  }

  const shell: ShellSession = {
    user: state.status === "authenticated" ? { displayName: t("account"), hideInitials: true } : null,
    loading: state.status === "loading",
    logout
  };

  return (
    <SessionContext.Provider value={{ ...state, logout }}>
      <ShellSessionContext.Provider value={shell}>{children}</ShellSessionContext.Provider>
    </SessionContext.Provider>
  );
}
