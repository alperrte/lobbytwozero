import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { PostDetailScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("post.title"), description: t("post.description"), robots: { index: false, follow: false } };
}

export default async function PostPage({ params }: { params: Promise<{ postId: string }> }) {
  const { postId } = await params;
  return (
    <Suspense>
      <PostDetailScreen postId={postId} />
    </Suspense>
  );
}
