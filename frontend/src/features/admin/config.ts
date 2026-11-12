/**
 * Yönetim panelinin gizli adresi. Sitede hiçbir yerden bağlantı verilmez, sitemap ve menüde yoktur, sayfa `noindex`tir.
 * Adres bir SIR DEĞİLDİR (güvenlik sınırı Auth'taki ROLE_ADMIN + zorunlu 2FA'dır); yalnız keşfedilmesini zorlaştırır.
 * Dil öneksizdir ve yalnız Türkçedir; `proxy.ts` bu yolu i18n yönlendirmesinden muaf tutar.
 */
export const ADMIN_BASE_PATH = "/atak-lan-o5-konseygir-is";
