"use client";

import { useAuth, useClerk, useUser } from "@clerk/nextjs";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, KeyRound, Loader2, Phone, Rocket, XCircle } from "lucide-react";
import { toast } from "sonner";

import { safeNext } from "@/components/auth/clerk-auth-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { errorMessage } from "@/lib/errors";
import type { SessionResult } from "@/lib/types";
import { USERNAME, USERNAME_HINT, useUsernameAvailability } from "@/lib/use-username-availability";
import { useClaimAccountMutation, useClerkSessionMutation, useOnboardMutation } from "@/store/api/authApi";
import { CollegePicker } from "../register/college-picker";

type Step =
  | { kind: "loading" }
  | { kind: "phone" }
  | { kind: "onboard"; phone: string; suggestedName: string | null }
  | { kind: "blocked"; message: string };

/**
 * Runs right after Clerk sign-in / sign-up:
 *   1. swap the Clerk session for LOLeague cookies (existing account -> straight to the feed),
 *   2. if Clerk has no verified phone yet (Google / GitHub sign-up), add one with an OTP,
 *   3. otherwise pick a college + username, or claim a pre-Clerk account with its old password.
 */
export function WelcomeFlow() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const { isLoaded, isSignedIn, getToken } = useAuth();
  const { signOut } = useClerk();
  const [exchange] = useClerkSessionMutation();
  const [step, setStep] = useState<Step>({ kind: "loading" });
  const started = useRef(false);

  const finish = useCallback(
    (result: SessionResult) => {
      if (result.status === "SIGNED_IN") {
        toast.success(`Welcome, ${result.user.displayName}!`);
        router.replace(next);
        router.refresh();
      } else if (result.status === "PHONE_REQUIRED") {
        setStep({ kind: "phone" });
      } else {
        setStep({ kind: "onboard", phone: result.phone, suggestedName: result.suggestedName });
      }
    },
    [next, router]
  );

  const run = useCallback(async () => {
    setStep({ kind: "loading" });
    try {
      const token = await getToken({ skipCache: true });
      if (!token) throw new Error("No Clerk session");
      finish(await exchange(token).unwrap());
    } catch (e) {
      setStep({ kind: "blocked", message: errorMessage(e) });
    }
  }, [exchange, finish, getToken]);

  useEffect(() => {
    if (!isLoaded) return;
    if (!isSignedIn) {
      router.replace(`/login?next=${encodeURIComponent(next)}`);
      return;
    }
    if (started.current) return;
    started.current = true;
    run();
  }, [isLoaded, isSignedIn, next, router, run]);

  async function switchAccount() {
    await signOut();
    router.replace("/login");
  }

  if (step.kind === "loading") {
    return (
      <div className="grid place-items-center py-20">
        <Loader2 className="size-8 animate-spin text-bubblegum" />
      </div>
    );
  }

  if (step.kind === "blocked") {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-2xl font-extrabold">
            <AlertTriangle className="size-6 text-bubblegum" /> Hold up
          </CardTitle>
          <CardDescription>{step.message}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button onClick={run}>Try again</Button>
          <Button variant="outline" onClick={switchAccount}>
            Use another sign-in
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (step.kind === "phone") {
    return <PhoneStep onVerified={run} onCancel={switchAccount} />;
  }

  return (
    <OnboardStep
      phone={step.phone}
      suggestedName={step.suggestedName}
      getToken={() => getToken({ skipCache: true })}
      onDone={finish}
    />
  );
}

