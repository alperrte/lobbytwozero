import { authSections } from "./auth-sections";
import type { AdminGroup, AdminSection } from "./types";

/**
 * Yönetim paneli bölüm kaydı. YENİ BÖLÜM EKLEMEK İÇİN (kendi servisin için):
 *  1. `sections/<servis>-sections.tsx` dosyası aç; `AdminSection[]` dışa aktar (örnek: `auth-sections.tsx`).
 *     Bölümün bileşeni `SectionContext` alır: `setBadge` menü rozetini, `open` başka bölüme geçişi sağlar.
 *  2. O listeyi aşağıdaki `ADMIN_SECTIONS` dizisine ekle.
 *  3. Grubun yoksa `ADMIN_GROUPS` dizisine ekle.
 * Panelin iskeletine (`admin-shell.tsx`) dokunmak gerekmez. Yetki sunucudadır (servisin kendi `ROLE_ADMIN` kontrolü).
 */
export const ADMIN_GROUPS: AdminGroup[] = [
  { id: "auth", label: "Auth" },
  { id: "user", label: "User" },
  { id: "mail", label: "Mail" }
];

export const ADMIN_SECTIONS: AdminSection[] = [
  ...authSections
];
