"use client";

import { useClerk } from "@clerk/nextjs";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { Coins, Gift, Home, LogOut, MessagesSquare, Moon, Sun, Trophy, UserRound } from "lucide-react";

import { Logo } from "@/components/brand/logo";
import { UserAvatar } from "@/components/user/user-avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetMeQuery, useLogoutMutation } from "@/store/api/authApi";
import { useGetWalletQuery } from "@/store/api/memeApi";
import { formatCoins } from "@/lib/format";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/feed", label: "Feed", short: "Feed", icon: Home },
  { href: "/leaderboard", label: "Leaderboard", short: "Ranks", icon: Trophy },
  { href: "/chat", label: "Adda", short: "Adda", icon: MessagesSquare },
  { href: "/rewards", label: "Rewards", short: "Rewards", icon: Gift },
  { href: "/profile", label: "Me", short: "Me", icon: UserRound },
];

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { data: me, isLoading } = useGetMeQuery();
  const { data: wallet } = useGetWalletQuery(undefined, { skip: !me });
  const [logout] = useLogoutMutation();
  const { signOut } = useClerk();
  const { resolvedTheme, setTheme } = useTheme();

  async function onLogout() {
    await logout().unwrap().catch(() => undefined);
    // End the Clerk session too, otherwise /login would sign straight back in.
    await signOut().catch(() => undefined);
    router.replace("/login");
    router.refresh();
  }

  return (
    <div className="min-h-dvh">
      {/* top bar */}
      <header className="sticky top-0 z-40 border-b-2 border-ink bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4">
          <Logo href="/feed" />
          {me && (
            <span className="ml-2 hidden max-w-[280px] truncate rounded-full border-2 border-ink bg-grape px-3 py-0.5 font-display text-xs font-bold text-white md:inline-block">
              {me.collegeName}
            </span>
          )}
          <div className="ml-auto flex items-center gap-2">
            <Link
              href="/rewards"
              className="inline-flex items-center gap-1.5 rounded-full border-2 border-ink bg-card px-3 py-1 font-display text-sm font-bold shadow-brutal-sm"
              title="Your coins"
            >
              <Coins className="size-4 text-bubblegum" />
              {wallet ? formatCoins(wallet.balance) : "–"}
            </Link>
            {isLoading || !me ? (
              <Skeleton className="size-9 rounded-full" />
            ) : (
              <DropdownMenu>
                <DropdownMenuTrigger className="rounded-full outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50">
                  <UserAvatar name={me.displayName} src={me.avatarUrl} />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>
                    <p className="truncate">{me.displayName}</p>
                    <p className="truncate font-sans text-xs font-normal text-muted-foreground">@{me.username}</p>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => router.push("/profile")}>
                    <UserRound /> Profile & settings
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}>
                    {resolvedTheme === "dark" ? <Sun /> : <Moon />} {resolvedTheme === "dark" ? "Light" : "Dark"} mode
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onSelect={onLogout}>
                    <LogOut className="text-destructive" /> Log out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </div>
        </div>
      </header>

      <div className="mx-auto flex max-w-6xl gap-6 px-4 pt-6 pb-28 md:pb-10">
        {/* desktop side nav */}
        <nav className="sticky top-22 hidden h-fit w-52 shrink-0 flex-col gap-1.5 md:flex">
          {NAV.map(({ href, label, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex items-center gap-3 rounded-xl border-2 px-3 py-2.5 font-display font-bold transition-all",
                isActive(pathname, href)
                  ? "border-ink bg-grape text-white shadow-brutal-sm"
                  : "border-transparent hover:border-ink hover:bg-card"
              )}
            >
              <Icon className={cn("size-5", isActive(pathname, href) ? "text-lime" : "text-bubblegum")} />
              {label}
            </Link>
          ))}
        </nav>

        <main className="min-w-0 flex-1">{children}</main>
      </div>

      {/* mobile bottom nav */}
      <nav className="fixed inset-x-3 bottom-3 z-40 flex justify-around rounded-2xl border-2 border-ink bg-card p-1.5 shadow-brutal md:hidden">
        {NAV.map(({ href, short, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            className={cn(
              "flex flex-1 flex-col items-center gap-0.5 rounded-xl py-1.5 font-display text-[11px] font-bold",
              isActive(pathname, href) ? "bg-grape text-white" : "text-foreground"
            )}
          >
            <Icon className={cn("size-5", isActive(pathname, href) ? "text-lime" : "text-bubblegum")} />
            {short}
          </Link>
        ))}
      </nav>
    </div>
  );
}

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-3xl font-extrabold sm:text-4xl">{title}</h1>
        {subtitle && <p className="mt-1 text-muted-foreground">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function EmptyState({ icon: Icon, title, body, action }: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  body?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-2xl border-2 border-dashed border-grape bg-card/60 px-6 py-12 text-center">
      <div className="grid size-14 place-items-center rounded-2xl border-2 border-ink bg-grape-soft">
        <Icon className="size-7 text-bubblegum" />
      </div>
      <h3 className="mt-4 text-xl font-bold">{title}</h3>
      {body && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

