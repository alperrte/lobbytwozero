/**
 * `features/social` açık export'u. Sayfalar (`src/app`) özelliğe yalnız buradan erişir; iç dosyalar doğrudan import edilmez.
 * Her görünüm oturum kapısının arkasındadır: oturum doğrulanmadan sosyal istek atılmaz, misafir girişe yönlenir.
 */
export { BlockedView, FriendsView, RequestsView, SentRequestsView } from "./components/social-screens";
