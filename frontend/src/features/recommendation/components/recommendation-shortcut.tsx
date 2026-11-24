import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";

/**
 * Başka ekranlardan (Performans paneli) "Sana Özel" bölümüne geçiş. API çağrısı yapmaz; öneri veya uyumluluk sonucu
 * göstermez, yalnız gerçek rotalara bağlantı verir.
 */
export function RecommendationShortcut() {
  const t = useTranslations("Recommendation.shortcut");
  return <div className="space-y-4">
    <p className="max-w-3xl text-sm leading-6 text-text-secondary">{t("description")}</p>
    <div className="flex flex-wrap gap-3">
      <Link href="/for-you/hardware" className={buttonClass("secondary", "md")}><Icon name="cpu" className="size-4" />{t("hardware")}</Link>
      <Link href="/for-you/games" className={buttonClass("ghost", "md")}><Icon name="sparkles" className="size-4" />{t("games")}</Link>
    </div>
  </div>;
}
