/**
 * Auth ekranları arası geçişlerin yön kuralları. Yol bilgisi next-intl `usePathname()` değeridir (dahili yol:
 * `/login`, `/register`, `/forgot-password`...). Saf fonksiyonlardır; DOM bilmezler.
 */
export type AuthScreen = "login" | "register" | "forgot" | "other";

/** Kartın içeriğine uygulanan giriş hareketi. `none`: ilk açılış (kartın kendi giriş sahnesi oynar). */
export type CardTransition = "none" | "from-right" | "from-left" | "unfold" | "settle" | "fade";

export function screenForPath(pathname: string): AuthScreen {
  switch (pathname) {
    case "/login":
      return "login";
    case "/register":
      return "register";
    case "/forgot-password":
      return "forgot";
    default:
      return "other";
  }
}

/** Sol karşılama metni: kayıt akışındaki sayfalar kayıt metnini, diğerleri giriş metnini gösterir. */
export function welcomeVariantFor(pathname: string): "login" | "register" {
  return pathname === "/register" || pathname === "/verify-email" ? "register" : "login";
}

/**
 * Geçiş hareketi:
 * - Giriş → Kayıt (ve Kayıt → E-posta doğrulama): yeni kart sağdan gelir (ileri).
 * - Kayıt → Giriş: soldan gelir (geri).
 * - → Şifremi unuttum: kart aşağıya doğru "çözülür" (sakin).
 * - Şifremi unuttum → başka ekran: hafifçe yerine oturur.
 * - Diğer her şey: kısa solma.
 */
export function transitionBetween(previous: string | null, next: string): CardTransition {
  if (previous === null || previous === next) {
    return "none";
  }
  const from = screenForPath(previous);
  const to = screenForPath(next);
  if (to === "forgot") {
    return "unfold";
  }
  if (from === "forgot") {
    return "settle";
  }
  if (from === "login" && to === "register") {
    return "from-right";
  }
  if (from === "register" && to === "login") {
    return "from-left";
  }
  if (from === "register" && next === "/verify-email") {
    return "from-right";
  }
  return "fade";
}

/** Çıkan kartın hareketi (yeni ekranın giriş hareketine eşlenir). */
export type CardExit = "left" | "right" | "down" | "fold" | "fade";

/**
 * Giriş hareketinin çıkış karşılığı: ileri giden akışta eski içerik sola, geri giderken sağa çıkar; şifremi unuttum'a
 * giderken eski form hafifçe aşağı sönerek kaybolur, şifremi unuttum'dan dönerken kapanır (yukarı toplanır).
 */
export function exitFor(transition: CardTransition): CardExit {
  switch (transition) {
    case "from-right":
      return "left";
    case "from-left":
      return "right";
    case "unfold":
      return "down";
    case "settle":
      return "fold";
    default:
      return "fade";
  }
}

/** Harf başına küçük, belirleyici (rastgele olmayan) açı farkı: SSR ile istemci aynı çıktıyı üretir. */
export function twistAngle(index: number): number {
  return ((index * 37) % 9) * 4 - 16;
}
