type Listener = () => void;

const listeners = new Set<Listener>();

/**
 * Profil değişti (ad, fotoğraf, kapak): kabuktaki ad/avatar gibi başka yerlerin kendini tazelemesi için haber verir.
 * Yalnız tarayıcı belleğinde, tek sekmede çalışır; sunucuya bir şey göndermez.
 */
export function emitProfileChanged(): void {
  for (const listener of [...listeners]) {
    listener();
  }
}

/** `emitProfileChanged` çağrılınca `listener` çalışır. Dönen işlev aboneliği kaldırır. */
export function onProfileChanged(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
