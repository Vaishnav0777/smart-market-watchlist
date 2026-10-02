"use client";

import { useAuth } from "@/components/auth-provider";
import { ThemeToggle } from "@/components/theme-toggle";
import Link from "next/link";

export default function Home() {
  const auth = useAuth();

  return (
    <main className="relative flex flex-1 items-center">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="mx-auto w-full max-w-3xl px-6 py-24">
        <h1 className="max-w-xl text-3xl font-semibold tracking-tight text-foreground">
          MarketPulse
        </h1>
        <p className="mt-3 max-w-xl text-lg text-muted">
          Know what changed.
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
