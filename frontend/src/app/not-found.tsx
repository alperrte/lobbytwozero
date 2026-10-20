import Link from "next/link";

export default function NotFound() {
  return (
    <main className="p-8">
      <h1>404 - Sayfa bulunamadı</h1>
      <Link href="/">Ana sayfaya dön</Link>
    </main>
  );
}
