"use client";

import { useContext } from "react";
import {
  ShellSessionContext,
  type ShellSession,
  type ShellUser
} from "@/shared/providers/shell-session-context";

export type { ShellSession, ShellUser };

/** Kabuğun oturum durumu (kullanıcı, yükleniyor, çıkış). Bağlam `shared/providers/shell-session-context` içindedir. */
export function useShellSession(): ShellSession {
  return useContext(ShellSessionContext);
}

export function useShellUser(): ShellUser | null {
  return useShellSession().user;
}
