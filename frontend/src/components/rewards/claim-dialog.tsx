"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Gift, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useClaimGoodieMutation } from "@/store/api/memeApi";
import type { ClaimAddress, Goodie } from "@/lib/types";
import { errorMessage } from "@/lib/errors";
import { formatCoins } from "@/lib/format";

const schema = z.object({
  fullName: z.string().trim().min(2, "Full name is required").max(80),
  phone: z.string().trim().regex(/^[6-9]\d{9}$/, "10-digit Indian mobile number"),
  addressLine1: z.string().trim().min(5, "House / street is required").max(160),
  addressLine2: z.string().trim().max(160).optional(),
  city: z.string().trim().min(2, "City is required").max(60),
  state: z.string().trim().min(2, "State is required").max(60),
  pincode: z.string().trim().regex(/^[1-9]\d{5}$/, "6-digit PIN code"),
});

const ADDRESS_KEY = "loleague.lastAddress";

function lastAddress(): ClaimAddress {
  const empty = { fullName: "", phone: "", addressLine1: "", addressLine2: "", city: "", state: "West Bengal", pincode: "" };
  try {
    const raw = localStorage.getItem(ADDRESS_KEY);
    return raw ? { ...empty, ...(JSON.parse(raw) as ClaimAddress) } : empty;
  } catch {
    return empty;
  }
}

const FIELDS: Array<{ name: keyof ClaimAddress; label: string; placeholder?: string; inputMode?: "numeric" | "tel" }> = [
  { name: "fullName", label: "Full name" },
  { name: "phone", label: "Mobile", placeholder: "98XXXXXXXX", inputMode: "tel" },
  { name: "addressLine1", label: "House / street", placeholder: "Flat 3B, 12 College Street" },
  { name: "addressLine2", label: "Landmark (optional)", placeholder: "Near the famous tea stall" },
  { name: "city", label: "City" },
  { name: "state", label: "State" },
  { name: "pincode", label: "PIN code", placeholder: "700032", inputMode: "numeric" },
];

export function ClaimDialog({ goodie }: { goodie: Goodie }) {
  const [open, setOpen] = useState(false);
  const [claim, { isLoading }] = useClaimGoodieMutation();
  const form = useForm<ClaimAddress>({ resolver: zodResolver(schema), defaultValues: lastAddress() });

  async function onSubmit(values: ClaimAddress) {
    try {
      await claim({ goodieId: goodie.id, address: values }).unwrap();
      try {
        localStorage.setItem(ADDRESS_KEY, JSON.stringify(values));
      } catch {
        /* storage may be blocked */
      }
      toast.success(`${goodie.name} claimed! We'll ship it soon 📦`);
      setOpen(false);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button className="w-full" disabled={!goodie.canClaim}>
          <Gift /> {goodie.canClaim ? "Claim" : goodie.blockedReason}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Claim {goodie.name}</DialogTitle>
          <DialogDescription>
            {goodie.coinCost > 0 ? `${formatCoins(goodie.coinCost)} coins will be deducted. ` : "This one's free! "}
            Where should we deliver it?
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-3 sm:grid-cols-2" noValidate>
            {FIELDS.map((f) => (
              <FormField
                key={f.name}
                control={form.control}
                name={f.name}
                render={({ field }) => (
                  <FormItem className={f.name.startsWith("address") || f.name === "fullName" ? "sm:col-span-2" : undefined}>
                    <FormLabel>{f.label}</FormLabel>
                    <FormControl>
                      <Input placeholder={f.placeholder} inputMode={f.inputMode} {...field} value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ))}
            <DialogFooter className="sm:col-span-2">
              <Button type="submit" size="lg" disabled={isLoading}>
                {isLoading ? <Loader2 className="animate-spin" /> : <Gift />} Confirm claim
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
