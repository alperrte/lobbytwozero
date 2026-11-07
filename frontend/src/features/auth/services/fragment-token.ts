/**
 * E-postadaki bağlantılar token'ı URL PARÇASINDA (`#token=…`) taşır; parça sunucuya, günlüklere ve `Referer`'a hiç gitmez.
 * Token okunur okunmaz adres çubuğundan SİLİNİR (geçmişe ve paylaşılan/kopyalanan adrese kalmasın) ve yalnız bellekte
 * tutulur: `localStorage`/`sessionStorage`'a, günlüğe ya da hataya yazılmaz.
 *
 * `undefined` = henüz okunmadı (sunucu görüntüsü ve hidrasyon), `null` = bağlantıda token yok/biçimi geçersiz.
 */
const TOKEN_PATTERN = /^#token=([A-Za-z0-9_-]{20,256})$/;

let captured: string | null | undefined;

export function readFragmentToken(): string | null | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  const match = TOKEN_PATTERN.exec(window.location.hash);
  if (match) {
    captured = match[1];
    // Parçayı adresten sil: yol ve sorgu korunur, geçmiş girdisi yenilenir.
    window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
  } else if (captured === undefined) {
    captured = null;
  }
  return captured;
}

/** Test ve sayfa değişimi için bellekteki token'ı unutur. */
export function forgetFragmentToken(): void {
  captured = undefined;
}

/** `useSyncExternalStore` için; sunucu görüntüsü `undefined` (hidrasyon uyuşmazlığı olmaz). */
export const fragmentTokenStore = {
  subscribe: () => () => undefined,
  getSnapshot: readFragmentToken,
  getServerSnapshot: (): string | null | undefined => undefined
};
