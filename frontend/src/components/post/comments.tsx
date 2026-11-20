"use client";

import Link from "next/link";
import { useState } from "react";
import { Loader2, MessageCircle, SendHorizontal, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { UserAvatar } from "@/components/user/user-avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  useAddCommentMutation,
  useDeleteCommentMutation,
  useGetCommentsInfiniteQuery,
} from "@/store/api/memeApi";
import { errorMessage } from "@/lib/errors";
import { timeAgo } from "@/lib/format";

export function Comments({ postId, postIsMine }: { postId: string; postIsMine: boolean }) {
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useGetCommentsInfiniteQuery(postId);
  const [addComment, { isLoading: sending }] = useAddCommentMutation();
  const [deleteComment] = useDeleteCommentMutation();
  const [body, setBody] = useState("");
  const comments = data?.pages.flatMap((p) => p.items) ?? [];

  async function submit(e?: React.FormEvent) {
    e?.preventDefault();
    const text = body.trim();
    if (!text) return;
    try {
      await addComment({ postId, body: text }).unwrap();
      setBody("");
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <section className="rounded-2xl border-2 border-border bg-card p-4 shadow-brutal">
      <h2 className="flex items-center gap-2 text-xl font-bold">
        <MessageCircle className="size-5 text-bubblegum" /> Comments
      </h2>

      <form onSubmit={submit} className="mt-4 flex items-end gap-2">
        <Textarea
          value={body}
          onChange={(e) => setBody(e.target.value.slice(0, 500))}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          rows={1}
          placeholder="Add a spicy (but kind) comment..."
          className="min-h-11"
        />
        <Button type="submit" size="icon" disabled={sending || !body.trim()} aria-label="Send comment">
          {sending ? <Loader2 className="animate-spin" /> : <SendHorizontal />}
        </Button>
      </form>

      <ul className="mt-5 grid gap-4">
        {isLoading &&
          Array.from({ length: 3 }).map((_, i) => (
            <li key={i} className="flex gap-3">
              <Skeleton className="size-9 rounded-full" />
              <div className="grid flex-1 gap-1.5">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-4 w-3/4" />
              </div>
            </li>
          ))}
        {!isLoading && comments.length === 0 && (
          <li className="py-6 text-center text-sm text-muted-foreground">No comments yet. Say something funny first!</li>
        )}
        {comments.map((c) => (
          <li key={c.id} className="group flex gap-3">
            <Link href={`/u/${c.author.username}`}>
              <UserAvatar name={c.author.displayName} src={c.author.avatarUrl} />
            </Link>
            <div className="min-w-0 flex-1">
              <p className="text-sm">
                <Link href={`/u/${c.author.username}`} className="font-display font-bold hover:underline">
                  {c.author.displayName}
                </Link>{" "}
                <span className="text-xs text-muted-foreground">· {timeAgo(c.createdAt)}</span>
              </p>
              <p className="mt-0.5 text-sm whitespace-pre-wrap break-words">{c.body}</p>
            </div>
            {(c.mine || postIsMine) && (
              <Button
                variant="ghost"
                size="icon-sm"
                className="opacity-60 group-hover:opacity-100"
                aria-label="Delete comment"
                onClick={() =>
                  deleteComment({ id: c.id, postId })
                    .unwrap()
                    .catch((err) => toast.error(errorMessage(err)))
                }
              >
                <Trash2 className="text-bubblegum" />
              </Button>
            )}
          </li>
        ))}
      </ul>

      {hasNextPage && (
        <Button variant="outline" className="mt-4 w-full" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
          {isFetchingNextPage ? <Loader2 className="animate-spin" /> : null} Load older comments
        </Button>
      )}
    </section>
  );
}
