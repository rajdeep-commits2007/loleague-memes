import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="grid min-h-dvh place-items-center px-5 text-center">
      <div>
        <p className="font-display text-8xl font-extrabold text-grape">404</p>
        <h1 className="mt-2 text-2xl font-bold">This page bunked class.</h1>
        <p className="mt-2 text-muted-foreground">It&apos;s not here. Maybe it&apos;s in the canteen?</p>
        <Button asChild className="mt-6" size="lg">
          <Link href="/">Take me home</Link>
        </Button>
      </div>
    </main>
  );
}
