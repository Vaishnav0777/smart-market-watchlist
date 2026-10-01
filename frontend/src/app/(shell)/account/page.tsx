"use client";

import { useAuth } from "@/components/auth-provider";
import { formatTimestamp } from "@/lib/format";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function AccountPage() {
  const auth = useAuth();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onLogout() {
    setPending(true);
    setError(null);
    try {
      await auth.logout();
      router.replace("/login");
    } catch {
      setError("Could not end the session. Try again.");
      setPending(false);
    }
  }

  if (!auth.user) {
    return null;
  }

  return (
    <div className="grid max-w-xl gap-6">
      <header>
        <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Account</p>
        <h1 className="mt-2 font-serif text-4xl">{auth.user.displayName}</h1>
      </header>
      <dl className="surface grid gap-4 px-5 py-5 text-sm">
        <div>
          <dt className="text-xs tracking-wide text-muted uppercase">Email</dt>
          <dd className="mt-1">{auth.user.email}</dd>
        </div>
        <div>
          <dt className="text-xs tracking-wide text-muted uppercase">Member since</dt>
          <dd className="mt-1">{formatTimestamp(auth.user.createdAt)}</dd>
        </div>
      </dl>
      <p className="text-sm leading-6 text-muted">
        Signing out revokes the refresh session. The refresh token stays in an HttpOnly cookie. The access token is kept only in memory for this tab.
      </p>
      {error ? <p className="text-sm text-negative" role="alert">{error}</p> : null}
      <button className="button-primary w-fit" type="button" onClick={onLogout} disabled={pending}>
        {pending ? "Signing out" : "Sign out"}
      </button>
    </div>
  );
}
