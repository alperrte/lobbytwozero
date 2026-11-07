/** Kabukta (navbar profil menüsü) gösterilen en küçük profil özeti. */
export type ProfileSummary = {
  /** Gösterim adı; yoksa kullanıcı adı; ikisi de yoksa `null` (arayüz genel etiketi kullanır). */
  name: string | null;
  /** Küçük avatarın mutlak adresi; görsel yoksa `null`. */
  avatarUrl: string | null;
};
