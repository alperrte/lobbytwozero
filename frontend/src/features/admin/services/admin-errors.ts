import { ApiHttpError } from "@/shared/lib/api/client";
import { authErrorCode } from "@/features/auth";

/**
 * Yönetim paneli hata metni (yalnız Türkçe). Sunucunun kendi metni gösterilmez; kod/durum okunur,
 * girilen değerler ve iç ayrıntı arayüze sızmaz.
 */
export function adminErrorMessage(error: unknown): string {
  const code = authErrorCode(error);
  switch (code) {
    case "INVALID_CREDENTIALS":
      return "Kimlik bilgileri hatalı.";
    case "INVALID_TWO_FACTOR_CODE":
    case "MFA_FAILED":
      return "Kod hatalı ya da doğrulama oturumunun süresi doldu.";
    case "TOO_MANY_ATTEMPTS":
      return "Çok fazla deneme yapıldı. Biraz bekleyip tekrar dene.";
    case "ACCOUNT_SUSPENDED":
    case "ACCOUNT_BANNED":
    case "ACCOUNT_UNAVAILABLE":
    case "ACCOUNT_DELETION_PENDING":
    case "EMAIL_NOT_VERIFIED":
      return "Bu hesapla giriş yapılamıyor.";
    case "DELETION_SUPPORT_REQUEST_NOT_FOUND":
      return "Talep bulunamadı; başka biri işlemiş olabilir. Listeyi yenile.";
    case "DELETION_SUPPORT_ALREADY_DECIDED":
      return "Bu talep zaten karara bağlanmış. Listeyi yenile.";
    case "TARGET_NOT_ACTIVE":
      return "Hesap artık etkin değil; talep onaylanamaz. Reddedebilirsin.";
    case "DELETION_SELF_SERVICE_AVAILABLE":
      return "Hesap artık kendi kendine silebilen türde (A); talep onaylanamaz. Reddedebilirsin.";
    case "NETWORK":
      return "Sunucuya ulaşılamadı.";
    default:
      break;
  }
  if (error instanceof ApiHttpError) {
    if (error.status === 403) return "Bu işlem için yetkin yok ya da kendi hesabını değiştiremezsin.";
    if (error.status === 404) return "Kayıt bulunamadı.";
    if (error.status === 409) return "İşlem şu anki duruma uygun değil.";
    if (error.status === 400) return "Girdi geçersiz. Alanları kontrol et.";
  }
  return "İşlem yapılamadı. Tekrar dene.";
}
