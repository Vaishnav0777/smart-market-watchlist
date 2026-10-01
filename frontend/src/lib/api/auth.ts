import { apiRequest } from "@/lib/api/client";
import { clearSession, setAccessToken } from "@/lib/session";
import type { AuthResponse, CurrentUser } from "@/lib/types";

export async function register(email: string, password: string, displayName: string): Promise<AuthResponse> {
  const auth = await apiRequest<AuthResponse>("/api/v1/auth/register", {
    method: "POST",
    auth: false,
    body: { email, password, displayName },
  });
  setAccessToken(auth.accessToken);
  return auth;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const auth = await apiRequest<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email, password },
  });
  setAccessToken(auth.accessToken);
  return auth;
}

export async function currentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export async function logout(): Promise<void> {
  try {
    await apiRequest<void>("/api/v1/auth/logout", {
      method: "POST",
      auth: false,
      retry: false,
    });
  } finally {
    clearSession();
  }
}
