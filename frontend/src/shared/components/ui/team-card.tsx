import { IconAction } from "@/shared/components/ui/icon-action";
import { Icon } from "@/shared/components/ui/icons";
import { SocialIcon } from "@/shared/components/ui/social-icons";
import { isLiveHref, type TeamMember } from "@/shared/config/team";

type TeamCardLabels = {
  role: string;
  github: string;
  linkedin: string;
  cv: string;
  /** CV dosyası yoksa gösterilen tooltip metni. */
  cvMissingTooltip: string;
  /** CV dosyası yoksa erişilebilir ad. */
  cvMissingLabel: string;
};

type TeamCardProps = {
  member: TeamMember;
  labels: TeamCardLabels;
  className?: string;
};

/**
 * Ekip üyesi kartı: avatar, ad, README unvanı, kısa rol özeti, servisler ve GitHub / LinkedIn / CV simgeleri.
 * E-posta simgesi veya adresi bilerek yoktur. CV yoksa pasif simge gösterilir (bkz. `shared/config/team.ts`).
 */
export function TeamCard({ member, labels, className }: TeamCardProps) {
  const cvLive = isLiveHref(member.cvHref);

  return (
    <li
      data-team-card={member.id}
      className={`flex flex-col rounded-panel border border-border bg-surface p-5 sm:p-6 ${className ?? ""}`}
    >
      <div className="flex items-center gap-4">
        {/* eslint-disable-next-line @next/next/no-img-element -- küçük, sabit SVG avatar; optimizasyon gerekmez */}
        <img
          src={member.avatar}
          alt=""
          width={72}
          height={72}
          className="size-[4.5rem] shrink-0 rounded-panel border border-border bg-surface-elevated object-cover"
        />
        <div className="min-w-0">
          <h3 className="font-display text-lg font-semibold leading-tight text-text-primary">
            {member.name}
          </h3>
          <p className="mt-1 text-sm font-medium text-brand-text">{member.readmeTitle}</p>
        </div>
      </div>

      <p className="mt-4 text-sm leading-relaxed text-text-secondary">{labels.role}</p>
      <p className="mt-2 text-sm font-medium text-text-primary">{member.services.join(", ")}</p>

      <div className="mt-5 flex items-center gap-2 pt-1">
        <IconAction label={labels.github} tooltip={labels.github} href={member.github}>
          <SocialIcon id="github" className="size-5" />
        </IconAction>
        <IconAction label={labels.linkedin} tooltip={labels.linkedin} href={member.linkedin}>
          <SocialIcon id="linkedin" className="size-5" />
        </IconAction>
        {cvLive ? (
          <IconAction label={labels.cv} tooltip={labels.cv} href={member.cvHref}>
            <Icon name="fileText" />
          </IconAction>
        ) : (
          <IconAction label={labels.cvMissingLabel} tooltip={labels.cvMissingTooltip} href={null}>
            <Icon name="fileText" />
          </IconAction>
        )}
      </div>
    </li>
  );
}
