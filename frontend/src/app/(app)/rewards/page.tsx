"use client";

import { Coins, Gift, MessageCircle, Package, PartyPopper, TrendingUp, Trophy } from "lucide-react";

import { EmptyState, PageHeader } from "@/components/layout/app-shell";
import { ClaimDialog } from "@/components/rewards/claim-dialog";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetClaimsQuery, useGetGoodiesQuery, useGetWalletQuery } from "@/store/api/memeApi";
import { formatCoins, formatCount, timeAgo } from "@/lib/format";

const MILESTONE = 20_000;

const STATUS_VARIANT = {
  PENDING: "secondary",
  SHIPPED: "default",
  DELIVERED: "lime",
  CANCELLED: "outline",
} as const;

function Stat({ icon: Icon, label, value }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string }) {
  return (
    <div className="rounded-xl border-2 border-ink bg-card p-4 text-card-foreground shadow-brutal-sm">
      <Icon className="size-5 text-bubblegum" />
      <p className="mt-2 font-display text-2xl font-extrabold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}

export default function RewardsPage() {
  const { data: wallet, isLoading: walletLoading } = useGetWalletQuery();
  const { data: goodies, isLoading: goodiesLoading } = useGetGoodiesQuery();
  const { data: claims } = useGetClaimsQuery();
  const milestonePct = wallet ? Math.min(100, (wallet.commentsReceived / MILESTONE) * 100) : 0;

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            <Gift className="size-8 text-bubblegum" /> Rewards
          </span>
        }
        subtitle="Top the weekly leaderboard, earn coins, claim real goodies."
      />

      {/* wallet */}
      <section className="rounded-2xl border-2 border-ink bg-grape p-5 text-white shadow-brutal">
        {walletLoading || !wallet ? (
          <Skeleton className="h-28 bg-white/20" />
        ) : (
          <>
            <div className="flex flex-wrap items-end gap-4">
              <div>
                <p className="text-sm opacity-85">Your balance</p>
                <p className="flex items-center gap-2 font-display text-5xl font-extrabold">
                  <Coins className="size-10 text-lime" />
                  {formatCoins(wallet.balance)}
                </p>
              </div>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat icon={TrendingUp} label="Lifetime coins" value={formatCoins(wallet.lifetimeEarned)} />
              <Stat icon={MessageCircle} label="Comments received" value={formatCount(wallet.commentsReceived)} />
              <Stat icon={PartyPopper} label="Likes received" value={formatCount(wallet.likesReceived)} />
              <Stat icon={Package} label="Goodies claimed" value={String(claims?.filter((c) => c.status !== "CANCELLED").length ?? 0)} />
            </div>
            <div className="mt-5">
              <div className="flex justify-between text-xs font-bold">
                <span>Legend Box milestone</span>
                <span>
                  {formatCount(wallet.commentsReceived)} / {formatCount(MILESTONE)} comments
                </span>
              </div>
              <div className="mt-1.5 h-4 overflow-hidden rounded-full border-2 border-ink bg-card">
                <div className="h-full rounded-full bg-lime transition-all" style={{ width: `${milestonePct}%` }} />
              </div>
            </div>
          </>
        )}
      </section>

      {/* goodies */}
      <h2 className="mt-10 mb-4 text-2xl font-extrabold">Goodies shop</h2>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {goodiesLoading && Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-64 rounded-2xl" />)}
        {goodies?.map((g) => (
          <Card key={g.id} className="gap-3">
            <CardHeader>
              <div className="mb-2 grid h-28 place-items-center overflow-hidden rounded-xl border-2 border-ink bg-grape-soft">
                {g.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={g.imageUrl} alt={g.name} className="h-full w-full object-cover" />
                ) : (
                  <Gift className="size-12 text-bubblegum" />
                )}
              </div>
              <CardTitle>{g.name}</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-1 flex-col gap-3">
              <p className="text-sm text-muted-foreground">{g.description}</p>
              <div className="flex flex-wrap gap-1.5">
                {g.coinCost > 0 ? (
                  <Badge>
                    <Coins /> {formatCoins(g.coinCost)}
                  </Badge>
                ) : (
                  <Badge variant="lime">Free</Badge>
                )}
                {g.minCommentsReceived > 0 && (
                  <Badge variant="secondary">
                    <MessageCircle /> {formatCount(g.minCommentsReceived)} comments
                  </Badge>
                )}
                {g.stock !== null && <Badge variant="outline">{g.stock} left</Badge>}
              </div>
              <div className="mt-auto">
                <ClaimDialog goodie={g} />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* history */}
      <div className="mt-10 grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Package className="size-5 text-bubblegum" /> My claims
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!claims?.length ? (
              <p className="text-sm text-muted-foreground">Nothing claimed yet.</p>
            ) : (
              <ul className="grid gap-2">
                {claims.map((c) => (
                  <li key={c.id} className="flex items-center gap-3 rounded-lg border-2 border-border/30 p-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-display font-bold">{c.goodieName}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {c.city} {c.pincode} · {timeAgo(c.createdAt)}
                        {c.adminNote ? ` · ${c.adminNote}` : ""}
                      </p>
                    </div>
                    <Badge variant={STATUS_VARIANT[c.status]}>{c.status.toLowerCase()}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Coins className="size-5 text-bubblegum" /> Coin history
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!wallet?.transactions.length ? (
              <EmptyState icon={Trophy} title="No coins yet" body="Finish in your college's weekly top 10 to earn your first coins." />
            ) : (
              <ul className="grid gap-2">
                {wallet.transactions.map((t) => (
                  <li key={t.id} className="flex items-center gap-3 rounded-lg border-2 border-border/30 p-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{t.note ?? t.kind}</p>
                      <p className="text-xs text-muted-foreground">{timeAgo(t.createdAt)}</p>
                    </div>
                    <span className={t.amount >= 0 ? "font-display font-bold text-grape" : "font-display font-bold text-destructive"}>
                      {t.amount >= 0 ? "+" : ""}
                      {formatCoins(t.amount)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
