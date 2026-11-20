"use client";

import { useDebounced } from "@/lib/use-debounced";
import { useCheckUsernameQuery } from "@/store/api/authApi";

export const USERNAME = /^[A-Za-z0-9][A-Za-z0-9._]{1,18}[A-Za-z0-9]$/;
export const USERNAME_HINT = "3-20 characters: letters, numbers, dot or underscore (not at the ends)";

export type Availability = { state: "idle" | "checking" | "ok" | "taken" | "same"; reason?: string | null };

/**
 * Live "is it already claimed?" check while typing (debounced, cached by RTK Query).
 * `current` is the user's own username, which counts as "same" rather than taken.
 */
export function useUsernameAvailability(raw: string, current?: string): Availability {
  const value = raw.trim();
  const debounced = useDebounced(value, 400);
  const same = !!current && value.toLowerCase() === current.toLowerCase();
  const valid = USERNAME.test(debounced) && !same;
  const { data, isFetching } = useCheckUsernameQuery(debounced, { skip: !valid });
  if (same) return { state: "same" };
  if (!USERNAME.test(value)) return { state: "idle" };
  if (value !== debounced || isFetching || !data || data.username !== debounced.toLowerCase()) {
    return { state: "checking" };
  }
  return { state: data.available ? "ok" : "taken", reason: data.reason };
}
