"use client";

import { applyTheme, type ThemeChoice } from "@/lib/theme";
import { useSyncExternalStore } from "react";

const themeEvent = "marketpulse-theme";

function subscribe(onStoreChange: () => void) {
  window.addEventListener(themeEvent, onStoreChange);
  return () => window.removeEventListener(themeEvent, onStoreChange);
}

function currentTheme(): ThemeChoice {
  return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
}

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, currentTheme, () => "light" satisfies ThemeChoice);
  const dark = theme === "dark";

  function choose(next: ThemeChoice) {
    applyTheme(next);
    window.dispatchEvent(new Event(themeEvent));
  }

  return (
    <button
      type="button"
      className="theme-switch"
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      aria-pressed={dark}
      onClick={() => choose(dark ? "light" : "dark")}
    >
      <span className="theme-icon-light" aria-hidden="true">☀️</span>
      <span className="theme-icon-dark" aria-hidden="true">🌙</span>
    </button>
  );
}
