import { getTranslations } from "next-intl/server";
import { RequestsView } from "@/features/social";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";
import { PageContainer } from "@/shared/components/layout/page-container";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("requests"), robots: { index: false, follow: false } };
}

export default function FriendRequestsPage() {
  return (
    <AppShell footer={<Footer />}>
      <PageContainer>
        <RequestsView />
      </PageContainer>
    </AppShell>
  );
}
