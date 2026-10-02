import { AuthProvider } from "@/components/auth-provider";
import { ThemeSync } from "@/components/theme-sync";
import { themeInitScript } from "@/lib/theme";
import type { Metadata } from "next";
import { IBM_Plex_Sans } from "next/font/google";
import Script from "next/script";
import "./globals.css";

const outfit = IBM_Plex_Sans({
  variable: "--font-outfit",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

export const metadata: Metadata = {
  title: "MarketPulse",
  description: "Know what changed.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${outfit.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col">
        <Script id="marketpulse-theme" strategy="beforeInteractive">
          {themeInitScript}
        </Script>
        <ThemeSync />
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
