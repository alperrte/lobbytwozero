/**
 * Oturum ipucu çerezi (Auth'ın `ltz_session=1`, HttpOnly değil): "bu tarayıcıda oturum olabilir" der, değer ve yetki
 * taşımaz. Yoksa arayüz `/me` ve `/session/refresh` isteklerini hiç atmaz (misafir ziyaretçiye gereksiz 401 yok).
 * Güvenli bağlamda `__Host-` önekli, yerel http'de öneksizdir. Çerez yalnız frontend ve Gateway aynı host altındaysa
 * okunabilir (CSRF çerezindeki kısıtla aynı); okunamazsa sonuç "ipucu yok" olur ve kullanıcı misafir görünür.
 */
const HINT_COOKIE_NAMES = ["__Host-ltz_session", "ltz_session"] as const;

export function hasSessionHint(): boolean {
  if (typeof document === "undefined") {
    return false;
  }
  return document.cookie.split(";").some((part) => {
    const [key, ...rest] = part.trim().split("=");
    return (HINT_COOKIE_NAMES as readonly string[]).includes(key ?? "") && rest.join("=") !== "";
  });
}
