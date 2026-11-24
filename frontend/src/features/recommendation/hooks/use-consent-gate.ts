"use client";

import { useEffect, useRef, useState } from "react";
import { enrichmentApi } from "../services/enrichment-api";
import { recommendationCache } from "../services/recommendation-cache";
import { isAbortError } from "../services/recommendation-errors";
import type { RecommendationConsent } from "../types/recommendation";

export type ConsentGateState =
  | { status: "loading" }
  | { status: "granted"; consent: RecommendationConsent }
  | { status: "required"; consent: RecommendationConsent }
  | { status: "error"; error: unknown };

/**
 * Kişiselleştirilmiş öneri rızası (`GET/PUT /api/v1/user/me/consents`, `recommendation` alanı). Rıza gerçekten
 * sunucuda açık değilse hiçbir öneri isteği atılmaz. "İzin ver" yalnız `recommendation: true` + bilinen `version`
 * gönderir; 409 (eşzamanlı değişiklik) olursa güncel durum yeniden okunur ve kullanıcı tekrar onaylar.
 */
export function useConsentGate(accountId: string | null) {
  const [state, setState] = useState<{ accountId: string | null; value: ConsentGateState }>({ accountId: null, value: { status: "loading" } });
  const [revision, setRevision] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>();
  const lock = useRef(false);

  useEffect(() => {
    if (!accountId) return;
    const controller = new AbortController();
    enrichmentApi.getConsent(controller.signal).then(
      (consent) => setState({ accountId, value: consent.recommendation ? { status: "granted", consent } : { status: "required", consent } }),
      (error: unknown) => {
        if (!isAbortError(error)) setState({ accountId, value: { status: "error", error } });
      }
    );
    return () => controller.abort();
  }, [accountId, revision]);

  const value: ConsentGateState = state.accountId === accountId ? state.value : { status: "loading" };

  async function grant() {
    if (lock.current || value.status !== "required") return;
    lock.current = true;
    setSaving(true);
    setSaveError(undefined);
    try {
      const saved = await enrichmentApi.saveConsent({ recommendation: true, version: value.consent.version });
      recommendationCache.clear();
      setState({ accountId, value: saved.recommendation ? { status: "granted", consent: saved } : { status: "required", consent: saved } });
    } catch (error) {
      setSaveError(error);
      // Kayıp yanıt işlenmiş olabilir veya sürüm eskidi: sunucudaki gerçek durumu yeniden oku.
      setRevision((current) => current + 1);
    } finally {
      lock.current = false;
      setSaving(false);
    }
  }

  /** Bir öneri ucu `CONSENT_REQUIRED` döndü: eski sonuçlar silinir ve rıza sunucudan yeniden okunur. */
  function consentRequired() {
    recommendationCache.clear();
    setState({ accountId, value: { status: "loading" } });
    setRevision((current) => current + 1);
  }

  return { state: value, saving, saveError, grant, consentRequired, reload: () => setRevision((current) => current + 1) };
}
