"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { authApi, type SessionApi } from "../services/auth-api";
import { hasSessionHint } from "../services/session-hint";
import type { AuthMe } from "../types/auth.types";

export type SessionState =
  | { status: "loading"; me: null }
  | { status: "guest"; me: null }
  | { status: "authenticated"; me: AuthMe };

export type SessionValue = SessionState & {
  /** Oturumu kapatır ve bellekteki kullanıcı verisini temizler. Sunucu hatasında da yerel durum misafire döner. */
  logout: () => Promise<void>;
  /** `GET /auth/me`yi yeniden yükler (ör. yaş doğrulaması sonrası). Hatada mevcut durum korunur. */
  reload: () => Promise<void>;
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
 * ve çıkış eylemini sağlar. Oturum ipucu çerezi yoksa hiç istek atılmaz ve doğrudan misafir sayılır.
 * Token hiçbir yerde saklanmaz: bellekte yalnız hesap kimliği, roller ve bitiş zamanı durur.
 * `roles` yalnız arayüz içindir; yetki kararı sunucudadır. Ağ/sunucu hatasında da kimlik varsayılmaz (misafir).
 */
export function useSessionState(api: SessionApi = authApi): SessionValue {
  const [state, setState] = useState<SessionState>({ status: "loading", me: null });

  useEffect(() => {
    const controller = new AbortController();
    const load: Promise<AuthMe | null> = hasSessionHint()
      ? api.getMe(controller.signal)
      : Promise.resolve(null);
    load
      .then((me) => setState(me ? { status: "authenticated", me } : { status: "guest", me: null }))
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

  async function reload() {
    try {
      const me = await api.getMe();
      setState(me ? { status: "authenticated", me } : { status: "guest", me: null });
    } catch {
      // Geçici hatada kullanıcıyı oturumdan atmayız; mevcut durum kalır.
    }
  }

  return { ...state, logout, reload };
}
