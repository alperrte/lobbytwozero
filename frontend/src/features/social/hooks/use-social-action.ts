"use client";

import { useState } from "react";
import { socialErrorKey, type SocialErrorKey } from "../services/social-errors";
import { bumpSocialRevision } from "./social-revision";

export type ActionOutcome<T> = { ok: true; value: T } | { ok: false; error: SocialErrorKey };

/**
 * Sosyal işlem çalıştırıcı. Aynı anda bir işlem (`pendingId`) yürür; gönderim sırasında düğmeler kilitlenir (çift gönderim yok).
 * Başarı yalnız sunucu yanıtından sonra bildirilir ve sosyal sürüm artırılır (listeler, rozetler tazelenir). Hata, kullanıcıya
 * gösterilecek mesaj anahtarı olarak döner; iyimser güncelleme yapılmaz.
 */
export function useSocialAction() {
  const [pendingId, setPendingId] = useState<string | null>(null);

  async function run<T>(id: string, action: () => Promise<T>): Promise<ActionOutcome<T>> {
    setPendingId(id);
    try {
      const value = await action();
      bumpSocialRevision();
      return { ok: true, value };
    } catch (error) {
      return { ok: false, error: socialErrorKey(error) };
    } finally {
      setPendingId(null);
    }
  }

  return { pendingId, busy: pendingId !== null, run };
}
