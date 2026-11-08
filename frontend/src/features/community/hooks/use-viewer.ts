"use client";

import { useSession } from "@/features/auth";

export type Viewer = {
  status: "loading" | "guest" | "authenticated";
  accountId: string | null;
  /** Yalnız arayüz ipucu: moderatör/yönetici eylemlerini göstermek içindir. Yetki kararı sunucudadır. */
  canModerate: boolean;
};

export function useViewer(): Viewer {
  const session = useSession();
  if (session.status !== "authenticated") {
    return { status: session.status, accountId: null, canModerate: false };
  }
  return {
    status: "authenticated",
    accountId: session.me.accountId,
    canModerate: session.me.roles.some((role) => role === "MODERATOR" || role === "ADMIN")
  };
}