/** Adds a phone number to the Clerk user and verifies it with an SMS code. */
function PhoneStep({ onVerified, onCancel }: { onVerified: () => void; onCancel: () => void }) {
  const { user } = useUser();
  const [phone, setPhone] = useState("+91");
  const [code, setCode] = useState("");
  const [pending, setPending] = useState<Awaited<ReturnType<NonNullable<typeof user>["createPhoneNumber"]>> | null>(
    null
  );
  const [busy, setBusy] = useState(false);

  async function sendCode(e: React.FormEvent) {
    e.preventDefault();
    if (!user) return;
    const number = phone.replace(/[\s-]/g, "");
    if (!/^\+\d{10,15}$/.test(number)) {
      toast.error("Enter your number with the country code, like +919876543210");
      return;
    }
    setBusy(true);
    try {
      const existing = user.phoneNumbers.find((p) => p.phoneNumber === number);
      const resource = existing ?? (await user.createPhoneNumber({ phoneNumber: number }));
      await resource.prepareVerification();
      setPending(resource);
      toast.success("OTP sent!");
    } catch (err) {
      toast.error(clerkError(err));
    } finally {
      setBusy(false);
    }
  }

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    if (!user || !pending) return;
    setBusy(true);
    try {
      const verified = await pending.attemptVerification({ code: code.trim() });
      if (verified.verification.status !== "verified") throw new Error("That code didn't work");
      await user.update({ primaryPhoneNumberId: verified.id });
      onVerified();
    } catch (err) {
      toast.error(clerkError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-2xl font-extrabold">
          <Phone className="size-6 text-bubblegum" /> Verify your phone
        </CardTitle>
        <CardDescription>
          Every LOLeague account is tied to one phone number. We&apos;ll text you a one-time code.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {!pending ? (
          <form onSubmit={sendCode} className="grid gap-3">
            <Label htmlFor="phone">Phone number</Label>
            <Input
              id="phone"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+919876543210"
            />
            <Button type="submit" size="lg" disabled={busy}>
              {busy ? <Loader2 className="animate-spin" /> : null} Send OTP
            </Button>
            <Button type="button" variant="ghost" onClick={onCancel}>
              Use another sign-in
            </Button>
          </form>
        ) : (
          <form onSubmit={verify} className="grid gap-3">
            <Label htmlFor="code">Code sent to {pending.phoneNumber}</Label>
            <Input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              placeholder="123456"
              autoFocus
            />
            <Button type="submit" size="lg" disabled={busy || code.length < 6}>
              {busy ? <Loader2 className="animate-spin" /> : <CheckCircle2 />} Verify
            </Button>
            <Button type="button" variant="ghost" onClick={() => setPending(null)} disabled={busy}>
              Change number
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

function OnboardStep({
  phone,
  suggestedName,
  getToken,
  onDone,
}: {
  phone: string;
  suggestedName: string | null;
  getToken: () => Promise<string | null>;
  onDone: (r: SessionResult) => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-3xl font-extrabold">Almost in!</CardTitle>
        <CardDescription>
          Phone verified: <b>{phone}</b>
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="new">
          <TabsList className="w-full">
            <TabsTrigger value="new">I&apos;m new</TabsTrigger>
            <TabsTrigger value="claim">I had an account</TabsTrigger>
          </TabsList>
          <TabsContent value="new">
            <NewAccountForm suggestedName={suggestedName} getToken={getToken} onDone={onDone} />
          </TabsContent>
          <TabsContent value="claim">
            <ClaimForm getToken={getToken} onDone={onDone} />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}

function NewAccountForm({
  suggestedName,
  getToken,
  onDone,
}: {
  suggestedName: string | null;
  getToken: () => Promise<string | null>;
  onDone: (r: SessionResult) => void;
}) {
  const [onboard, { isLoading }] = useOnboardMutation();
  const [collegeId, setCollegeId] = useState("");
  const [username, setUsername] = useState(suggestedName && USERNAME.test(suggestedName) ? suggestedName : "");
  const [collegeError, setCollegeError] = useState(false);
  const availability = useUsernameAvailability(username);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!collegeId) return setCollegeError(true);
    if (!USERNAME.test(username.trim())) return toast.error(USERNAME_HINT);
    if (availability.state === "taken") return toast.error(availability.reason ?? "Already claimed");
    try {
      const token = await getToken();
      if (!token) throw new Error("Your sign-in expired. Refresh the page.");
      onDone(await onboard({ token, collegeId, username: username.trim() }).unwrap());
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <form onSubmit={submit} className="mt-4 grid gap-4" noValidate>
      <div className="grid gap-2">
        <Label>College</Label>
        <CollegePicker
          value={collegeId}
          onChange={(v: string) => {
            setCollegeId(v);
            setCollegeError(false);
          }}
          invalid={collegeError}
        />
        {collegeError && <p className="text-xs font-semibold text-destructive">Pick your college</p>}
      </div>

      <div className="grid gap-2">
        <Label htmlFor="username">Username</Label>
        <div className="relative">
          <Input
            id="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="canteen.critic"
            className="pr-10"
            value={username}
            onChange={(e) => setUsername(e.target.value.slice(0, 20))}
          />
          <AvailabilityIcon state={availability.state} />
        </div>
        {availability.state === "ok" && (
          <p className="text-xs font-semibold text-grape">Nice, @{username.trim().toLowerCase()} is yours to claim!</p>
        )}
        {availability.state === "taken" && (
          <p className="text-xs font-semibold text-destructive">{availability.reason}</p>
        )}
        {availability.state === "idle" && <p className="text-xs text-muted-foreground">{USERNAME_HINT}</p>}
      </div>

      <div className="flex gap-2 rounded-lg border-2 border-dashed border-grape bg-grape-soft p-3 text-xs">
        <AlertTriangle className="size-4 shrink-0 text-bubblegum" />
        <p>
          <b>Your college is permanent.</b> You can change your username later (it stays unique), plus your display
          name, photo, bio and birthday.
        </p>
      </div>

      <Button type="submit" size="lg" disabled={isLoading || availability.state === "checking"}>
        {isLoading ? <Loader2 className="animate-spin" /> : <Rocket />} Create my account
      </Button>
    </form>
  );
}

/** For students who signed up before Clerk: link the old account (memes, coins) to this phone, once. */
function ClaimForm({
  getToken,
  onDone,
}: {
  getToken: () => Promise<string | null>;
  onDone: (r: SessionResult) => void;
}) {
  const [claim, { isLoading }] = useClaimAccountMutation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!username.trim() || !password) return;
    try {
      const token = await getToken();
      if (!token) throw new Error("Your sign-in expired. Refresh the page.");
      onDone(await claim({ token, username: username.trim(), password }).unwrap());
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <form onSubmit={submit} className="mt-4 grid gap-4" noValidate>
      <p className="text-sm text-muted-foreground">
        Enter your old LOLeague username and password once. Your memes, coins and college move over, and from now on
        you&apos;ll sign in with this phone / Google / GitHub instead.
      </p>
      <div className="grid gap-2">
        <Label htmlFor="old-username">Old username</Label>
        <Input
          id="old-username"
          autoComplete="username"
          autoCapitalize="none"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="old-password">Old password</Label>
        <Input
          id="old-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      <Button type="submit" size="lg" disabled={isLoading || !username.trim() || !password}>
        {isLoading ? <Loader2 className="animate-spin" /> : <KeyRound />} Claim my account
      </Button>
    </form>
  );
}

export function AvailabilityIcon({ state }: { state: string }) {
  return (
    <span className="absolute top-1/2 right-3 -translate-y-1/2">
      {state === "checking" && <Loader2 className="size-4 animate-spin text-bubblegum" />}
      {state === "ok" && <CheckCircle2 className="size-5 text-bubblegum" />}
      {state === "taken" && <XCircle className="size-5 text-destructive" />}
    </span>
  );
}

function clerkError(err: unknown): string {
  const e = err as { errors?: Array<{ longMessage?: string; message?: string }> };
  return e?.errors?.[0]?.longMessage ?? e?.errors?.[0]?.message ?? errorMessage(err);
}
