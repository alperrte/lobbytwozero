/**
 * `features/auth` açık export'u. Sayfalar (`src/app`) ve başka katmanlar özelliğe yalnız buradan erişir;
 * iç dosyalar (`services`, `hooks/use-login` vb.) doğrudan import edilmez.
 */
export { AuthShell } from "./components/auth-shell";
export { LoginForm } from "./components/login-form";
export { SessionProvider } from "./components/session-provider";
export { useSession } from "./hooks/use-session";
export type { SessionValue } from "./hooks/use-session";
export type { AuthMe } from "./types/auth.types";
