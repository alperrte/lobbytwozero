import { Icon } from "@/shared/components/ui/icons";

/** Onay penceresinde başarısız işlemin nedeni. Pencere açık kalır; metin ekran okuyucuya hemen duyurulur. */
export function DialogError({ message }: Readonly<{ message: string }>) {
  return (
    <p role="alert" className="flex items-start gap-2 text-sm text-brand-text">
      <Icon name="info" className="mt-0.5 size-4 shrink-0" />
      {message}
    </p>
  );
}
