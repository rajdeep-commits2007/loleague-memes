"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertTriangle, CheckCircle2, Loader2, Rocket, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useCheckUsernameQuery, useRegisterMutation } from "@/store/api/authApi";
import { useDebounced } from "@/lib/use-debounced";
import { errorMessage, fieldErrors } from "@/lib/errors";
import { CollegePicker } from "./college-picker";

const USERNAME = /^[A-Za-z0-9][A-Za-z0-9._]{1,18}[A-Za-z0-9]$/;

const schema = z
  .object({
    collegeId: z.string().min(1, "Pick your college"),
    username: z
      .string()
      .trim()
      .regex(USERNAME, "3-20 characters: letters, numbers, dot or underscore (not at the ends)"),
    password: z.string().min(8, "At least 8 characters").max(72, "At most 72 characters"),
    confirmPassword: z.string().min(1, "Confirm your password"),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords don't match",
  });
type Values = z.infer<typeof schema>;

type Availability = { state: "idle" | "checking" | "ok" | "taken"; reason?: string | null };

/** Live "is it already claimed?" check while typing (debounced, cached by RTK Query). */
function useUsernameAvailability(raw: string): Availability {
  const value = raw.trim();
  const debounced = useDebounced(value, 400);
  const valid = USERNAME.test(debounced);
  const { data, isFetching } = useCheckUsernameQuery(debounced, { skip: !valid });
  if (!USERNAME.test(value)) return { state: "idle" };
  if (value !== debounced || isFetching || !data || data.username !== debounced.toLowerCase()) {
    return { state: "checking" };
  }
  return { state: data.available ? "ok" : "taken", reason: data.reason };
}

export function RegisterForm() {
  const router = useRouter();
  const [register, { isLoading }] = useRegisterMutation();

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { collegeId: "", username: "", password: "", confirmPassword: "" },
  });
  const username = useWatch({ control: form.control, name: "username" });

  const availability = useUsernameAvailability(username);

  async function onSubmit(values: Values) {
    if (availability.state === "taken") {
      form.setError("username", { message: availability.reason ?? "Already claimed" });
      return;
    }
    try {
      const user = await register(values).unwrap();
      toast.success(`You're in, ${user.username}! 🎉`);
      router.replace("/feed");
      router.refresh();
    } catch (e) {
      const errors = fieldErrors(e);
      Object.entries(errors).forEach(([field, message]) =>
        form.setError(field as keyof Values, { message })
      );
      toast.error(errorMessage(e));
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-3xl font-extrabold">Join the league</CardTitle>
        <CardDescription>Takes 20 seconds. No email, no OTP drama.</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
            <FormField
              control={form.control}
              name="collegeId"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>College</FormLabel>
                  <CollegePicker value={field.value} onChange={field.onChange} invalid={!!fieldState.error} />
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Username</FormLabel>
                  <div className="relative">
                    <FormControl>
                      <Input
                        autoComplete="username"
                        autoCapitalize="none"
                        spellCheck={false}
                        placeholder="canteen.critic"
                        className="pr-10"
                        {...field}
                      />
                    </FormControl>
                    <span className="absolute top-1/2 right-3 -translate-y-1/2">
                      {availability.state === "checking" && <Loader2 className="size-4 animate-spin text-bubblegum" />}
                      {availability.state === "ok" && <CheckCircle2 className="size-5 text-bubblegum" />}
                      {availability.state === "taken" && <XCircle className="size-5 text-destructive" />}
                    </span>
                  </div>
                  {availability.state === "ok" && (
                    <p className="text-xs font-semibold text-grape">Nice, @{username.toLowerCase()} is yours to claim!</p>
                  )}
                  {availability.state === "taken" && (
                    <p className="text-xs font-semibold text-destructive">{availability.reason}</p>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Password</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="new-password" {...field} />
                  </FormControl>
                  <FormDescription>8+ characters. Not your roll number, please.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Confirm password</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="new-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex gap-2 rounded-lg border-2 border-dashed border-grape bg-grape-soft p-3 text-xs">
              <AlertTriangle className="size-4 shrink-0 text-bubblegum" />
              <p>
                <b>College and username are permanent.</b> You can change your display name, photo, bio and birthday
                later, but not these two.
              </p>
            </div>

            <Button type="submit" size="lg" disabled={isLoading || availability.state === "checking"}>
              {isLoading ? <Loader2 className="animate-spin" /> : <Rocket />} Create my account
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Already in?{" "}
              <Link href="/login" className="font-bold text-grape underline-offset-4 hover:underline">
                Log in
              </Link>
            </p>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
