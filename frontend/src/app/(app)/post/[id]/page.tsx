"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Ghost } from "lucide-react";

import { EmptyState } from "@/components/layout/app-shell";
import { PostSkeleton } from "@/components/feed/feed-skeleton";
import { PostCard } from "@/components/feed/post-card";
import { Comments } from "@/components/post/comments";
import { Button } from "@/components/ui/button";
import { useGetPostQuery } from "@/store/api/memeApi";

export default function PostPage() {
  const { id } = useParams<{ id: string }>();
  const { data: post, isLoading, isError } = useGetPostQuery(id);

  return (
    <div className="mx-auto grid max-w-xl gap-5">
      <Button variant="ghost" asChild className="w-fit">
        <Link href="/feed">
          <ArrowLeft className="text-bubblegum" /> Back to feed
        </Link>
      </Button>
      {isLoading && <PostSkeleton />}
      {isError && (
        <EmptyState
          icon={Ghost}
          title="Meme not found"
          body="It was deleted, or it belongs to another college."
          action={
            <Button asChild>
              <Link href="/feed">Go to feed</Link>
            </Button>
          }
        />
      )}
      {post && (
        <>
          <PostCard post={post} linkToDetail={false} />
          <Comments postId={post.id} postIsMine={post.mine} />
        </>
      )}
    </div>
  );
}
