import type { Metadata } from "next";
import { Suspense } from "react";
import { ClerkAuthCard } from "@/components/auth/clerk-auth-card";

export const metadata: Metadata = { title: "Join" };

export default function RegisterPage() {
  return (
    <Suspense>
      <ClerkAuthCard mode="sign-up" />
    </Suspense>
  );
}
