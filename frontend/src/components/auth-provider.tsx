"use client";

import { currentUser, login as loginRequest, logout as logoutRequest, register as registerRequest } from "@/lib/api/auth";
import { clearSession, readSession } from "@/lib/session";
import type { CurrentUser } from "@/lib/types";
import { createContext, useContext, useEffect, useMemo, useState } from "react";

type AuthStatus = "loading" | "anonymous" | "authenticated";

type AuthContextValue = {
  status: AuthStatus;
  user: CurrentUser | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    let cancelled = false;
    const session = readSession();
    if (!session) {
      queueMicrotask(() => {
        if (!cancelled) {
          setUser(null);
          setStatus("anonymous");
        }
      });
      return () => {
        cancelled = true;
      };
    }
    currentUser()
      .then((next) => {
        if (cancelled) {
          return;
        }
        setUser(next);
        setStatus("authenticated");
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        clearSession();
        setUser(null);
        setStatus("anonymous");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    async login(email, password) {
      const auth = await loginRequest(email, password);
      setUser(auth.user);
      setStatus("authenticated");
    },
    async register(email, password, displayName) {
      const auth = await registerRequest(email, password, displayName);
      setUser(auth.user);
      setStatus("authenticated");
    },
    async logout() {
      await logoutRequest();
      setUser(null);
      setStatus("anonymous");
    },
  }), [status, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return value;
}
