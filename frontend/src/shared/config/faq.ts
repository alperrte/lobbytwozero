/**
 * SSS içerik yapısı: statik, backend ve veritabanı gerektirmez.
 *
 * Bu dosya yalnız YAPIYI taşır (kategori sırası, soru kimliği, hangi kategoriye ait olduğu, sıra).
 * Görünen metinler `Faq` i18n ad alanından okunur (TR/EN):
 *   - kategori adı:  `Faq.categories.<categoryId>`
 *   - soru başlığı:  `Faq.items.<id>.title`
 *   - soru cevabı:   `Faq.items.<id>.answer`
 *
 * Yeni soru eklemek için buraya bir kayıt ve iki dildeki metinler yeterlidir. Cevaplar yayında olmayan
 * bir özelliği hazırmış gibi sunmaz; "planlanıyor / hedefleniyor" dili kullanılır.
 */
export const faqCategoryIds = [
  "general",
  "account",
  "games",
  "social",
  "hardware",
  "security"
] as const;

export type FaqCategoryId = (typeof faqCategoryIds)[number];

export type FaqCategory = {
  id: FaqCategoryId;
  order: number;
};

export type FaqItem = {
  /** Benzersiz kimlik; i18n anahtarı ve DOM kimliği için kullanılır. */
  id: string;
  categoryId: FaqCategoryId;
  /** Kategori içindeki sıra (1'den başlar). */
  order: number;
};

export const faqCategories: readonly FaqCategory[] = faqCategoryIds.map((id, index) => ({
  id,
  order: index + 1
}));

export const faqItems = [
  { id: "whatIs", categoryId: "general", order: 1 },
  { id: "purpose", categoryId: "general", order: 2 },
  { id: "availability", categoryId: "general", order: 3 },
  { id: "accountRequired", categoryId: "general", order: 4 },

  { id: "createAccount", categoryId: "account", order: 1 },
  { id: "steamGoogle", categoryId: "account", order: 2 },
  { id: "editProfile", categoryId: "account", order: 3 },
  { id: "deleteAccount", categoryId: "account", order: 4 },

  { id: "discoverGames", categoryId: "games", order: 1 },
  { id: "libraryFavorites", categoryId: "games", order: 2 },
  { id: "ratingsReviews", categoryId: "games", order: 3 },
  { id: "filterGames", categoryId: "games", order: 4 },

  { id: "friends", categoryId: "social", order: 1 },
  { id: "messaging", categoryId: "social", order: 2 },
  { id: "findTeam", categoryId: "social", order: 3 },
  { id: "communityPosts", categoryId: "social", order: 4 },
  { id: "eventsTournaments", categoryId: "social", order: 5 },

  { id: "addHardware", categoryId: "hardware", order: 1 },
  { id: "multipleProfiles", categoryId: "hardware", order: 2 },
  { id: "fpsCompatibility", categoryId: "hardware", order: 3 },
  { id: "ltzPerformance", categoryId: "hardware", order: 4 },
  { id: "recommendations", categoryId: "hardware", order: 5 },

  { id: "accountSecurity", categoryId: "security", order: 1 },
  { id: "twoFactor", categoryId: "security", order: 2 },
  { id: "visibility", categoryId: "security", order: 3 },
  { id: "reporting", categoryId: "security", order: 4 }
] as const satisfies readonly FaqItem[];

export type FaqItemId = (typeof faqItems)[number]["id"];

/** Bir kategorinin sorularını `order` sırasıyla döndürür. */
export function faqItemsOf(categoryId: FaqCategoryId): readonly (typeof faqItems)[number][] {
  return faqItems
    .filter((item) => item.categoryId === categoryId)
    .toSorted((a, b) => a.order - b.order);
}

export const faqAnchorId = (categoryId: FaqCategoryId) => `faq-${categoryId}`;
