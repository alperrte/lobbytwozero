"use client";

import { useTranslations } from "next-intl";
import { apiUrl } from "@/shared/lib/api/client";
import type { SocialProfile } from "../types/social.types";

export type PersonLabel = {
  /** Görünen ad; yoksa kullanıcı adı; o da yoksa yedek ad + kısa kimlik (ekran okuyucu etiketleri benzersiz kalsın). */
  name: string;
  /** `@kullaniciadi`; kullanıcı adı yoksa `null`. */
  handle: string | null;
  /** Gateway üzerinden tam avatar adresi; yoksa `null` (portre çizilir). */
  avatarUrl: string | null;
};

/**
 * Profil kartı eksik olabilir: user-service silinmiş/yasaklı hesapları ve aranızda engel olanları atlar. Engellenenler bu
 * yüzden hiçbir zaman ad almaz ("Engellenen oyuncu #9f3c2a1b").
 */
export function usePerson() {
  const t = useTranslations("Social.person");
  return (profile: SocialProfile, kind: "default" | "blocked" = "default"): PersonLabel => {
    const fallback = `${t(kind === "blocked" ? "blockedPlayer" : "unknownPlayer")} #${profile.userId.slice(-8)}`;
    return {
      name: profile.displayName ?? profile.username ?? fallback,
      handle: profile.username ? `@${profile.username}` : null,
      avatarUrl: profile.avatarPath ? apiUrl(profile.avatarPath as `/${string}`) : null
    };
  };
}
