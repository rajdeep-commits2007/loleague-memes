"use client";

import Link from "next/link";
import { useState } from "react";
import { Heart, MessageCircle, MoreHorizontal, Share2, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { UserAvatar } from "@/components/user/user-avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useDeletePostMutation, useToggleLikeMutation } from "@/store/api/memeApi";
import type { Post } from "@/lib/types";
import { formatCount, timeAgo } from "@/lib/format";
import { errorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";

export function PostCard({ post, linkToDetail = true }: { post: Post; linkToDetail?: boolean }) {
  const [toggleLike] = useToggleLikeMutation();
  const [deletePost, { isLoading: deleting }] = useDeletePostMutation();
  const [popKey, setPopKey] = useState(0);

  function onLike() {
    setPopKey((k) => k + 1);
    toggleLike({ id: post.id, like: !post.likedByMe })
      .unwrap()
      .catch((e) => toast.error(errorMessage(e)));
  }

  async function onShare() {
    const url = `${window.location.origin}/post/${post.id}`;
    try {
      if (navigator.share) await navigator.share({ title: "LOLeague meme", url });
      else {
        await navigator.clipboard.writeText(url);
        toast.success("Link copied. Only your college can open it 😉");
      }
    } catch {
      /* user cancelled */
    }
  }

  async function onDelete() {
    try {
      await deletePost(post.id).unwrap();
      toast.success("Meme deleted");
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  const image = (
    // eslint-disable-next-line @next/next/no-img-element -- user uploads live on S3, sized by the browser
    <img
      src={post.imageUrl}
      alt={post.caption ?? `Meme by ${post.author.displayName}`}
      loading="lazy"
      className="max-h-[560px] w-full bg-grape-soft object-contain"
    />
  );

  return (
    <article className="overflow-hidden rounded-2xl border-2 border-border bg-card shadow-brutal">
      <header className="flex items-center gap-3 px-4 py-3">
        <Link href={`/u/${post.author.username}`} className="flex min-w-0 items-center gap-3">
          <UserAvatar name={post.author.displayName} src={post.author.avatarUrl} />
          <div className="min-w-0">
            <p className="truncate font-display font-bold leading-tight">{post.author.displayName}</p>
            <p className="truncate text-xs text-muted-foreground">
              @{post.author.username} · {timeAgo(post.createdAt)}
            </p>
          </div>
        </Link>
        {post.mine && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" className="ml-auto" aria-label="More">
                <MoreHorizontal className="text-bubblegum" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem variant="destructive" disabled={deleting} onSelect={onDelete}>
                <Trash2 className="text-destructive" /> Delete meme
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </header>

      <div className="border-y-2 border-border">
        {linkToDetail ? <Link href={`/post/${post.id}`}>{image}</Link> : image}
      </div>

      {post.caption && <p className="px-4 pt-3 whitespace-pre-wrap break-words">{post.caption}</p>}

      <footer className="flex items-center gap-1 px-2 py-2">
        <Button
          variant="ghost"
          onClick={onLike}
          aria-pressed={post.likedByMe}
          aria-label={post.likedByMe ? "Unlike" : "Like"}
          className="gap-1.5"
        >
          <Heart
            key={popKey}
            className={cn(
              "size-5 text-bubblegum",
              post.likedByMe && "fill-bubblegum",
              popKey > 0 && "animate-pop"
            )}
          />
          {formatCount(post.likeCount)}
        </Button>
        <Button variant="ghost" asChild className="gap-1.5">
          <Link href={`/post/${post.id}`} aria-label="Comments">
            <MessageCircle className="size-5 text-bubblegum" />
            {formatCount(post.commentCount)}
          </Link>
        </Button>
        <Button variant="ghost" size="icon" className="ml-auto" onClick={onShare} aria-label="Share">
          <Share2 className="size-5 text-bubblegum" />
        </Button>
      </footer>
    </article>
  );
}
