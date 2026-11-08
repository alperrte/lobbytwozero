import Image from "next/image";

type BrandLogoProps = {
  wordmarkClassName: string;
  markClassName: string;
};

/**
 * Gerçek LTZ marka varlıkları (kırpılmış, optimize türevler; kaynaklar ltz-yazi.png / ltz-icon.png).
 * Erişilebilir ad bağlantıdadır; görseller dekoratiftir. Yatay logo ve ikon CSS ile değişir.
 * Zemin şeffaftır ve iki temada da okunur; invert veya sahte logo kullanılmaz.
 */
export function BrandLogo({ wordmarkClassName, markClassName }: BrandLogoProps) {
  return (
    <>
      <Image
        src="/assets/brand/ltz-wordmark.png"
        alt=""
        width={480}
        height={147}
        sizes="128px"
        className={wordmarkClassName}
      />
      <Image
        src="/assets/brand/ltz-mark.png"
        alt=""
        width={256}
        height={181}
        sizes="48px"
        className={markClassName}
      />
    </>
  );
}
