/**
 * Ekip kayıtları — tek kaynak (Hakkımızda sayfası).
 *
 * Adlar, servis adları, README unvanları ve bağlantılar çevrilmez; yalnız kısa rol özeti
 * `About.team.members.<id>.role` i18n anahtarından gelir.
 *
 * CV: dosyalar `public/assets/cv/` altındadır (ASCII dosya adı, site içi yol). Aynı PDF'lerin kaynak kopyaları
 * README için `docs/assets/cv/` altında durur; bir CV güncellenince iki konum birlikte güncellenir.
 * Bir üyenin CV'si yoksa `cvHref` `null` yapılır; kart pasif simge gösterir. Başkasının CV'sine bağlantı verilmez.
 * E-posta alanı bilerek yoktur: kartlarda e-posta simgesi veya adresi gösterilmez.
 */
export const teamMemberIds = ["alper", "nisa", "umut", "hamza", "kerem"] as const;

export type TeamMemberId = (typeof teamMemberIds)[number];

export type TeamMember = {
  id: TeamMemberId;
  name: string;
  /** Sorumlu olunan backend servisleri (çevrilmez). */
  services: readonly string[];
  /** README'deki oyuncu unvanı (çevrilmez). */
  readmeTitle: string;
  /** `public/` altındaki avatar yolu. */
  avatar: string;
  github: string;
  linkedin: string;
  /** CV dosyasının genel adresi; yoksa `null` (pasif simge gösterilir). */
  cvHref: string | null;
};

export const teamMembers: readonly TeamMember[] = [
  {
    id: "alper",
    name: "Alper Temiz",
    services: ["Hardware", "Recommendation", "Performance", "LTZ Performance Web"],
    readmeTitle: "Tech Oracle",
    avatar: "/assets/team/player-alper.svg",
    github: "https://github.com/alperrte",
    linkedin: "https://www.linkedin.com/in/alpertemizz/",
    cvHref: "/assets/cv/alper-temiz-cv.pdf"
  },
  {
    id: "nisa",
    name: "Nisa Camcı",
    services: ["Community", "Content", "Event"],
    readmeTitle: "Guild Master",
    avatar: "/assets/team/player-nisa.svg",
    github: "https://github.com/Camcinisa",
    linkedin: "https://www.linkedin.com/in/nisa-camci/",
    cvHref: "/assets/cv/nisa-camci-cv.pdf"
  },
  {
    id: "umut",
    name: "Umut Yeşilyurt",
    services: ["Social", "Messaging", "LFP"],
    readmeTitle: "Party Summoner",
    avatar: "/assets/team/player-umut.svg",
    github: "https://github.com/Umutys61",
    linkedin: "https://www.linkedin.com/in/umut-ye%C5%9Filyurt",
    cvHref: "/assets/cv/umut-yesilyurt-cv.pdf"
  },
  {
    id: "hamza",
    name: "Hamza Taşbay",
    services: ["Game", "Library", "Review"],
    readmeTitle: "Lore Keeper",
    avatar: "/assets/team/player-hamza.svg",
    github: "https://github.com/HmzT270",
    linkedin: "https://www.linkedin.com/in/hamza-tasbay/",
    cvHref: "/assets/cv/hamza-tasbay-cv.pdf"
  },
  {
    id: "kerem",
    name: "Kerem Kalyoncu",
    services: ["Auth", "User", "Mail"],
    readmeTitle: "Gate Paladin",
    avatar: "/assets/team/player-kerem.svg",
    github: "https://github.com/KeremKalyoncu",
    linkedin: "https://www.linkedin.com/in/kerem-kalyoncu/",
    cvHref: "/assets/cv/kerem-kalyoncu-cv.pdf"
  }
];

/** Yalnız güvenli bağlantılar (https veya site içi yol) etkin sayılır. */
export function isLiveHref(href: string | null): href is string {
  return href !== null && (href.startsWith("https://") || href.startsWith("/"));
}
