import { getTranslations } from "next-intl/server";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import { TeamCard } from "@/shared/components/ui/team-card";
import { teamMembers } from "@/shared/config/team";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("about");

const pillars = [
  { id: "discover", icon: "gamepad" },
  { id: "social", icon: "messages" },
  { id: "community", icon: "usersRound" },
  { id: "hardware", icon: "cpu" }
] as const satisfies readonly { id: string; icon: IconName }[];

const sectionTitleClass = "font-display text-2xl font-semibold text-balance text-text-primary";

/**
 * Hero, hikâye ve bölüm açıklamaları için paragraf genişliği: 72rem (1152px). Dar ekranda kap genişliğine iner
 * (max-w yalnız üst sınırdır), geniş ekranda cümleler erken kırılmaz. `text-pretty` tek sözcüklü son satırı azaltır.
 */
const proseWidthClass = "max-w-6xl text-pretty";

/**
 * Hakkımızda: hero, hikâye, hedefler, ekip ve kapanış. Uydurma tarih, kullanıcı sayısı veya
 * yayında olmayan özellik iddiası içermez. Ekip verisi `shared/config/team.ts` dosyasından gelir.
 */
export default async function AboutPage() {
  const t = await getTranslations("About");

  return (
    <PageContainer>
      <PageHeader
        title={t("hero.title")}
        description={t("hero.description")}
        className="sm:mb-14"
        descriptionClassName={proseWidthClass}
      />

      <div className="space-y-14 sm:space-y-20">
        <section aria-labelledby="about-story">
          <h2 id="about-story" className={sectionTitleClass}>
            {t("story.title")}
          </h2>
          <div
            className={`mt-4 space-y-4 text-base leading-relaxed text-text-secondary sm:text-lg ${proseWidthClass}`}
          >
            <p>{t("story.paragraphs.one")}</p>
            <p>{t("story.paragraphs.two")}</p>
            <p>{t("story.paragraphs.three")}</p>
          </div>
        </section>

        <section aria-labelledby="about-vision">
          <h2 id="about-vision" className={sectionTitleClass}>
            {t("vision.title")}
          </h2>
          <p className={`mt-3 text-base text-text-secondary ${proseWidthClass}`}>{t("vision.intro")}</p>
          <ul className="mt-6 grid gap-4 sm:grid-cols-2 min-[96rem]:grid-cols-4">
            {pillars.map((pillar) => (
              <li
                key={pillar.id}
                className="rounded-panel border border-border bg-surface p-5 sm:p-6"
              >
                <span className="inline-flex size-10 items-center justify-center rounded-control border border-border bg-surface-elevated text-brand-text">
                  <Icon name={pillar.icon} />
                </span>
                <h3 className="mt-4 font-display text-lg font-semibold text-text-primary">
                  {t(`vision.pillars.${pillar.id}.title`)}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-pretty text-text-secondary">
                  {t(`vision.pillars.${pillar.id}.body`)}
                </p>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="about-team">
          <h2 id="about-team" className={sectionTitleClass}>
            {t("team.title")}
          </h2>
          <p className={`mt-3 text-base text-text-secondary ${proseWidthClass}`}>{t("team.subtitle")}</p>
          <ul className="mt-8 flex flex-wrap justify-center gap-6">
            {teamMembers.map((member) => (
              <TeamCard
                key={member.id}
                member={member}
                className="w-full sm:w-[calc((100%-1.5rem)/2)] lg:w-[calc((100%-3rem)/3)]"
                labels={{
                  role: t(`team.members.${member.id}.role`),
                  github: t("team.github", { name: member.name }),
                  linkedin: t("team.linkedin", { name: member.name }),
                  cv: t("team.cv", { name: member.name }),
                  cvMissingLabel: t("team.cvMissingLabel", { name: member.name }),
                  cvMissingTooltip: t("team.cvMissingTooltip")
                }}
              />
            ))}
          </ul>
        </section>

        <section
          aria-labelledby="about-closing"
          className="rounded-panel border border-border bg-surface px-6 py-10 text-center sm:px-10 sm:py-14"
        >
          <h2
            id="about-closing"
            className="font-display text-2xl font-bold text-balance text-text-primary sm:text-3xl"
          >
            {t("closing.title")}
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-base text-pretty text-text-secondary">
            {t("closing.body")}
          </p>
        </section>
      </div>
    </PageContainer>
  );
}
