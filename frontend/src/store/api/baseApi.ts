import {
  createApi,
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from "@reduxjs/toolkit/query/react";
import { sessionExpired } from "@/store/slices/authSlice";

/**
 * Every call goes to our own origin (/api/*). The Next.js route handler forwards it to the right
 * Spring Boot service, so the HttpOnly auth cookies are first-party and never touched by JS.
 */
const rawBaseQuery = fetchBaseQuery({
  baseUrl: "/api",
  credentials: "same-origin",
  prepareHeaders: (headers) => {
    headers.set("Accept", "application/json");
    return headers;
  },
});

// Single-flight refresh: many parallel 401s trigger exactly one /auth/refresh.
let refreshing: Promise<"ok" | "retry" | "failed"> | null = null;

async function refreshSession(): Promise<"ok" | "retry" | "failed"> {
  if (!refreshing) {
    refreshing = fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then((res) => (res.ok ? "ok" : res.status === 409 ? "retry" : "failed"))
      .catch(() => "failed" as const)
      .finally(() => {
        setTimeout(() => (refreshing = null), 0);
      });
  }
  return refreshing;
}

const baseQueryWithReauth: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions
) => {
  let result = await rawBaseQuery(args, api, extraOptions);
  const url = typeof args === "string" ? args : args.url;
  const isAuthCall = url.startsWith("/auth/");

  if (result.error?.status === 401 && !isAuthCall) {
    const outcome = await refreshSession();
    if (outcome === "ok" || outcome === "retry") {
      result = await rawBaseQuery(args, api, extraOptions);
    }
    if (outcome === "failed" || result.error?.status === 401) {
      api.dispatch(sessionExpired());
      if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
        // Drop the (non-secret) session hint so the route guard doesn't bounce us back.
        document.cookie = "ll_session=; Max-Age=0; path=/";
        const next = encodeURIComponent(window.location.pathname + window.location.search);
        // Hard navigation on purpose: wipes all in-memory state of the expired session.
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign(`/login?next=${next}`);
      }
    }
  }
  return result;
};

export const baseApi = createApi({
  reducerPath: "api",
  baseQuery: baseQueryWithReauth,
  tagTypes: ["Me", "Feed", "Post", "Comments", "Leaderboard", "Wallet", "Goodies", "Claims", "Profile"],
  refetchOnReconnect: true,
  endpoints: () => ({}),
});
