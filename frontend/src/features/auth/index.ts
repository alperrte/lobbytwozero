/**
 * `features/auth` açık export'u. Sayfalar (`src/app`) ve başka katmanlar özelliğe yalnız buradan erişir;
 * iç dosyalar (`services`, `hooks/use-login` vb.) doğrudan import edilmez.
 */
export { AuthCallback } from "./components/auth-callback";
export { AuthStage } from "./components/auth-stage";
export { ForgotPasswordForm } from "./components/forgot-password-form";
export { LoginForm } from "./components/login-form";
export { RegisterForm } from "./components/register-form";
export { ResetPasswordForm } from "./components/reset-password-form";
export { AccountDeletionSection } from "./components/account-deletion-section";
export { AccountSecuritySection } from "./components/account-security-section";
export { DeleteAccountForm } from "./components/delete-account-form";
export { AuthBackdrop } from "./components/auth-backdrop";
export { CancelDeletionForm } from "./components/cancel-deletion-form";
export { useRequireSession } from "./hooks/use-require-session";
export { SessionProvider } from "./components/session-provider";
export { VerifyEmailForm } from "./components/verify-email-form";
export { useSession } from "./hooks/use-session";
export type { SessionValue } from "./hooks/use-session";
export type { AuthMe } from "./types/auth.types";

// Yönetim paneli (features/admin) için: Auth uçları ve hata kodu çevirisi.
export { authApi } from "./services/auth-api";
export { authErrorCode } from "./services/auth-http";
export type { AdminAccount, AdminDeletionRequest, AdminTargetStatus, AuthErrorCode, LoginResult } from "./types/auth.types";
