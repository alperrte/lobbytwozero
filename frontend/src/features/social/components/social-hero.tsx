import type { ReactNode } from "react";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { InfoTip } from "@/shared/components/ui/info-tip";
import { HeroScene } from "./hero-scene";

type SocialHeroProps = {
  icon: IconName;
  title: string;
  subtitle: string;
  figures?: 1 | 2;
  /** Sağdaki birincil eylem (ör. "Arkadaş Ekle"). */
  action?: ReactNode;
  /** Örnek veri uyarısı: rozet metni + açıklama. */
  preview?: { badge: string; description: string };
};

/**
 * Sosyal sayfaların ortak başlık kartı: tek `h1`, kısa açıklama, isteğe bağlı birincil eylem ve şehir illüstrasyonu.
 * Metin, illüstrasyonun önündeki yüzey geçişinin üstünde durur; iki temada da okunur.
 */
export function SocialHero({ icon, title, subtitle, figures = 2, action, preview }: SocialHeroProps) {
  return (
    <header className="relative isolate overflow-hidden rounded-panel border border-border bg-surface">
      <HeroScene figures={figures} />
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-gradient-to-r from-surface from-30% via-surface/55 via-50% to-transparent to-72% max-sm:from-55% max-sm:via-surface/85 max-sm:to-surface/40"
      />
      <div
        aria-hidden="true"
        className="absolute inset-x-0 bottom-0 h-0.5 bg-gradient-to-r from-primary via-accent to-primary"
      />
      <div className="relative flex min-h-36 flex-wrap items-center gap-x-6 gap-y-4 px-5 py-6 sm:min-h-40 sm:px-7">
        {/* En az 18rem: dar ekranda metin sıkışmak yerine eylem düğmesi alt satıra iner. */}
        <div className="flex min-w-0 flex-1 basis-72 items-start gap-4">
          <span className="mt-1 inline-flex size-12 shrink-0 items-center justify-center rounded-control bg-primary/12 text-brand-text ring-1 ring-primary/40">
            <Icon name={icon} className="size-7" />
          </span>
          <div className="min-w-0">
            <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">{title}</h1>
            <p className="mt-1.5 max-w-xl text-base text-text-secondary sm:text-lg">{subtitle}</p>
            {preview ? (
              <p className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-warning/50 bg-surface/80 py-0.5 ps-2.5 pe-1 text-xs font-semibold text-warning">
                <Icon name="info" className="size-3.5" />
                {preview.badge}
                <InfoTip subject={preview.badge}>{preview.description}</InfoTip>
              </p>
            ) : null}
          </div>
        </div>
        {action ? <div className="shrink-0 max-sm:w-full max-sm:*:w-full">{action}</div> : null}
      </div>
    </header>
  );
}
