"use client";

import { useAuth } from "@/components/auth-provider";
import { ApiError } from "@/lib/api/client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

type Mode = "login" | "register";

export function AuthCard({ mode }: { mode: Mode }) {
  const auth = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const nextErrors = validate(mode, email, password, displayName);
    setFieldErrors(nextErrors);
    setError(null);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }
    setPending(true);
    try {
      if (mode === "login") {
        await auth.login(email.trim(), password);
      } else {
        await auth.register(email.trim(), password, displayName.trim());
      }
      router.replace("/dashboard");
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : null;
      setError(apiError?.message ?? "Could not sign you in.");
      setFieldErrors(apiError?.fieldErrors ?? {});
      setPending(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-full w-full max-w-md flex-col justify-center px-6 py-16">
      <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Smart Market Watchlist</p>
      <h1 className="mt-3 font-serif text-4xl text-foreground">{mode === "login" ? "Sign in" : "Create an account"}</h1>
      <p className="mt-3 text-sm leading-6 text-muted">
        {mode === "login"
          ? "Use the email and password for your account."
          : "Password must be 8 to 72 characters. It is sent only to sign you up and is not stored in the browser."}
      </p>
      <form className="mt-8 grid gap-4" onSubmit={onSubmit} noValidate>
        {mode === "register" ? (
          <label className="grid gap-1 text-sm">
            <span>Name</span>
            <input className="field" value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />
            <FieldNote message={fieldErrors.displayName} />
          </label>
        ) : null}
        <label className="grid gap-1 text-sm">
          <span>Email</span>
          <input className="field" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" />
          <FieldNote message={fieldErrors.email} />
        </label>
        <label className="grid gap-1 text-sm">
          <span>Password</span>
          <input className="field" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={mode === "login" ? "current-password" : "new-password"} />
          <FieldNote message={fieldErrors.password} />
        </label>
        {error ? <p className="text-sm text-negative" role="alert">{error}</p> : null}
        <button className="button-primary" type="submit" disabled={pending}>
          {pending ? "Please wait" : mode === "login" ? "Sign in" : "Create account"}
        </button>
      </form>
      <p className="mt-6 text-sm text-muted">
        {mode === "login" ? (
          <>No account yet? <Link className="text-foreground underline" href="/register">Create one</Link></>
        ) : (
          <>Already registered? <Link className="text-foreground underline" href="/login">Sign in</Link></>
        )}
      </p>
    </main>
  );
}

function FieldNote({ message }: { message?: string }) {
  if (!message) {
    return null;
  }
  return <span className="text-xs text-negative">{message}</span>;
}

function validate(mode: Mode, email: string, password: string, displayName: string): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!email.trim() || !email.includes("@")) {
    errors.email = "Enter a valid email address.";
  }
  if (!password) {
    errors.password = "Enter your password.";
  } else if (mode === "register" && (password.length < 8 || password.length > 72)) {
    errors.password = "Use 8 to 72 characters.";
  } else if (password.length > 72) {
    errors.password = "Password must be 72 characters or fewer.";
  }
  if (mode === "register" && !displayName.trim()) {
    errors.displayName = "Enter your name.";
  } else if (displayName.trim().length > 120) {
    errors.displayName = "Name must be 120 characters or fewer.";
  }
  return errors;
}
