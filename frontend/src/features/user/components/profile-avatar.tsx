import { cn } from "@/shared/utils/cn";

/** Yuvarlak avatar: görsel varsa görsel, yoksa adın ilk harfi. Görsel dekoratiftir (ad zaten metinde yazar). */
export function ProfileAvatar({
  name,
  src,
  className,
  initialClassName = "text-3xl"
}: {
  name: string;
  src: string | null;
  className?: string;
  /** Harfin boyutu; küçük avatarlarda küçültülür. */
  initialClassName?: string;
}) {
  const initial = Array.from(name.trim())[0]?.toLocaleUpperCase() ?? "";
  return (
    <span
      className={cn(
        "flex items-center justify-center overflow-hidden rounded-full bg-surface-elevated text-text-secondary",
        className
      )}
    >
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="" className="size-full object-cover" referrerPolicy="no-referrer" />
      ) : (
        <span aria-hidden="true" className={cn("font-display font-semibold text-text-primary", initialClassName)}>
          {initial}
        </span>
      )}
    </span>
  );
}
