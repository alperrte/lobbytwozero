"use client";

import { useTranslations } from "next-intl";
import { AuthIcon } from "./auth-icons";

type PasswordToggleProps = {
  visible: boolean;
  onToggle: () => void;
};

/** Şifre göster/gizle düğmesi: erişilebilir adı eyleme göre değişir, durumu `aria-pressed` ile bildirir. */
export function PasswordToggle({ visible, onToggle }: PasswordToggleProps) {
  const t = useTranslations("Auth.login");
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={visible}
      aria-label={visible ? t("hide") : t("show")}
      className="inline-flex size-10 items-center justify-center rounded-xl text-text-secondary transition-colors duration-150 hover:text-text-primary motion-reduce:transition-none"
    >
      <AuthIcon name={visible ? "eyeOff" : "eye"} />
    </button>
  );
}
