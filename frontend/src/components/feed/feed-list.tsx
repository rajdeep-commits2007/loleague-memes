"use client";

import { Fragment, useEffect } from "react";
import { Flame, Laugh, Loader2, Plus, Sparkles } from "lucide-react";

import { EmptyState } from "@/components/layout/app-shell";
import { AdCard } from "@/components/feed/ad-card";
import { CreatePostDialog } from "@/components/feed/create-post-dialog";
import { PostSkeleton } from "@/components/feed/feed-skeleton";
import { PostCard } from "@/components/feed/post-card";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useGetAdsQuery, useGetFeedInfiniteQuery, type FeedSort } from "@/store/api/memeApi";
import { useInView } from "@/lib/use-in-view";
import { errorMessage } from "@/lib/errors";

/** Insert a sponsored card after every AD_EVERY memes. */
const AD_EVERY = 5;

export function FeedList({ sort, onSortChange }: { sort: FeedSort; onSortChange: (s: FeedSort) => void }) {
  const { data, isLoading, isError, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useGetFeedInfiniteQuery({ sort });
  const { data: ads } = useGetAdsQuery(4);
  const { ref: sentinel, inView } = useInView<HTMLDivElement>({ rootMargin: "800px" });

  useEffect(() => {
    if (inView && hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [inView, hasNextPage, isFetchingNextPage, fetchNextPage]);

  const posts = data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className="mx-auto grid max-w-xl gap-5">
      <div className="flex items-center justify-between gap-3">
        <Tabs value={sort} onValueChange={(v) => onSortChange(v as FeedSort)}>
          <TabsList>
            <TabsTrigger value="new">
              <Sparkles /> Fresh
            </TabsTrigger>
            <TabsTrigger value="top">
              <Flame /> Top this week
            </TabsTrigger>
          </TabsList>
        </Tabs>
        <CreatePostDialog
          trigger={
            <Button>
              <Plus /> Post
            </Button>
          }
        />
      </div>

      {isLoading && (
        <>
          <PostSkeleton />
          <PostSkeleton />
        </>
      )}

      {isError && (
        <EmptyState
          icon={Laugh}
          title="The feed tripped over a banana"
          body={errorMessage(error)}
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      )}

      {!isLoading && !isError && posts.length === 0 && (
        <EmptyState
          icon={Laugh}
          title={sort === "top" ? "No bangers this week yet" : "Your campus is suspiciously quiet"}
          body="Be the first legend to post a meme. First mover advantage on the leaderboard is real."
          action={
            <CreatePostDialog
              trigger={
                <Button size="lg">
                  <Plus /> Post the first meme
                </Button>
              }
            />
          }
        />
      )}

      {posts.map((post, i) => {
        const showAd = ads && ads.length > 0 && (i + 1) % AD_EVERY === 0;
        const ad = showAd ? ads[Math.floor(i / AD_EVERY) % ads.length] : null;
        return (
          <Fragment key={post.id}>
            <PostCard post={post} />
            {ad && <AdCard ad={ad} />}
          </Fragment>
        );
      })}

      <div ref={sentinel} />
      {isFetchingNextPage && (
        <div className="flex justify-center py-4">
          <Loader2 className="size-6 animate-spin text-bubblegum" />
        </div>
      )}
      {!hasNextPage && posts.length > 0 && (
        <p className="py-6 text-center font-display text-sm font-bold text-muted-foreground">
          You reached the end. Go touch grass 🌱 (or post a meme)
        </p>
      )}
    </div>
  );
}
