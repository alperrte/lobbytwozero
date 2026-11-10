export const visibilities = ["PUBLIC", "FRIENDS", "PRIVATE"] as const;
export type Visibility = (typeof visibilities)[number];

export const guestAccesses = ["BASIC", "DETAILED"] as const;
export type GuestAccess = (typeof guestAccesses)[number];

export const themes = ["SYSTEM", "LIGHT", "DARK"] as const;
export type Theme = (typeof themes)[number];

export const locales = ["tr", "en"] as const;
export type SettingsLocale = (typeof locales)[number];

/** Gizlilik bölümleri; adlar sunucu yanıtındaki alanlarla aynıdır. */
export const privacyFields = ["friendsList", "library", "hardware", "performance"] as const;
export type PrivacyField = (typeof privacyFields)[number];

/** `GET/PUT /api/v1/user/me/privacy`: kullanıcının kendi gizlilik ayarları. */
export type PrivacySettings = {
  friendsList: Visibility;
  library: Visibility;
  hardware: Visibility;
  performance: Visibility;
  guestProfileAccess: GuestAccess;
  /** İyimser kilit; hiç kaydedilmemişse 0. */
  version: number;
};

/** `GET/PUT /api/v1/user/me/preferences`. */
export type Preferences = {
  locale: SettingsLocale;
  theme: Theme;
  /** Sunucu bu iki tercihi tutar; arayüz henüz kullanmaz, kaydederken olduğu gibi geri gönderilir. */
  reducedMotion: boolean;
  compactLayout: boolean;
  version: number;
};

/** `GET/PUT /api/v1/user/me/consents`: açık ve geri alınabilir rızalar. */
export type Consents = {
  /** Kişiselleştirilmiş öneri için favori/bitirilen oyun verisinin işlenmesi. Varsayılan: kapalı. */
  recommendation: boolean;
  recommendationChangedAt: string | null;
  version: number;
};
