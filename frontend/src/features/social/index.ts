/**
 * `features/social` açık export'u. Sayfalar (`src/app`) özelliğe yalnız buradan erişir; iç dosyalar doğrudan import edilmez.
 * Her görünüm oturum kapısının arkasındadır: oturum doğrulanmadan sosyal istek atılmaz, misafir girişe yönlenir.
 */
export { BlockedView, FriendsView, RequestsView, SentRequestsView } from "./components/social-screens";

// Mesajlaşma (features/messaging) için: kişi adları/avatarları ve yeni sohbette arkadaş seçimi.
export { fetchProfileCards } from "./services/user-profiles";
export type { ProfileCard } from "./services/user-profiles";
export { socialApi } from "./services/social-api";

// Takım Bul (features/lfp) için: sosyal sayfalarla aynı başlık kartı.
export { SocialHero } from "./components/social-hero";
