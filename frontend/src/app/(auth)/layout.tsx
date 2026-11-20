import { Logo } from "@/components/brand/logo";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col px-5 py-6">
        <Logo />
        <div className="flex flex-1 items-center justify-center py-10">
          <div className="w-full max-w-md">{children}</div>
        </div>
      </div>
      <aside className="relative hidden overflow-hidden border-l-2 border-ink bg-grape lg:block">
        <div className="absolute inset-0 grid place-items-center p-12">
          <div className="max-w-md -rotate-2 rounded-3xl border-2 border-ink bg-card p-8 shadow-brutal-lg">
            <p className="font-display text-4xl leading-tight font-extrabold">
              &ldquo;I came for attendance. I stayed for the{" "}
              <span className="rounded-lg bg-lime px-2 text-[#1a1033]">memes</span>.&rdquo;
            </p>
            <p className="mt-4 text-sm text-muted-foreground">- every final year student, probably</p>
          </div>
        </div>
        <div className="absolute -right-10 -bottom-10 size-56 rounded-full border-2 border-ink bg-bubblegum" />
        <div className="absolute -top-8 -left-8 size-40 rounded-full border-2 border-ink bg-lime" />
      </aside>
    </main>
  );
}
