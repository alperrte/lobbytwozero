import { Suspense } from "react";
import { LibraryPrivacy, LibraryLoading } from "@/features/library";

export default function Page() {
  return <Suspense fallback={<LibraryLoading />}><LibraryPrivacy /></Suspense>;
}
