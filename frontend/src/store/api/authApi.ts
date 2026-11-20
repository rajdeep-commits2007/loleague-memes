import { baseApi } from "@/store/api/baseApi";
import { loggedIn, loggedOut, userUpdated } from "@/store/slices/authSlice";
import type { College, PublicProfile, SessionResult, User, UsernameAvailability } from "@/lib/types";

type SessionApi = {
  dispatch: (action: unknown) => unknown;
  queryFulfilled: Promise<{ data: SessionResult }>;
};

async function onSession({ dispatch, queryFulfilled }: SessionApi) {
  try {
    const { data } = await queryFulfilled;
    if (data.status === "SIGNED_IN") {
      dispatch(baseApi.util.resetApiState());
      dispatch(loggedIn(data.user));
      dispatch(authApi.util.upsertQueryData("getMe", undefined, data.user));
    }
  } catch {
    /* handled by the caller */
  }
}

export interface OnboardBody {
  token: string;
  collegeId: string;
  username: string;
}

export interface ClaimBody {
  token: string;
  username: string;
  password: string;
}

export interface UpdateProfileBody {
  displayName?: string;
  bio?: string | null;
  avatarUrl?: string | null;
  birthday?: string | null;
}

export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getColleges: build.query<College[], void>({
      query: () => "/auth/colleges",
      keepUnusedDataFor: 3600,
    }),
    checkUsername: build.query<UsernameAvailability, string>({
      query: (username) => ({ url: "/auth/username-available", params: { username } }),
      keepUnusedDataFor: 10,
    }),
    // Clerk did the sign-in; these swap its short-lived session token for our HttpOnly cookies.
    clerkSession: build.mutation<SessionResult, string>({
      query: (token) => ({ url: "/auth/clerk/session", method: "POST", body: { token } }),
      onQueryStarted: (_, api) => onSession(api),
    }),
    onboard: build.mutation<SessionResult, OnboardBody>({
      query: (body) => ({ url: "/auth/clerk/onboard", method: "POST", body }),
      onQueryStarted: (_, api) => onSession(api),
    }),
    claimAccount: build.mutation<SessionResult, ClaimBody>({
      query: (body) => ({ url: "/auth/clerk/claim", method: "POST", body }),
      onQueryStarted: (_, api) => onSession(api),
    }),
    logout: build.mutation<void, void>({
      query: () => ({ url: "/auth/logout", method: "POST" }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          await queryFulfilled;
        } finally {
          dispatch(loggedOut());
          dispatch(baseApi.util.resetApiState());
        }
      },
    }),
    getMe: build.query<User, void>({
      query: () => "/users/me",
      providesTags: ["Me"],
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(loggedIn(data));
        } catch {
          /* handled by baseQuery (redirect to /login) */
        }
      },
    }),
    updateProfile: build.mutation<User, UpdateProfileBody>({
      query: (body) => ({ url: "/users/me", method: "PATCH", body }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        const { data } = await queryFulfilled;
        dispatch(userUpdated(data));
        dispatch(authApi.util.upsertQueryData("getMe", undefined, data));
      },
      invalidatesTags: ["Profile", "Feed"],
    }),
    changeUsername: build.mutation<User, string>({
      query: (username) => ({ url: "/users/me/username", method: "PATCH", body: { username } }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        const { data } = await queryFulfilled;
        dispatch(userUpdated(data));
        dispatch(authApi.util.upsertQueryData("getMe", undefined, data));
      },
      invalidatesTags: ["Profile", "Feed", "Comments", "Leaderboard"],
    }),
    getProfile: build.query<PublicProfile, string>({
      query: (username) => `/users/${encodeURIComponent(username)}`,
      providesTags: (_r, _e, username) => [{ type: "Profile", id: username }],
    }),
  }),
});

export const {
  useGetCollegesQuery,
  useCheckUsernameQuery,
  useClerkSessionMutation,
  useOnboardMutation,
  useClaimAccountMutation,
  useLogoutMutation,
  useGetMeQuery,
  useUpdateProfileMutation,
  useChangeUsernameMutation,
  useGetProfileQuery,
} = authApi;
