import { Suspense } from "react";
import { LibraryProfileView, LibraryLoading } from "@/features/library";

export default async function Page({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return <Suspense fallback={<LibraryLoading />}><LibraryProfileView userId={userId} /></Suspense>;
}
