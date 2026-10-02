export const THEME_STORAGE_KEY = "marketpulse-theme";

export type ThemeChoice = "light" | "dark";

export function resolveTheme(value: string | null): ThemeChoice {
  return value === "dark" ? "dark" : "light";
}

export function readStoredTheme(): ThemeChoice {
  try {
    return resolveTheme(localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return "light";
  }
}

export function applyTheme(theme: ThemeChoice) {
  document.documentElement.setAttribute("data-theme", theme);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // The visible theme still changes when storage is unavailable.
  }
}

export const themeInitScript = `(function(){try{var t=localStorage.getItem("${THEME_STORAGE_KEY}");document.documentElement.setAttribute("data-theme",t==="dark"?"dark":"light");}catch(e){document.documentElement.setAttribute("data-theme","light");}})();`;
