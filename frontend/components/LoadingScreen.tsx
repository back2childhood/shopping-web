export function LoadingScreen({ message }: { message: string }) {
  return (
    <main className="page-loading" aria-live="polite">
      <span className="brand-mark">S</span>
      <p>{message}</p>
    </main>
  );
}
