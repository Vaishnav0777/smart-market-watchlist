"use client";

import { useAuth } from "@/components/auth-provider";
import Link from "next/link";

export default function Home() {
  const auth = useAuth();

  return (
    <main className="flex flex-1 items-center">
      <div className="mx-auto w-full max-w-3xl px-6 py-24">
        <div className="mb-10 flex h-11 w-11 items-center justify-center rounded-full border border-brass/40">
          <span className="h-2.5 w-2.5 rounded-full bg-brass" />
        </div>
        <p className="text-xs font-medium tracking-[0.22em] text-brass uppercase">
          Market intelligence
        </p>
        <h1 className="mt-4 max-w-xl font-serif text-5xl leading-tight tracking-tight text-foreground sm:text-6xl">
          Smart Market Watchlist
        </h1>
        <p className="mt-6 max-w-xl text-xl leading-8 text-muted">
          Come back later and see what actually changed on the instruments you follow.
        </p>
        <div className="mt-10 flex flex-wrap gap-3">
          {auth.status === "authenticated" ? (
            <Link className="button-primary" href="/dashboard">Open dashboard</Link>
          ) : (
            <>
              <Link className="button-primary" href="/login">Sign in</Link>
              <Link className="button-secondary" href="/register">Create an account</Link>
            </>
          )}
        </div>
        <p className="mt-12 max-w-lg border-t border-line pt-6 text-sm leading-6 text-muted">
          Market figures in this project are synthetic sample data. The application describes observed changes. It does not predict prices.
        </p>
      </div>
    </main>
  );
}
