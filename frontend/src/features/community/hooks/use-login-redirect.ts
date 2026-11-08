"use client";

import { usePathname } from "next/navigation";
import { useRouter } from "@/shared/lib/i18n/navigation";

/**
 * Misafirin yazma eylemi (beğen, yorum, rapor…) onu girişe götürür ve girişten sonra aynı sayfaya döndürür.
 * `next` değerini giriş sayfası doğrular (açık yönlendirme engeli orada); burada yalnız mevcut yol verilir.
 */
export function useLoginRedirect() {
  const pathname = usePathname();
  const router = useRouter();
  return () => router.push({ pathname: "/login", query: { next: pathname } });
}
