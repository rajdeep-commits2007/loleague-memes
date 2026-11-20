"use client";

import Link from "next/link";
import { Heart, ImageOff, Loader2, MessageCircle } from "lucide-react";

import { EmptyState } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetUserPostsInfiniteQuery } from "@/store/api/memeApi";
import { formatCount } from "@/lib/format";

export function MemeGrid({ username, emptyText }: { username: string; emptyText: string }) {
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useGetUserPostsInfiniteQuery({ username });
  const posts = data?.pages.flatMap((p) => p.items) ?? [];

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="aspect-square rounded-xl" />
        ))}
      </div>
    );
  }
  if (posts.length === 0) return <EmptyState icon={ImageOff} title="No memes yet" body={emptyText} />;

  return (
    <>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {posts.map((p) => (
          <Link
            key={p.id}
            href={`/post/${p.id}`}
            className="group relative aspect-square overflow-hidden rounded-xl border-2 border-border bg-grape-soft shadow-brutal-sm"
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={p.imageUrl} alt={p.caption ?? "Meme"} loading="lazy" className="size-full object-cover" />
            <div className="absolute inset-x-0 bottom-0 flex gap-3 bg-gradient-to-t from-black/70 to-transparent p-2 text-xs font-bold text-white opacity-90">
              <span className="inline-flex items-center gap-1">
                <Heart className="size-3.5 fill-bubblegum text-bubblegum" /> {formatCount(p.likeCount)}
              </span>
              <span className="inline-flex items-center gap-1">
                <MessageCircle className="size-3.5" /> {formatCount(p.commentCount)}
              </span>
            </div>
          </Link>
        ))}
      </div>
      {hasNextPage && (
        <Button variant="outline" className="mt-4 w-full" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
          {isFetchingNextPage && <Loader2 className="animate-spin" />} Load more
        </Button>
      )}
    </>
  );
}
