"use client";

import Link from "next/link";
import { Fragment, useState } from "react";
import { Loader2, MessageCircle, Pencil, Reply, SendHorizontal, SmilePlus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { MentionTextarea } from "@/components/post/mention-textarea";
import { UserAvatar } from "@/components/user/user-avatar";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useAddCommentMutation,
  useDeleteCommentMutation,
  useEditCommentMutation,
  useGetCommentsInfiniteQuery,
  useGetRepliesInfiniteQuery,
  useReactToCommentMutation,
} from "@/store/api/memeApi";
import { errorMessage } from "@/lib/errors";
import { timeAgo } from "@/lib/format";
import { COMMENT_REACTIONS, type Comment } from "@/lib/types";
import { cn } from "@/lib/utils";

export function Comments({ postId, postIsMine }: { postId: string; postIsMine: boolean }) {
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useGetCommentsInfiniteQuery(postId);
  const comments = data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <section className="rounded-2xl border-2 border-border bg-card p-4 shadow-brutal">
      <h2 className="flex items-center gap-2 text-xl font-bold">
        <MessageCircle className="size-5 text-bubblegum" /> Comments
      </h2>

      <Composer postId={postId} className="mt-4" placeholder="Add a spicy (but kind) comment... type @ to tag" />

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
          <Thread key={c.id} comment={c} postIsMine={postIsMine} />
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

/** A top-level comment, its replies (loaded on demand) and the reply box. */
function Thread({ comment, postIsMine }: { comment: Comment; postIsMine: boolean }) {
  const [showReplies, setShowReplies] = useState(false);
  const [replyTo, setReplyTo] = useState<Comment | null>(null);

  function startReply(target: Comment) {
    setShowReplies(true);
    setReplyTo(target);
  }

  return (
    <li className="grid gap-3">
      <CommentItem comment={comment} postIsMine={postIsMine} onReply={startReply} />
      <div className="ml-12 grid gap-3 border-l-2 border-border pl-3 empty:hidden">
        {comment.replyCount > 0 && (
          <button
            type="button"
            className="w-fit text-xs font-bold text-muted-foreground hover:text-foreground"
            onClick={() => setShowReplies((s) => !s)}
          >
            {showReplies ? "Hide replies" : `View ${comment.replyCount} ${comment.replyCount === 1 ? "reply" : "replies"}`}
          </button>
        )}
        {showReplies && comment.replyCount > 0 && (
          <Replies parentId={comment.id} postIsMine={postIsMine} onReply={startReply} />
        )}
        {replyTo && (
          <Composer
            key={replyTo.id}
            postId={comment.postId}
            parentId={comment.id}
            initial={replyTo.mine ? "" : `@${replyTo.author.username} `}
            placeholder={`Reply to ${replyTo.author.displayName}...`}
            autoFocus
            onDone={() => setReplyTo(null)}
            onCancel={() => setReplyTo(null)}
          />
        )}
      </div>
    </li>
  );
}

function Replies({
  parentId,
  postIsMine,
  onReply,
}: {
  parentId: string;
  postIsMine: boolean;
  onReply: (c: Comment) => void;
}) {
  const { data, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useGetRepliesInfiniteQuery(parentId);
  const replies = data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <>
      {isLoading && <Skeleton className="h-10 w-3/4" />}
      {replies.map((r) => (
        <CommentItem key={r.id} comment={r} postIsMine={postIsMine} onReply={onReply} small />
      ))}
      {hasNextPage && (
        <button
          type="button"
          className="w-fit text-xs font-bold text-muted-foreground hover:text-foreground"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
        >
          {isFetchingNextPage ? "Loading..." : "View more replies"}
        </button>
      )}
    </>
  );
}

function CommentItem({
  comment: c,
  postIsMine,
  onReply,
  small,
}: {
  comment: Comment;
  postIsMine: boolean;
  onReply: (c: Comment) => void;
  small?: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(c.body);
  const [editComment, { isLoading: saving }] = useEditCommentMutation();
  const [deleteComment] = useDeleteCommentMutation();
  const [react] = useReactToCommentMutation();
  const [pickerOpen, setPickerOpen] = useState(false);

  async function saveEdit() {
    const text = draft.trim();
    if (!text) return;
    if (text === c.body) return setEditing(false);
    try {
      await editComment({ comment: c, body: text }).unwrap();
      setEditing(false);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  function toggle(emoji: string) {
    setPickerOpen(false);
    react({ comment: c, emoji })
      .unwrap()
      .catch((err) => toast.error(errorMessage(err)));
  }

  return (
    <div className="group flex gap-3">
      <Link href={`/u/${c.author.username}`} className="shrink-0">
        <UserAvatar name={c.author.displayName} src={c.author.avatarUrl} className={small ? "size-7" : undefined} />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="text-sm">
          <Link href={`/u/${c.author.username}`} className="font-display font-bold hover:underline">
            {c.author.displayName}
          </Link>{" "}
          <span className="text-xs text-muted-foreground">
            · {timeAgo(c.createdAt)}
            {c.editedAt && " · edited"}
          </span>
        </p>

        {editing ? (
          <div className="mt-1 grid gap-2">
            <MentionTextarea
              value={draft}
              onChange={setDraft}
              onSubmit={saveEdit}
              onCancel={() => {
                setDraft(c.body);
                setEditing(false);
              }}
              autoFocus
            />
            <div className="flex gap-2">
              <Button size="sm" onClick={saveEdit} disabled={saving || !draft.trim()}>
                {saving ? <Loader2 className="animate-spin" /> : null} Save
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setDraft(c.body);
                  setEditing(false);
                }}
              >
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <p className="mt-0.5 text-sm whitespace-pre-wrap break-words">
            <CommentBody body={c.body} mentions={c.mentions} />
          </p>
        )}

        {!editing && (
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            {c.reactions.map((r) => (
              <button
                key={r.emoji}
                type="button"
                onClick={() => toggle(r.emoji)}
                aria-pressed={r.mine}
                className={cn(
                  "flex h-7 items-center gap-1 rounded-full border-2 px-2 text-xs font-bold transition",
                  r.mine ? "border-grape bg-grape-soft" : "border-border hover:bg-accent"
                )}
              >
                <span className="text-sm leading-none">{r.emoji}</span> {r.count}
              </button>
            ))}
            <Popover open={pickerOpen} onOpenChange={setPickerOpen}>
              <PopoverTrigger asChild>
                <Button variant="ghost" size="icon-sm" className="size-7" aria-label="React">
                  <SmilePlus className="size-4" />
                </Button>
              </PopoverTrigger>
              <PopoverContent side="top" align="start" className="flex w-auto gap-1 p-1.5">
                {COMMENT_REACTIONS.map((e) => (
                  <button
                    key={e}
                    type="button"
                    onClick={() => toggle(e)}
                    className="grid size-9 place-items-center rounded-lg text-xl transition hover:scale-125 hover:bg-accent"
                    aria-label={`React ${e}`}
                  >
                    {e}
                  </button>
                ))}
              </PopoverContent>
            </Popover>
            <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={() => onReply(c)}>
              <Reply className="size-3.5" /> Reply
            </Button>
            {c.mine && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={() => {
                  setDraft(c.body);
                  setEditing(true);
                }}
              >
                <Pencil className="size-3.5" /> Edit
              </Button>
            )}
            {(c.mine || postIsMine) && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs opacity-60 group-hover:opacity-100"
                aria-label="Delete comment"
                onClick={() => {
                  if (!c.parentId && c.replyCount > 0 && !confirm("Delete this comment and its replies?")) return;
                  deleteComment({ id: c.id, postId: c.postId, parentId: c.parentId, replyCount: c.replyCount })
                    .unwrap()
                    .catch((err) => toast.error(errorMessage(err)));
                }}
              >
                <Trash2 className="size-3.5 text-bubblegum" />
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/** Renders `@username` as a profile link, but only for mentions the server resolved to a real batchmate. */
function CommentBody({ body, mentions }: { body: string; mentions: string[] }) {
  if (mentions.length === 0) return <>{body}</>;
  const known = new Set(mentions.map((m) => m.toLowerCase()));
  const parts = body.split(/((?<![A-Za-z0-9_.])@[A-Za-z0-9][A-Za-z0-9._]{1,18}[A-Za-z0-9])/g);
  return (
    <>
      {parts.map((part, i) => {
        const name = part.startsWith("@") ? part.slice(1) : null;
        if (name && known.has(name.toLowerCase())) {
          return (
            <Link key={i} href={`/u/${name}`} className="font-bold text-grape hover:underline">
              {part}
            </Link>
          );
        }
        return <Fragment key={i}>{part}</Fragment>;
      })}
    </>
  );
}

function Composer({
  postId,
  parentId,
  initial = "",
  placeholder,
  autoFocus,
  className,
  onDone,
  onCancel,
}: {
  postId: string;
  parentId?: string;
  initial?: string;
  placeholder?: string;
  autoFocus?: boolean;
  className?: string;
  onDone?: () => void;
  onCancel?: () => void;
}) {
  const [addComment, { isLoading: sending }] = useAddCommentMutation();
  const [body, setBody] = useState(initial);

  async function submit(e?: React.FormEvent) {
    e?.preventDefault();
    const text = body.trim();
    if (!text) return;
    try {
      await addComment({ postId, body: text, parentId }).unwrap();
      setBody("");
      onDone?.();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <form onSubmit={submit} className={cn("flex items-end gap-2", className)}>
      <MentionTextarea
        value={body}
        onChange={setBody}
        onSubmit={submit}
        onCancel={onCancel}
        placeholder={placeholder}
        autoFocus={autoFocus}
      />
      <Button type="submit" size="icon" disabled={sending || !body.trim()} aria-label="Send comment">
        {sending ? <Loader2 className="animate-spin" /> : <SendHorizontal />}
      </Button>
    </form>
  );
}
