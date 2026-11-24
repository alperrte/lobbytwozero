/**
 * `features/messaging` açık export'u. Sayfalar (`src/app`) özelliğe yalnız buradan erişir; iç dosyalar doğrudan import edilmez.
 * Ekran oturum kapısının arkasındadır: oturum doğrulanmadan mesajlaşma isteği atılmaz, misafir girişe yönlenir.
 */
export { MessagingScreen } from "./components/messaging-screen";
