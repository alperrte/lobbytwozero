import { useTranslations } from "next-intl";
import { apiUrl } from "@/shared/lib/api/client";
import { GoogleLogo, SteamLogo } from "./brand-logos";

type SocialButtonsProps = {
  /** Metinler `Auth.social.<mode>.*` anahtarından gelir. */
  mode: "login" | "register";
};

const linkClass =
  "inline-flex min-h-12 items-center justify-center gap-2.5 rounded-xl border border-control-border bg-transparent px-4 py-3 text-sm font-semibold text-text-primary transition-colors duration-150 hover:border-text-secondary hover:bg-surface-elevated/60 motion-reduce:transition-none";

/**
 * Google ve Steam ile giriş/kayıt düğmeleri (gerçek marka logolarıyla). Bunlar API çağrısı değil TARAYICI GEZİNMESİDİR:
 * Auth sağlayıcıya yönlendirir (`302`), dönüş sonucu sabit frontend adresine URL parçasıyla gelir (`/auth/callback`).
 * Adres istemciden alınmaz. Telefon genişliğinde (<640 px) yalnız logo görünür; erişilebilir ad `aria-label` ile korunur.
 */
export function SocialButtons({ mode }: SocialButtonsProps) {
  const t = useTranslations("Auth.social");

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3">
        <a href={apiUrl("/api/v1/auth/oauth/google/start")} aria-label={t(`${mode}.google`)} className={linkClass}>
          <GoogleLogo className="size-6 shrink-0 sm:size-5" />
          <span className="max-sm:hidden">{t(`${mode}.google`)}</span>
        </a>
        <a href={apiUrl("/api/v1/auth/oauth/steam/start")} aria-label={t(`${mode}.steam`)} className={linkClass}>
          <SteamLogo className="size-6 shrink-0 sm:size-5" />
          <span className="max-sm:hidden">{t(`${mode}.steam`)}</span>
        </a>
      </div>
      <div className="flex items-center gap-3 text-xs text-text-secondary" role="separator" aria-label={t("or")}>
        <span className="h-px flex-1 bg-border" />
        <span aria-hidden="true">{t("or")}</span>
        <span className="h-px flex-1 bg-border" />
      </div>
    </div>
  );
}
