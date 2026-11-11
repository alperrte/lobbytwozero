import { ApiHttpError } from "@/shared/lib/api/client";
import type { ReviewErrorContext } from "../types/review";

export type ReviewErrorInfo = {
  /** `Review.errors.<key>` mesaj anahtarı. */
  key: string;
  /** 429'da `Retry-After` saniyesi; mesajdaki `{seconds}` değeri. */
  retryAfterSeconds?: number;
  /** Oturum yok/sona erdi: arayüz giriş bağlantısı sunar. */
  needsLogin: boolean;
  /** Sürüm çakışması: arayüz "güncel hali yükle" sunar. */
  conflict: boolean;
  /** Sonucu bilinmeyen istek (ağ/zaman aşımı): körlemesine tekrarlanmaz. */
  unknownOutcome: boolean;
};

/**
 * Hata → kullanıcı mesajı anahtarı. Sunucu metni (`detail`) kullanıcıya basılmaz. Aynı HTTP durumu bağlama göre ayrılır:
 * 404 "kayıt yok" (okuma) ile "oyun/inceleme bulunamadı" (yazma), 409 "zaten inceleme var" (oluşturma) ile "sürüm çakışması".
 */
export function describeReviewError(error: unknown, context: ReviewErrorContext = "read"): ReviewErrorInfo {
  if (!(error instanceof ApiHttpError)) {
    return { key: "network", needsLogin: false, conflict: false, unknownOutcome: true };
  }
  const base = { needsLogin: false, conflict: false, unknownOutcome: false };
  if (error.code === "CSRF_TOKEN_INVALID") return { ...base, key: "csrf" };
  switch (error.status) {
    case 400:
      return { ...base, key: context === "create" || context === "update" ? "invalidBody" : context === "report" ? "invalidReport" : "badRequest" };
    case 401:
      return { ...base, key: "401", needsLogin: true };
    case 403:
      if (context === "report") return { ...base, key: "403report" };
      return { ...base, key: context === "like" ? "403like" : context === "update" || context === "delete" ? "403owner" : "403" };
    case 404:
      return { ...base, key: context === "read" ? "404" : context === "update" || context === "delete" || context === "report" ? "404review" : "404game" };
    case 409:
      if (context === "create") return { ...base, key: "409create" };
      if (context === "report") return { ...base, key: "409report" };
      return { ...base, key: "409version", conflict: true };
    case 413:
      return { ...base, key: "413" };
    case 429:
      return { ...base, key: error.retryAfterSeconds ? "429wait" : "429", retryAfterSeconds: error.retryAfterSeconds };
    case 503:
      return { ...base, key: "503" };
    default:
      return { ...base, key: "500" };
  }
}

/** Sunucu kotası (429): arayüz bu süre dolana kadar tekrarlayan eylemi kapatır; otomatik yeniden deneme yapılmaz. */
export function retryAfterOf(error: unknown): number | undefined {
  return error instanceof ApiHttpError && error.status === 429 ? error.retryAfterSeconds : undefined;
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiHttpError && error.status === 404;
}
