/** Hata durumu: neden ve güvenli yeniden deneme yolu. */
export function ProfileStateNotice({
  title,
  body,
  actionLabel,
  onAction
}: {
  title: string;
  body: string;
  actionLabel: string;
  onAction: () => void;
}) {
  return (
    <section role="alert" className="max-w-prose rounded-panel border border-border bg-surface p-5 sm:p-6">
      <h2 className="font-display text-lg font-semibold">{title}</h2>
      <p className="mt-2 text-text-secondary">{body}</p>
      <button
        type="button"
        onClick={onAction}
        className="mt-4 inline-flex min-h-10 items-center rounded-control border border-control-border px-4 text-sm font-semibold transition-colors duration-150 hover:bg-surface-elevated"
      >
        {actionLabel}
      </button>
    </section>
  );
}
