"use client";

import Link from "next/link";
import { useState } from "react";
import { Coins, Crown, Heart, Medal, MessageCircle, Trophy } from "lucide-react";

import { EmptyState, PageHeader } from "@/components/layout/app-shell";
import { UserAvatar } from "@/components/user/user-avatar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useGetLeaderboardQuery } from "@/store/api/memeApi";
import { useAppSelector } from "@/store/hooks";
import type { LeaderboardEntry } from "@/lib/types";
import { formatCount, formatDay } from "@/lib/format";
import { cn } from "@/lib/utils";

const PODIUM_STYLE = [
  "bg-lime text-[#1a1033] sm:order-2 sm:-mt-6",
  "bg-card sm:order-1",
  "bg-bubblegum text-white sm:order-3",
];

function Podium({ entries }: { entries: LeaderboardEntry[] }) {
  return (
    <div className="grid gap-4 sm:grid-cols-3 sm:items-end">
      {entries.slice(0, 3).map((e, i) => (
        <Link
          key={e.userId}
          href={`/u/${e.username}`}
          className={cn(
            "flex flex-col items-center rounded-2xl border-2 border-ink p-5 text-center shadow-brutal brutal-hover",
            PODIUM_STYLE[i]
          )}
        >
          <div className="relative">
            {i === 0 && <Crown className="absolute -top-6 left-1/2 size-7 -translate-x-1/2 fill-lime text-[#1a1033]" />}
            <UserAvatar name={e.displayName} src={e.avatarUrl} className="size-16 text-xl" />
          </div>
          <p className="mt-3 font-display text-3xl font-extrabold">#{e.rank}</p>
          <p className="max-w-full truncate font-display font-bold">{e.displayName}</p>
          <p className="text-xs opacity-80">@{e.username}</p>
          <div className="mt-3 flex gap-3 text-sm font-bold">
            <span className="inline-flex items-center gap-1">
              <Heart className="size-4" /> {formatCount(e.likes)}
            </span>
            <span className="inline-flex items-center gap-1">
              <MessageCircle className="size-4" /> {formatCount(e.comments)}
            </span>
          </div>
          <Badge variant="default" className="mt-3">
            +{e.coins} coins
          </Badge>
        </Link>
      ))}
    </div>
  );
}

function Row({ e, highlight }: { e: LeaderboardEntry; highlight?: boolean }) {
  return (
    <li
      className={cn(
        "flex items-center gap-3 rounded-xl border-2 border-border bg-card px-3 py-2.5",
        highlight && "border-grape bg-grape-soft"
      )}
    >
      <span className="w-8 text-center font-display text-lg font-extrabold text-grape">#{e.rank}</span>
      <UserAvatar name={e.displayName} src={e.avatarUrl} />
      <Link href={`/u/${e.username}`} className="min-w-0 flex-1">
        <p className="truncate font-display font-bold">{e.displayName}</p>
        <p className="truncate text-xs text-muted-foreground">
          {formatCount(e.likes)} likes · {formatCount(e.comments)} comments · {formatCount(e.score)} pts
        </p>
      </Link>
      {e.coins > 0 && (
        <span className="inline-flex items-center gap-1 font-display font-bold">
          <Coins className="size-4 text-bubblegum" />+{e.coins}
        </span>
      )}
    </li>
  );
}

export default function LeaderboardPage() {
  const [week, setWeek] = useState<"current" | "previous">("current");
  const { data, isLoading } = useGetLeaderboardQuery(week, { pollingInterval: week === "current" ? 60_000 : 0 });
  const me = useAppSelector((s) => s.auth.user);

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            <Trophy className="size-8 text-bubblegum" /> Leaderboard
          </span>
        }
        subtitle={me ? `Top 10 creators at ${me.collegeName}` : "Top 10 creators at your college"}
        action={
          <Tabs value={week} onValueChange={(v) => setWeek(v as "current" | "previous")}>
            <TabsList>
              <TabsTrigger value="current">This week</TabsTrigger>
              <TabsTrigger value="previous">Last week</TabsTrigger>
            </TabsList>
          </Tabs>
        }
      />

      {data && (
        <div className="mb-6 flex flex-wrap items-center gap-2 rounded-xl border-2 border-ink bg-grape p-3 text-sm text-white shadow-brutal-sm">
          <Medal className="size-5 text-lime" />
          <span className="font-bold">
            {formatDay(data.weekStart)} – {formatDay(data.weekEnd)}
          </span>
          <span className="opacity-80">
            {data.finalized ? "· Final. Coins paid!" : "· Live, updates every minute. Payout Monday 12:05 AM."}
          </span>
          <span className="ml-auto inline-flex flex-wrap gap-1">
            {data.rewards.slice(0, 5).map((c, i) => (
              <Badge key={i} variant="lime">
                #{i + 1}: {c}
              </Badge>
            ))}
          </span>
        </div>
      )}

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-56 rounded-2xl" />
          ))}
        </div>
      )}

      {data && data.entries.length === 0 && (
        <EmptyState
          icon={Trophy}
          title="The throne is empty"
          body="Nobody has scored this week yet. Post a meme and get your friends commenting to grab #1."
        />
      )}

      {data && data.entries.length > 0 && (
        <>
          <Podium entries={data.entries} />
          <ol className="mt-6 grid gap-2">
            {data.entries.slice(3).map((e) => (
              <Row key={e.userId} e={e} highlight={e.userId === me?.id} />
            ))}
          </ol>
        </>
      )}

      {data?.me && data.me.rank > 10 && (
        <div className="mt-6">
          <p className="mb-2 font-display text-sm font-bold text-muted-foreground">Your spot</p>
          <Row e={data.me} highlight />
        </div>
      )}

      <p className="mt-8 text-center text-xs text-muted-foreground">
        Score = likes + 2 × comments your memes received this week. Your own likes and comments don&apos;t count.
      </p>
    </div>
  );
}
