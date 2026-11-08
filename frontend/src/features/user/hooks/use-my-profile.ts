"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { getMyProfile, getProfileStats, getSteamLink } from "../services/profile-api";
import type { MyProfile, ProfileStats, SteamLink } from "../types/profile.types";

export type MyProfileState =
  | { status: "loading" }
  | { status: "error"; code: string | undefined }
  | { status: "ready"; profile: MyProfile; steam: SteamLink | null; stats: ProfileStats | null };

export type MyProfileExtras = {
  /** Steam bağlantı durumu da alınsın mı. */
  steam?: boolean;
  /** Doluysa sayı özeti (arkadaş, oyun, inceleme) bu hesap kimliğiyle alınır. */
  statsFor?: string | null;
};

/**
 * Kendi profilini yükler. Steam durumu ve sayı özeti ikincil veridir: alınamazsa profil yine gösterilir (`null` = bilinmiyor).
 * `enabled=false` iken (oturum henüz doğrulanmadıysa) istek atılmaz.
 */
export function useMyProfile(enabled: boolean, extras: MyProfileExtras = {}) {
  const withSteam = extras.steam === true;
  const statsFor = extras.statsFor ?? null;
  const [state, setState] = useState<MyProfileState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const controller = new AbortController();
    Promise.all([
      getMyProfile(controller.signal),
      withSteam ? getSteamLink(controller.signal).catch(() => null) : Promise.resolve(null),
      statsFor ? getProfileStats(statsFor, controller.signal).catch(() => null) : Promise.resolve(null)
    ])
      .then(([profile, steam, stats]) => setState({ status: "ready", profile, steam, stats }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState({ status: "error", code: error instanceof ApiHttpError ? error.code : undefined });
      });
    return () => controller.abort();
  }, [enabled, withSteam, statsFor, attempt]);

  const retry = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((value) => value + 1);
  }, []);
  return { state, retry };
}
