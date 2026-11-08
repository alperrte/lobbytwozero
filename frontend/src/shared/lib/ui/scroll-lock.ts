let locks = 0;
let original = "";

/**
 * Sayfa kaydırmasını kilitler ve kilidi bırakan fonksiyonu döner. Sayaçlıdır: iç içe modallar (ör. form içinden açılan
 * "Emin misiniz?" penceresi) kilidi ayrı ayrı alır; özgün `overflow` değeri yalnız SON kilit bırakılınca geri yazılır.
 * Her pencerenin kendi "önceki değeri"ni saklayıp geri yazması, içteki pencerenin `hidden`'ı geri yazıp sayfayı donuk
 * bırakmasına yol açardı.
 */
export function lockPageScroll(): () => void {
  if (typeof document === "undefined") return () => undefined;
  const root = document.documentElement;
  if (locks === 0) original = root.style.overflow;
  locks += 1;
  root.style.overflow = "hidden";
  let released = false;
  return () => {
    if (released) return;
    released = true;
    locks = Math.max(0, locks - 1);
    if (locks === 0) root.style.overflow = original;
  };
}
