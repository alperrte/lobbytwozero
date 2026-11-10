import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { OnboardingFlow } from "@/features/user";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Onboarding");
  return { title: t("title"), robots: { index: false, follow: false } };
}

export default function OnboardingPage() {
  return <OnboardingFlow />;
}
