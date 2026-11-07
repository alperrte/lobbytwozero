/**
 * Kayıttan doğrulama ekranına geçerken e-postayı BELLEKTE taşır: URL'ye (adres çubuğu, günlükler, Referer) ve
 * `localStorage`/`sessionStorage`'a yazılmaz. Sayfa yenilenirse kaybolur ve kullanıcı e-postayı yeniden yazar. Parola
 * burada tutulmaz. Yalnız tarayıcıda yazılır; sunucuda hiç yazılmadığı için kullanıcılar arasında sızıntı olmaz.
 */
let pendingEmail: string | null = null;
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((listener) => listener());
}

export function rememberPendingEmail(email: string): void {
  pendingEmail = email;
  notify();
}

export function clearPendingEmail(): void {
  pendingEmail = null;
  notify();
}

/**
 * Steam hesabı için tek kullanımlık doğrulama kanıtı (parola yerine). Aynı kurallar: yalnız bellekte, URL/depoya yazılmaz.
 */
let pendingProof: string | null = null;

export function rememberPendingProof(proof: string): void {
  pendingProof = proof;
  notify();
}

export function clearPendingProof(): void {
  pendingProof = null;
  notify();
}

export const pendingProofStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  getSnapshot: (): string | null => pendingProof,
  getServerSnapshot: (): string | null => null
};

/** `useSyncExternalStore` için: sunucu görüntüsü her zaman `null`'dır (hidrasyon uyuşmazlığı olmaz). */
export const pendingEmailStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  getSnapshot: (): string | null => pendingEmail,
  getServerSnapshot: (): string | null => null
};
