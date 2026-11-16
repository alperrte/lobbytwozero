import qrcode from "qrcode-generator";

type TotpQrProps = {
  /** `otpauth://` adresi (gizli anahtarı içerir). Yalnız tarayıcıda çizilir; hiçbir dış servise gönderilmez. */
  uri: string;
  /** Ekran okuyucu etiketi (kodun ne olduğunu söyler; adresi ya da anahtarı açıklamaz). */
  label: string;
};

const QUIET_ZONE = 4;

/** QR matrisini tek bir SVG yoluna çevirir (koyu hücreler); `null`: veri QR'a sığmadı ya da boş. */
function buildPath(uri: string): { size: number; path: string } | null {
  if (uri === "") {
    return null;
  }
  try {
    const qr = qrcode(0, "M");
    qr.addData(uri);
    qr.make();
    const count = qr.getModuleCount();
    let path = "";
    for (let row = 0; row < count; row += 1) {
      for (let col = 0; col < count; col += 1) {
        if (qr.isDark(row, col)) {
          path += `M${col + QUIET_ZONE} ${row + QUIET_ZONE}h1v1h-1z`;
        }
      }
    }
    return { size: count + QUIET_ZONE * 2, path };
  } catch {
    return null;
  }
}

/**
 * TOTP kurulum QR kodu. Gizli anahtar içerdiği için tarayıcıda yerel üretilir (kütüphane ağ isteği yapmaz) ve
 * `dangerouslySetInnerHTML` kullanılmaz; SVG öğeleri doğrudan çizilir. Tarayıcıların okuyabilmesi için tema ne olursa
 * olsun beyaz zemin üstüne siyah hücreler ve 4 hücrelik sessiz bölge kullanılır. Üretilemezse hiçbir şey çizilmez
 * (kullanıcı altındaki anahtarı elle girebilir).
 */
export function TotpQr({ uri, label }: TotpQrProps) {
  const built = buildPath(uri);
  if (built === null) {
    return null;
  }
  return (
    <svg
      role="img"
      aria-label={label}
      data-testid="totp-qr"
      viewBox={`0 0 ${built.size} ${built.size}`}
      shapeRendering="crispEdges"
      className="mx-auto size-48 rounded-lg bg-white"
    >
      <rect width={built.size} height={built.size} fill="#fff" />
      <path d={built.path} fill="#000" />
    </svg>
  );
}
