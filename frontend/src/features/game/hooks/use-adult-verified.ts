"use client";

import { useSession } from "@/features/auth";

/**
 * Hesabın yaşı doğrulanmış mı (oturumlu ve `/me` `adultVerified === true`)? Yalnız arayüz kolaylığıdır: kutuyu göstermek ve boşa istek atmamak için.
 * Asıl kapı sunucudadır (Game, token'daki `adult_verified` iddiası); doğrulama geri alınınca iddia ≤60 sn gecikebileceğinden gösterimde esas alınan şey
 * sunucu yanıtındaki `includeAdultApplied`dır.
 */
export function useAdultVerified(): boolean {
  const session = useSession();
  return session.status === "authenticated" && session.me.adultVerified === true;
}
