"use client";

import { useState } from "react";
import { AtSign, Loader2, Lock } from "lucide-react";
import { toast } from "sonner";

import { AvailabilityIcon } from "@/app/(auth)/welcome/welcome-flow";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/errors";
import type { User } from "@/lib/types";
import { USERNAME, USERNAME_HINT, useUsernameAvailability } from "@/lib/use-username-availability";
import { useChangeUsernameMutation } from "@/store/api/authApi";

function Locked({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="grid gap-2">
      <Label>{label}</Label>
      <div className="flex h-11 items-center gap-2 rounded-lg border-2 border-dashed border-border/50 bg-muted px-3 text-sm text-muted-foreground">
        <Lock className="size-4 text-bubblegum" />
        <span className="truncate">{value}</span>
      </div>
      <p className="text-xs text-muted-foreground">{hint}</p>
    </div>
  );
}

/** Username (unique, changeable), plus the parts of your identity that are locked: email and college. */
export function AccountSettings({ user }: { user: User }) {
  const [username, setUsername] = useState(user.username);
  const [changeUsername, { isLoading }] = useChangeUsernameMutation();
  const availability = useUsernameAvailability(username, user.username);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const value = username.trim();
    if (!USERNAME.test(value)) return toast.error(USERNAME_HINT);
    if (availability.state === "taken") return toast.error(availability.reason ?? "Already claimed");
    try {
      const updated = await changeUsername(value).unwrap();
      toast.success(`You're now @${updated.username}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <div className="grid gap-6">
      <form onSubmit={submit} className="grid gap-2" noValidate>
        <Label htmlFor="username">Username</Label>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <AtSign className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="username"
              autoCapitalize="none"
              spellCheck={false}
              className="pr-10 pl-9"
              value={username}
              onChange={(e) => setUsername(e.target.value.slice(0, 20))}
            />
            <AvailabilityIcon state={availability.state} />
          </div>
          <Button type="submit" disabled={isLoading || availability.state !== "ok"}>
            {isLoading ? <Loader2 className="animate-spin" /> : null} Save
          </Button>
        </div>
        {availability.state === "taken" ? (
          <p className="text-xs font-semibold text-destructive">{availability.reason}</p>
        ) : (
          <p className="text-xs text-muted-foreground">
            Unique across LOLeague. Your old username is freed up for someone else, and links to your old @ stop
            working. You can change it once a week.
          </p>
        )}
      </form>

      <Locked
        label="Email"
        value={user.email ?? "Not linked"}
        hint="One account per email address. It's verified by OTP and can't be changed."
      />
      <Locked label="College" value={user.collegeName} hint="Your college is permanent." />
    </div>
  );
}
