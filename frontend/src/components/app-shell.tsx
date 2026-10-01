"use client";

import { useAuth } from "@/components/auth-provider";
import { LoadingState } from "@/components/states";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

const links = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/watchlists", label: "Watchlists" },
  { href: "/search", label: "Search" },
  { href: "/portfolio", label: "Portfolio" },
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
    <div className="min-h-full lg:grid lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside className="border-b border-line lg:min-h-screen lg:border-r lg:border-b-0">
        <div className="flex items-center justify-between gap-4 px-5 py-5 lg:block">
          <Link href="/dashboard" className="block">
            <p className="text-[0.68rem] font-medium tracking-[0.18em] text-brass uppercase">Smart Market</p>
            <p className="mt-1 font-serif text-xl text-foreground">Watchlist</p>
          </Link>
          <p className="truncate text-sm text-muted lg:mt-6">{auth.user?.displayName}</p>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 lg:block lg:px-3 lg:pb-6" aria-label="Primary">
          {links.map((link) => {
            const active = pathname === link.href || pathname.startsWith(`${link.href}/`);
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`block rounded-full px-3 py-2 text-sm whitespace-nowrap ${
                  active ? "bg-surface text-foreground" : "text-muted hover:text-foreground"
                }`}
                aria-current={active ? "page" : undefined}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
      </aside>
      <div className="min-w-0 px-4 py-6 sm:px-6 lg:px-10 lg:py-8">{children}</div>
    </div>
  );
}
