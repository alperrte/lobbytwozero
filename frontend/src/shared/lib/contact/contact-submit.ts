import type { ContactValues } from "@/shared/lib/contact/contact-form";

/**
 * İletişim formunun gönderim sözleşmesi. Form bileşeni yalnız bu arayüzü bilir; gerçek bir
 * gönderim (ör. Mail Service) eklendiğinde yeni bir adaptör yazılır ve form koduna dokunulmaz.
 *
 * Henüz backend uç noktası, e-posta altyapısı veya sözleşmesi YOKTUR (docs/api.md'de kayıt yok).
 * Bu yüzden tek adaptör `demoContactSubmit`'tir.
 */
export type ContactSubmitResult = { status: "demo" };

export type ContactSubmitAdapter = (values: ContactValues) => Promise<ContactSubmitResult>;

/**
 * Demo adaptör: ağ isteği, depolama, günlük veya başka bir yan etki YOKTUR.
 * Değerler hiçbir yere iletilmez; yalnızca formun doğrulamayı geçtiğini bildirir.
 */
export const demoContactSubmit: ContactSubmitAdapter = async () => ({ status: "demo" });
