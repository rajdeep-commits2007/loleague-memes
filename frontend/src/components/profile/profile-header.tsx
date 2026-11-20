import { Cake, CalendarDays, School } from "lucide-react";

import { UserAvatar } from "@/components/user/user-avatar";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function ProfileHeader({
  displayName,
  username,
  collegeName,
  bio,
  avatarUrl,
  birthdayDay,
  birthdayMonth,
  createdAt,
  action,
}: {
  displayName: string;
  username: string;
  collegeName: string;
  bio: string | null;
  avatarUrl: string | null;
  birthdayDay?: number | null;
  birthdayMonth?: number | null;
  createdAt: string;
  action?: React.ReactNode;
}) {
  const today = new Date();
  const isBirthday = birthdayDay === today.getDate() && birthdayMonth === today.getMonth() + 1;

  return (
    <section className="overflow-hidden rounded-2xl border-2 border-ink bg-card shadow-brutal">
      <div className="h-24 border-b-2 border-ink bg-grape bg-[radial-gradient(circle_at_20%_50%,var(--bubblegum)_0_10%,transparent_11%),radial-gradient(circle_at_85%_30%,var(--lime)_0_8%,transparent_9%)]" />
      <div className="px-5 pb-5">
        <div className="-mt-10 flex items-end justify-between gap-3">
          <UserAvatar name={displayName} src={avatarUrl} className="size-20 border-[3px] text-2xl" />
          {action}
        </div>
        <h1 className="mt-3 text-2xl font-extrabold">
          {displayName} {isBirthday && <span title="Birthday today!">🎂</span>}
        </h1>
        <p className="text-sm text-muted-foreground">@{username}</p>
        {bio && <p className="mt-3 whitespace-pre-wrap">{bio}</p>}
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <School className="size-4 text-bubblegum" /> {collegeName}
          </span>
          {birthdayDay && birthdayMonth && (
            <span className="inline-flex items-center gap-1.5">
              <Cake className="size-4 text-bubblegum" /> {birthdayDay} {MONTHS[birthdayMonth - 1]}
            </span>
          )}
          <span className="inline-flex items-center gap-1.5">
            <CalendarDays className="size-4 text-bubblegum" /> Joined{" "}
            {new Date(createdAt).toLocaleDateString("en-IN", { month: "short", year: "numeric" })}
          </span>
        </div>
      </div>
    </section>
  );
}
