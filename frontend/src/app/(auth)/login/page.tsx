import type { Metadata } from "next";
import { Suspense } from "react";
import { ClerkAuthCard } from "@/components/auth/clerk-auth-card";

export const metadata: Metadata = { title: "Log in" };

export default function LoginPage() {
  return (
    <Suspense>
      <ClerkAuthCard mode="sign-in" />
    </Suspense>
  );
}
