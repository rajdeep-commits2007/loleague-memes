import type { FetchBaseQueryError } from "@reduxjs/toolkit/query";
import type { SerializedError } from "@reduxjs/toolkit";

/** Pulls the human message out of a Spring ProblemDetail (or anything else) for toasts and forms. */
export function errorMessage(error: unknown, fallback = "Something went wrong. Try again."): string {
  if (!error) return fallback;
  const e = error as FetchBaseQueryError | SerializedError;
  if ("status" in e) {
    const data = e.data as { detail?: string; title?: string } | undefined;
    if (data?.detail) return data.detail;
    if (e.status === "FETCH_ERROR") return "Can't reach LOLeague. Check your connection.";
    if (data?.title) return data.title;
    return fallback;
  }
  return e.message ?? fallback;
}

/** Field errors from ProblemDetail.errors, e.g. { username: "Already claimed" }. */
export function fieldErrors(error: unknown): Record<string, string> {
  const e = error as FetchBaseQueryError | undefined;
  if (e && "status" in e) {
    const data = e.data as { errors?: Record<string, string> } | undefined;
    return data?.errors ?? {};
  }
  return {};
}
