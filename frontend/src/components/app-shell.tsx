"use client";

import { useAuth } from "@/components/auth-provider";
import { LoadingState } from "@/components/states";
import { ThemeToggle } from "@/components/theme-toggle";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

const links = [
  { href: "/dashboard", label: "Overview" },
  { href: "/watchlists", label: "Watchlist" },
  { href: "/portfolio", label: "Portfolio" },
  { href: "/search", label: "Markets" },
  { href: "/assistant", label: "Assistant" },
  { href: "/account", label: "Account" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (auth.status === "anonymous") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  if (auth.status !== "authenticated") {
    return (
      <main className="mx-auto w-full max-w-3xl px-6 py-16">
        <LoadingState label="Checking your session" />
      </main>
    );
  }

  return (
    <div className="min-h-full bg-background">
      <header className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-2">
        <Link href="/dashboard" className="text-sm font-semibold tracking-[0.12em] text-foreground">MARKETPULSE</Link>
        <div className="flex items-center gap-3">
          <Link href="/account" className="max-w-[12rem] truncate text-xs text-muted">{auth.user?.displayName}</Link>
          <ThemeToggle />
        </div>
      </header>
      <div className="lg:grid lg:grid-cols-[200px_minmax(0,1fr)]">
        <nav className="flex gap-1 overflow-x-auto border-b border-line bg-surface px-2 py-1.5 lg:block lg:min-h-[calc(100vh-2.75rem)] lg:border-r lg:border-b-0 lg:py-2" aria-label="Primary">
          {links.map((link) => {
            const active = pathname === link.href || pathname.startsWith(`${link.href}/`);
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`block px-3 py-1.5 text-sm whitespace-nowrap ${
                  active ? "bg-hover font-medium text-foreground" : "text-muted hover:text-foreground"
                }`}
                aria-current={active ? "page" : undefined}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
        <div className="min-w-0 bg-background px-4 py-4 sm:px-5 lg:px-6 lg:py-5">{children}</div>
      </div>
    </div>
  );
}
