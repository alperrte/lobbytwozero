/**
 * Google/Steam dönüşü: Auth sonucu SABİT frontend adresine URL PARÇASIYLA iletir (`/auth/callback#status=…&token=…`);
 * parça sunucuya, günlüğe ve `Referer`'a gitmez. Burada yalnız bilinen anahtarlar ve güvenli değerler kabul edilir
 * (`status`, `error`, `token`, `proof`, `challenge`), değerler `[A-Za-z0-9_-]` ile sınırlıdır. Parça okunur okunmaz
 * adres çubuğundan SİLİNİR; sonuç yalnız bellekte tutulur, hiçbir yere yazılmaz ya da loglanmaz.
 */
export type CallbackStatus =
  | "authenticated"
  | "mfa_required"
  | "mfa_enrollment_required"
  | "registration_required"
  | "email_verification_required"
  | "google_linked"
  | "steam_linked";

const STATUSES: readonly string[] = [
  "authenticated",
  "mfa_required",
  "mfa_enrollment_required",
  "registration_required",
  "email_verification_required",
  "google_linked",
  "steam_linked"
];

export type CallbackResult =
  | { kind: "status"; status: CallbackStatus; token?: string; proof?: string; challenge?: string }
  | { kind: "error"; code: string }
  | { kind: "empty" };

const VALUE_PATTERN = /^[A-Za-z0-9_-]{1,512}$/;
const ERROR_PATTERN = /^[A-Z_]{1,64}$/;

/** `#a=b&c=d` metnini çözer; bilinmeyen anahtar, tekrar eden anahtar ve geçersiz değer sonucu `empty` yapar. */
export function parseCallbackFragment(hash: string): CallbackResult {
  if (!hash.startsWith("#") || hash.length < 2) {
    return { kind: "empty" };
  }
  const params = new Map<string, string>();
  for (const part of hash.slice(1).split("&")) {
    const index = part.indexOf("=");
    if (index <= 0) {
      return { kind: "empty" };
    }
    const key = part.slice(0, index);
    const value = part.slice(index + 1);
    if (params.has(key) || !["status", "error", "token", "proof", "challenge"].includes(key)) {
      return { kind: "empty" };
    }
    params.set(key, value);
  }

  const error = params.get("error");
  if (error !== undefined) {
    return ERROR_PATTERN.test(error) && params.size === 1 ? { kind: "error", code: error } : { kind: "empty" };
  }

  const status = params.get("status");
  if (status === undefined || !STATUSES.includes(status)) {
    return { kind: "empty" };
  }
  const secrets = { token: params.get("token"), proof: params.get("proof"), challenge: params.get("challenge") };
  for (const value of Object.values(secrets)) {
    if (value !== undefined && !VALUE_PATTERN.test(value)) {
      return { kind: "empty" };
    }
  }
  return {
    kind: "status",
    status: status as CallbackStatus,
    ...(secrets.token !== undefined ? { token: secrets.token } : {}),
    ...(secrets.proof !== undefined ? { proof: secrets.proof } : {}),
    ...(secrets.challenge !== undefined ? { challenge: secrets.challenge } : {})
  };
}

let captured: CallbackResult | undefined;

/** Sunucu görüntüsünde `undefined` (henüz okunmadı), istemcide sonuç; parçayı adres çubuğundan siler. */
export function readCallbackResult(): CallbackResult | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  if (window.location.hash.length > 1) {
    captured = parseCallbackFragment(window.location.hash);
    window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
  } else if (captured === undefined) {
    captured = { kind: "empty" };
  }
  return captured;
}

export function forgetCallbackResult(): void {
  captured = undefined;
}

export const callbackResultStore = {
  subscribe: () => () => undefined,
  getSnapshot: readCallbackResult,
  getServerSnapshot: (): CallbackResult | undefined => undefined
};
