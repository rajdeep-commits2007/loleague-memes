import type { Metadata, Viewport } from "next";
import "@fontsource-variable/bricolage-grotesque";
import "@fontsource-variable/dm-sans";
import "./globals.css";
import { ClerkProvider } from "@clerk/nextjs";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: {
    default: "LOLeague - campus memes, ranked",
    template: "%s · LOLeague",
  },
  description:
    "Post memes with your college, climb the weekly leaderboard, earn coins and claim real goodies. Made for West Bengal campuses.",
  applicationName: "LOLeague",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#6b3cf6" },
    { media: "(prefers-color-scheme: dark)", color: "#120a24" },
  ],
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <ClerkProvider signInUrl="/login" signUpUrl="/register">
          <Providers>{children}</Providers>
        </ClerkProvider>
      </body>
    </html>
  );
}
