"use client";

import { createContext, useContext } from "react";

/**
 * Rıza kapısının içindeki bileşenlerin ortak bağlamı. Yalnız `ConsentGate` "rıza var" dediğinde sağlanır; bu sayede
 * hiçbir öneri isteği rıza doğrulanmadan atılmaz (rıza kapalıyken her istek backend'de geçmişi siler).
 */
export type RecommendationScopeValue = {
  accountId: string;
  /** Herhangi bir uçtan 403 `CONSENT_REQUIRED` geldi: önbellek temizlenir ve kapı yeniden gösterilir. */
  consentRequired: () => void;
};

export const RecommendationScopeContext = createContext<RecommendationScopeValue | null>(null);

export function useRecommendationScope(): RecommendationScopeValue {
  const value = useContext(RecommendationScopeContext);
  if (!value) throw new Error("useRecommendationScope yalnız ConsentGate içinde kullanılabilir.");
  return value;
}
