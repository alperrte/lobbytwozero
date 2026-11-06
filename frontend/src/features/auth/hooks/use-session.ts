"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { authApi, type SessionApi } from "../services/auth-api";
import type { AuthMe } from "../types/auth.types";

export type SessionState =
  | { status: "loading"; me: null }
  | { status: "guest"; me: null }
  | { status: "authenticated"; me: AuthMe };

export type SessionValue = SessionState & {
  /** Oturumu kapatır ve bellekteki kullanıcı verisini temizler. Sunucu hatasında da yerel durum misafire döner. */
  logout: () => Promise<void>;
};

export const SessionContext = createContext<SessionValue | null>(null);

/** Oturum durumu: `loading` | `guest` | `authenticated`. `SessionProvider` dışında çağrılırsa hata verir. */
export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (value === null) {
    throw new Error("useSession yalnız SessionProvider içinde kullanılabilir.");
  }
  return value;
}

/**
 * Oturumu açılışta bir kez `GET /auth/me` ile yükler (HTTP katmanı 401'de önce yenilemeyi dener; o da 401 ise misafir)
 * ve çıkış eylemini sağlar. Token hiçbir yerde saklanmaz: bellekte yalnız hesap kimliği, roller ve bitiş zamanı durur.
 * `roles` yalnız arayüz içindir; yetki kararı sunucudadır. Ağ/sunucu hatasında da kimlik varsayılmaz (misafir).
 */
export function useSessionState(api: SessionApi = authApi): SessionValue {
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

  return { ...state, logout };
}
