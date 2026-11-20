"use client";

import { Images, Settings, UserCog } from "lucide-react";

import { AccountSettings } from "@/components/profile/account-settings";
import { EditProfileForm } from "@/components/profile/edit-profile-form";
import { MemeGrid } from "@/components/profile/meme-grid";
import { ProfileHeader } from "@/components/profile/profile-header";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useGetMeQuery } from "@/store/api/authApi";

export default function MyProfilePage() {
  const { data: me } = useGetMeQuery();

  if (!me) return <Skeleton className="mx-auto h-64 max-w-3xl rounded-2xl" />;

  const [, month, day] = me.birthday?.split("-").map(Number) ?? [];

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      <ProfileHeader
        displayName={me.displayName}
        username={me.username}
        collegeName={me.collegeName}
        bio={me.bio}
        avatarUrl={me.avatarUrl}
        birthdayDay={day ?? null}
        birthdayMonth={month ?? null}
        createdAt={me.createdAt}
      />

      <Tabs defaultValue="memes">
        <TabsList className="w-full sm:w-fit">
          <TabsTrigger value="memes">
            <Images /> My memes
          </TabsTrigger>
          <TabsTrigger value="edit">
            <Settings /> Edit profile
          </TabsTrigger>
          <TabsTrigger value="account">
            <UserCog /> Account
          </TabsTrigger>
        </TabsList>
        <TabsContent value="memes">
          <MemeGrid username={me.username} emptyText="Your meme legacy starts with one post." />
        </TabsContent>
        <TabsContent value="edit">
          <Card>
            <CardContent>
              <EditProfileForm key={me.avatarUrl ?? "none"} user={me} />
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="account">
          <Card>
            <CardContent>
              <AccountSettings key={me.username} user={me} />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
