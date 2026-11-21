"use client";

import { SignIn, SignUp, useAuth } from "@clerk/nextjs";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";

import { clerkAppearance } from "@/components/auth/clerk-appearance";

export function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/feed";
}

/**
 * Clerk's prebuilt card: email + OTP code, Google or GitHub. Configure the methods (email required, verified
 * with a code; phone number off) in the Clerk dashboard. After Clerk is done we always land on /welcome, which swaps the
 * Clerk session for LOLeague cookies and runs onboarding if needed.
 */
export function ClerkAuthCard({ mode }: { mode: "sign-in" | "sign-up" }) {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const welcome = `/welcome?next=${encodeURIComponent(next)}`;
  const { isLoaded, isSignedIn } = useAuth();

  // Still signed in to Clerk (e.g. our 30-day cookie expired): skip the card and re-exchange.
  useEffect(() => {
    if (isLoaded && isSignedIn) router.replace(welcome);
  }, [isLoaded, isSignedIn, router, welcome]);

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="font-display text-3xl font-extrabold">
          {mode === "sign-in" ? "Welcome back 👋" : "Join the league"}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {mode === "sign-in"
            ? "Your campus memes missed you."
            : "Sign up with your email, Google or GitHub. We'll verify your email with an OTP."}
        </p>
      </div>
      {mode === "sign-in" ? (
        <SignIn
          routing="hash"
          appearance={clerkAppearance}
          signUpUrl="/register"
          forceRedirectUrl={welcome}
          signUpForceRedirectUrl={welcome}
        />
      ) : (
        <SignUp
          routing="hash"
          appearance={clerkAppearance}
          signInUrl="/login"
          forceRedirectUrl={welcome}
          signInForceRedirectUrl={welcome}
        />
      )}
    </div>
  );
}
