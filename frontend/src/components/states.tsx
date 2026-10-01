import { ApiError } from "@/lib/api/client";

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return (
    <div className="surface flex items-center gap-3 px-4 py-5 text-sm text-muted" role="status">
      <span className="h-2 w-2 animate-pulse rounded-full bg-brass" />
      {label}
    </div>
  );
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="surface px-5 py-8">
      <h2 className="font-serif text-2xl text-foreground">{title}</h2>
      <p className="mt-2 max-w-xl text-sm leading-6 text-muted">{body}</p>
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : "Something went wrong while loading this page.";
  const expired = error instanceof ApiError && error.status === 401;
  return (
    <div className="surface border-negative/40 px-5 py-6" role="alert">
      <h2 className="font-serif text-2xl text-foreground">{expired ? "Sign in required" : "Could not load this"}</h2>
      <p className="mt-2 text-sm leading-6 text-muted">{message}</p>
      {onRetry && !expired ? (
        <button className="button-secondary mt-4" type="button" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}

export function SyntheticNotice() {
  return (
    <p className="text-xs leading-5 text-muted">
      Prices on this screen are synthetic development fixtures. They are not live or historical market data.
    </p>
  );
}
