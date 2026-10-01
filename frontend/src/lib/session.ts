import type { AuthResponse, CurrentUser } from "@/lib/types";

const ACCESS = "smw.accessToken";
const REFRESH = "smw.refreshToken";
const USER = "smw.user";

export type StoredSession = {
  accessToken: string;
  refreshToken: string;
  user: CurrentUser;
};

export function readSession(): StoredSession | null {
  if (typeof window === "undefined") {
    return null;
  }
  const accessToken = window.sessionStorage.getItem(ACCESS);
  const refreshToken = window.sessionStorage.getItem(REFRESH);
  const userJson = window.sessionStorage.getItem(USER);
  if (!accessToken || !refreshToken || !userJson) {
    return null;
  }
  try {
    const user = JSON.parse(userJson) as CurrentUser;
    if (!user?.id || !user.email) {
      return null;
    }
    return { accessToken, refreshToken, user };
  } catch {
    return null;
  }
}

export function writeSession(auth: AuthResponse): void {
  window.sessionStorage.setItem(ACCESS, auth.accessToken);
  window.sessionStorage.setItem(REFRESH, auth.refreshToken);
  window.sessionStorage.setItem(USER, JSON.stringify(auth.user));
}

export function writeUser(user: CurrentUser): void {
  window.sessionStorage.setItem(USER, JSON.stringify(user));
}

export function clearSession(): void {
  window.sessionStorage.removeItem(ACCESS);
  window.sessionStorage.removeItem(REFRESH);
  window.sessionStorage.removeItem(USER);
}
