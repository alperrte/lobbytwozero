"use client";

export default function Error({
  reset
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main role="alert" className="p-8">
      <h1>Beklenmeyen bir hata oluştu</h1>
      <button type="button" onClick={reset}>
        Tekrar dene
      </button>
    </main>
  );
}
