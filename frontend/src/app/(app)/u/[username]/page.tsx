"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Settings, UserX } from "lucide-react";

import { EmptyState } from "@/components/layout/app-shell";
import { MemeGrid } from "@/components/profile/meme-grid";
import { ProfileHeader } from "@/components/profile/profile-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetProfileQuery } from "@/store/api/authApi";
import { useAppSelector } from "@/store/hooks";

export default function UserProfilePage() {
  const { username } = useParams<{ username: string }>();
  const { data: profile, isLoading, isError } = useGetProfileQuery(username);
  const me = useAppSelector((s) => s.auth.user);
  const isMe = me?.username === username?.toLowerCase();

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      {isLoading && <Skeleton className="h-64 rounded-2xl" />}
      {isError && (
        <EmptyState
          icon={UserX}
          title="Student not found"
          body="Either this username doesn't exist or they study at another college."
          action={
            <Button asChild>
              <Link href="/feed">Back to feed</Link>
            </Button>
          }
        />
      )}
      {profile && (
        <>
          <ProfileHeader
            {...profile}
            action={
              isMe ? (
                <Button asChild variant="outline" size="sm">
                  <Link href="/profile">
                    <Settings className="text-bubblegum" /> Edit
                  </Link>
                </Button>
              ) : undefined
            }
          />
          <h2 className="text-xl font-bold">Memes</h2>
          <MemeGrid username={profile.username} emptyText={`${profile.displayName} hasn't posted yet. Tag them in the adda!`} />
        </>
      )}
    </div>
  );
}
