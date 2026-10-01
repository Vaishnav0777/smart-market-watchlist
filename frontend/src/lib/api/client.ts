import { clearSession, readSession, writeSession } from "@/lib/session";
import type { ApiErrorBody, AuthResponse } from "@/lib/types";

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;

  constructor(message: string, status: number, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  auth?: boolean;
  retry?: boolean;
};

function apiBase(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
}

let refreshInFlight: Promise<boolean> | null = null;

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers();
  headers.set("Accept", "application/json");
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.auth !== false) {
    const session = readSession();
    if (session) {
      headers.set("Authorization", `Bearer ${session.accessToken}`);
    }
  }

  const response = await fetch(`${apiBase()}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    cache: "no-store",
  });

  if (response.status === 401 && options.auth !== false && options.retry !== false && !isAuthPath(path)) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return apiRequest<T>(path, { ...options, retry: false });
    }
    clearSession();
    throw new ApiError("Your session has expired. Sign in again.", 401);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = await readBody(response);
  if (!response.ok) {
    const error = payload as ApiErrorBody | null;
    throw new ApiError(error?.message || "The request failed.", response.status, error?.fieldErrors ?? undefined);
  }
  return payload as T;
}

export async function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = refreshOnce().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function refreshOnce(): Promise<boolean> {
  const session = readSession();
  if (!session) {
    return false;
  }
  try {
    const next = await apiRequest<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
      auth: false,
      retry: false,
      body: { refreshToken: session.refreshToken },
    });
    writeSession(next);
    return true;
  } catch {
    clearSession();
    return false;
  }
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

function isAuthPath(path: string): boolean {
  return path.startsWith("/api/v1/auth/login")
    || path.startsWith("/api/v1/auth/register")
    || path.startsWith("/api/v1/auth/refresh")
    || path.startsWith("/api/v1/auth/logout");
}
