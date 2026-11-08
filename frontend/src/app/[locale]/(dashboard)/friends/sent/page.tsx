import { getTranslations } from "next-intl/server";
import { SentRequestsView } from "@/features/social";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";
import { PageContainer } from "@/shared/components/layout/page-container";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("sent"), robots: { index: false, follow: false } };
}

export default function SentFriendRequestsPage() {
  return (
    <AppShell footer={<Footer />}>
      <PageContainer>
        <SentRequestsView />
      </PageContainer>
    </AppShell>
  );
}
