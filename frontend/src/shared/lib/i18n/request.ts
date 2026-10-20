import { cookies } from "next/headers";
import { hasLocale } from "next-intl";
import { getRequestConfig } from "next-intl/server";
import { defaultLocale, localeCookieName, locales } from "./config";

export default getRequestConfig(async () => {
  const candidate = (await cookies()).get(localeCookieName)?.value;
  const locale = hasLocale(locales, candidate)
    ? candidate
    : defaultLocale;

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default,
    timeZone: "Europe/Istanbul"
  };
});
