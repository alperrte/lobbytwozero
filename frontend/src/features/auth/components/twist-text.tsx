import { twistAngle } from "../services/auth-motion";

type TwistTextProps = {
  text: string;
  /** Harf harf burgu animasyonu oynasın mı? Kapalıysa düz metin gösterilir. */
  animate: boolean;
  className?: string;
};

/**
 * Oyun açılış başlığı gibi harflerin tek tek burgulanarak yerine oturduğu başlık (CSS: `auth-twist-char`).
 * Erişilebilirlik: metin ekran okuyucuya BİR kez ve bütün olarak (`sr-only`) verilir; harfleri ayıran görsel katman
 * `aria-hidden` olduğu için tekrar okunmaz. Açı farkları indekse bağlıdır (rastgele değil), SSR ile istemci aynıdır.
 */
export function TwistText({ text, animate, className }: TwistTextProps) {
  if (!animate) {
    return <span className={className}>{text}</span>;
  }
  const words = text.split(" ");
  let index = 0;

  return (
    <span className={className}>
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {words.map((word, wordIndex) => (
          <span key={`${word}-${wordIndex}`}>
            <span className="inline-block whitespace-nowrap">
              {Array.from(word).map((character) => {
                const position = index++;
                return (
                  <span
                    key={position}
                    className="auth-twist-char"
                    style={{ "--i": position, "--r": twistAngle(position) } as React.CSSProperties}
                  >
                    {character}
                  </span>
                );
              })}
            </span>
            {wordIndex < words.length - 1 ? " " : null}
          </span>
        ))}
      </span>
    </span>
  );
}
