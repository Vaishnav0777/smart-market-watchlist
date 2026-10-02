"use client";

import { readStoredTheme } from "@/lib/theme";
import { useLayoutEffect } from "react";

export function ThemeSync() {
  useLayoutEffect(() => {
    document.documentElement.setAttribute("data-theme", readStoredTheme());
  }, []);
  return null;
}
