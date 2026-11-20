import type { Metadata } from "next";
import { Suspense } from "react";
import { WelcomeFlow } from "./welcome-flow";

export const metadata: Metadata = { title: "Almost there" };

export default function WelcomePage() {
  return (
    <Suspense>
      <WelcomeFlow />
    </Suspense>
  );
}
