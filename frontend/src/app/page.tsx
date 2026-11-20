import Link from "next/link";
import { ArrowRight, Coins, Crown, Gift, Laugh, MessagesSquare, School, Sparkles } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";

const features = [
  {
    icon: School,
    title: "Your campus only",
    body: "Pick your college once. You only see memes and chats from your own batchmates. No outsiders, no randoms.",
  },
  {
    icon: Crown,
    title: "Weekly leaderboard",
    body: "Likes and comments on your memes push you up. Every Monday the top 10 creators of each college get paid in coins.",
  },
  {
    icon: Gift,
    title: "Coins → real goodies",
    body: "Stack coins, unlock stickers, tees and hoodies delivered to your door. Cross 20K comments for a legend box.",
  },
  {
    icon: MessagesSquare,
    title: "The college adda",
    body: "One live room per college. Text, emojis and GIFs. Like a Discord server, minus the setup.",
  },
];

export default function LandingPage() {
  return (
    <main className="min-h-dvh">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-5 py-5">
        <Logo />
        <nav className="flex items-center gap-2">
          <Button asChild variant="ghost">
            <Link href="/login">Log in</Link>
          </Button>
          <Button asChild>
            <Link href="/register">Join free</Link>
          </Button>
        </nav>
      </header>

      <section className="mx-auto grid max-w-6xl items-center gap-10 px-5 pt-8 pb-16 md:grid-cols-[1.1fr_0.9fr] md:pt-16">
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border-2 border-ink bg-card px-3 py-1 font-display text-sm font-bold shadow-brutal-sm">
            <Sparkles className="size-4 text-bubblegum" /> Made for West Bengal campuses
          </span>
          <h1 className="mt-6 text-5xl leading-[0.95] font-extrabold sm:text-6xl md:text-7xl">
            Your college&apos;s
            <br />
            <span className="relative inline-block -rotate-1 rounded-xl border-2 border-ink bg-grape px-3 text-white shadow-brutal">
              meme league
            </span>
            <br />
            is now open.
          </h1>
          <p className="mt-6 max-w-lg text-lg text-muted-foreground">
            Post memes, roast the canteen, farm comments. The funniest creators on campus top the weekly board,
            earn coins and claim real goodies.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/register">
                Claim your username <ArrowRight />
              </Link>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <Link href="/login">I already have one</Link>
            </Button>
          </div>
        </div>

        {/* playful mock "meme card" stack */}
        <div className="relative mx-auto h-[380px] w-full max-w-sm" aria-hidden>
          <div className="absolute inset-x-6 top-6 h-72 rotate-6 rounded-2xl border-2 border-ink bg-bubblegum shadow-brutal" />
          <div className="absolute inset-x-3 top-3 h-72 -rotate-3 rounded-2xl border-2 border-ink bg-lime shadow-brutal" />
          <div className="absolute inset-x-0 top-0 rounded-2xl border-2 border-ink bg-card p-4 shadow-brutal-lg">
            <div className="flex items-center gap-2">
              <div className="size-8 rounded-full border-2 border-ink bg-grape" />
              <div>
                <p className="font-display text-sm font-bold">backbencher.babu</p>
                <p className="text-xs text-muted-foreground">Jadavpur University · 2m</p>
              </div>
            </div>
            <div className="mt-3 grid h-44 place-items-center rounded-xl border-2 border-ink bg-grape-soft">
              <Laugh className="size-20 text-bubblegum" />
            </div>
            <p className="mt-3 font-medium">When the prof says &ldquo;this won&apos;t come in exams&rdquo; 💀</p>
            <div className="mt-3 flex gap-4 text-sm font-bold">
              <span className="text-bubblegum">♥ 1.2K</span>
              <span>💬 348</span>
              <span className="ml-auto inline-flex items-center gap-1">
                <Coins className="size-4 text-bubblegum" /> #1 this week
              </span>
            </div>
          </div>
        </div>
      </section>

      <section className="border-y-2 border-ink bg-grape py-16 text-white">
        <div className="mx-auto max-w-6xl px-5">
          <h2 className="text-3xl font-extrabold sm:text-4xl">How LOLeague works</h2>
          <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {features.map(({ icon: Icon, title, body }) => (
              <div key={title} className="rounded-2xl border-2 border-ink bg-card p-5 text-card-foreground shadow-brutal">
                <div className="grid size-11 place-items-center rounded-xl border-2 border-ink bg-grape-soft">
                  <Icon className="size-5 text-bubblegum" />
                </div>
                <h3 className="mt-4 text-lg font-bold">{title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-5 py-16">
        <div className="grid gap-8 rounded-3xl border-2 border-ink bg-card p-8 shadow-brutal-lg md:grid-cols-2">
          <div>
            <h2 className="text-3xl font-extrabold">Weekly coin drop</h2>
            <p className="mt-3 text-muted-foreground">
              Score = likes + 2 × comments received this week (self-likes don&apos;t count, nice try). Every Monday the
              top 10 in each college get paid.
            </p>
          </div>
          <ol className="grid grid-cols-5 gap-2 self-center">
            {[10, 5, 3, 2, 1].map((coins, i) => (
              <li
                key={i}
                className="flex flex-col items-center rounded-xl border-2 border-ink bg-grape-soft px-1 py-3 text-center"
              >
                <span className="font-display text-xs font-bold text-muted-foreground">#{i + 1}</span>
                <span className="font-display text-xl font-extrabold">{coins}</span>
                <Coins className="size-4 text-bubblegum" />
              </li>
            ))}
          </ol>
        </div>
      </section>

      <footer className="border-t-2 border-ink py-8 text-center text-sm text-muted-foreground">
        © {new Date().getFullYear()} LOLeague · Be funny, not cruel.
      </footer>
    </main>
  );
}
