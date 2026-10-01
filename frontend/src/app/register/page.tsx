"use client";

import { AuthCard } from "@/components/auth-card";
import { useAuth } from "@/components/auth-provider";
import { LoadingState } from "@/components/states";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function RegisterPage() {
  const auth = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (auth.status === "authenticated") {
      router.replace("/dashboard");
    }
  }, [auth.status, router]);

  if (auth.status === "loading" || auth.status === "authenticated") {
    return (
      <main className="mx-auto w-full max-w-md px-6 py-16">
        <LoadingState label="Checking your session" />
      </main>
    );
  }

  return <AuthCard mode="register" />;
}
