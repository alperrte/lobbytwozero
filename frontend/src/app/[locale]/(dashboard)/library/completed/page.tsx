import { Suspense } from "react";
import { LibraryOverview, LibraryLoading } from "@/features/library";

export default function Page() {
  return <Suspense fallback={<LibraryLoading />}><LibraryOverview view="completed" /></Suspense>;
}
