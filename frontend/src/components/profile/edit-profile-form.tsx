"use client";

import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Camera, Loader2, Lock, Save } from "lucide-react";
import { toast } from "sonner";

import { UserAvatar } from "@/components/user/user-avatar";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useUpdateProfileMutation } from "@/store/api/authApi";
import { usePresignMutation } from "@/store/api/memeApi";
import type { User } from "@/lib/types";
import { ACCEPTED_IMAGE_TYPES, putToS3, validateImage } from "@/lib/upload";
import { errorMessage } from "@/lib/errors";

const today = new Date().toISOString().slice(0, 10);

const schema = z.object({
  displayName: z.string().trim().min(1, "Display name is required").max(40),
  bio: z.string().max(160, "Max 160 characters"),
  birthday: z
    .string()
    .refine((v) => v === "" || v < today, "Birthday must be in the past"),
});
type Values = z.infer<typeof schema>;

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-2">
      <Label>{label}</Label>
      <div className="flex h-11 items-center gap-2 rounded-lg border-2 border-dashed border-border/50 bg-muted px-3 text-sm text-muted-foreground">
        <Lock className="size-4 text-bubblegum" />
        <span className="truncate">{value}</span>
      </div>
    </div>
  );
}

export function EditProfileForm({ user }: { user: User }) {
  const [updateProfile, { isLoading }] = useUpdateProfileMutation();
  const [presign] = usePresignMutation();
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: user.displayName,
      bio: user.bio ?? "",
      birthday: user.birthday ?? "",
    },
  });

  async function onSubmit(values: Values) {
    try {
      await updateProfile({
        displayName: values.displayName,
        bio: values.bio || null,
        birthday: values.birthday || null,
        avatarUrl: user.avatarUrl,
      }).unwrap();
      toast.success("Profile updated ✨");
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  async function onAvatar(file: File | undefined) {
    if (!file) return;
    const problem = validateImage(file, 2);
    if (problem) {
      toast.error(problem);
      return;
    }
    setUploading(true);
    try {
      const signed = await presign({ purpose: "AVATAR", contentType: file.type, contentLength: file.size }).unwrap();
      await putToS3(signed, file);
      const current = form.getValues();
      await updateProfile({
        displayName: current.displayName || user.displayName,
        bio: current.bio || null,
        birthday: current.birthday || null,
        avatarUrl: signed.publicUrl,
      }).unwrap();
      toast.success("New face, who dis? 📸");
    } catch (e) {
      toast.error(e instanceof Error && !("status" in e) ? e.message : errorMessage(e));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="grid gap-6">
      <div className="flex items-center gap-4">
        <UserAvatar name={user.displayName} src={user.avatarUrl} className="size-20 text-2xl" />
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="secondary" onClick={() => fileRef.current?.click()} disabled={uploading}>
            {uploading ? <Loader2 className="animate-spin" /> : <Camera />} Change photo
          </Button>
          {user.avatarUrl && (
            <Button
              type="button"
              variant="ghost"
              disabled={uploading || isLoading}
              onClick={() =>
                updateProfile({
                  displayName: user.displayName,
                  bio: user.bio,
                  birthday: user.birthday,
                  avatarUrl: null,
                })
                  .unwrap()
                  .catch((e) => toast.error(errorMessage(e)))
              }
            >
              Remove
            </Button>
          )}
        </div>
        <input
          ref={fileRef}
          type="file"
          accept={ACCEPTED_IMAGE_TYPES.join(",")}
          className="hidden"
          onChange={(e) => {
            onAvatar(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <ReadOnlyField label="Username (change it in Account)" value={`@${user.username}`} />
        <ReadOnlyField label="College (permanent)" value={user.collegeName} />
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
          <FormField
            control={form.control}
            name="displayName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Display name</FormLabel>
                <FormControl>
                  <Input maxLength={40} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="bio"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Bio</FormLabel>
                <FormControl>
                  <Textarea maxLength={160} placeholder="CSE '27 · professional bunker · part-time meme lord" {...field} />
                </FormControl>
                <FormDescription>{field.value.length}/160</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="birthday"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Birthday</FormLabel>
                <FormControl>
                  <Input type="date" max={today} className="w-fit" {...field} />
                </FormControl>
                <FormDescription>Others only see the day and month (and a 🎂 on the day).</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" disabled={isLoading} className="w-fit">
            {isLoading ? <Loader2 className="animate-spin" /> : <Save />} Save changes
          </Button>
        </form>
      </Form>
    </div>
  );
}
