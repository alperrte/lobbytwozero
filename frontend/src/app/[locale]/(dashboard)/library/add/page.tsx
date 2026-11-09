import { Suspense } from "react";
import { LibraryAddGame, LibraryLoading } from "@/features/library";

export default function Page() {
  return <Suspense fallback={<LibraryLoading />}><LibraryAddGame /></Suspense>;
}
