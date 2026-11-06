"use client";

/**
 * Kabuğun (navbar profil menüsü) kullanıcı sınırı.
 * Kimlik doğrulama henüz yok; bu yüzden her zaman `null` (misafir) döner ve sahte ad/fotoğraf üretilmez.
 * Auth (Kerem'in auth/user servisleri) bağlandığında yalnız bu hook gerçek oturum verisini okuyacak
 * şekilde değişir; ProfileMenu ve Navbar aynı kalır.
 */
export type ShellUser = {
  displayName: string;
  avatarUrl?: string;
};

export function useShellUser(): ShellUser | null {
  return null;
}
