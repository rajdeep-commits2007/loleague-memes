import { baseApi } from "@/store/api/baseApi";
import { loggedIn, loggedOut, userUpdated } from "@/store/slices/authSlice";
import type { College, PublicProfile, User, UsernameAvailability } from "@/lib/types";

export interface RegisterBody {
  collegeId: string;
  username: string;
  password: string;
  confirmPassword: string;
}

export interface LoginBody {
  username: string;
  password: string;
}

export interface UpdateProfileBody {
  displayName?: string;
  bio?: string | null;
  avatarUrl?: string | null;
  birthday?: string | null;
}

export interface ChangePasswordBody {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
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
    register: build.mutation<User, RegisterBody>({
      query: (body) => ({ url: "/auth/register", method: "POST", body }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        const { data } = await queryFulfilled;
        dispatch(loggedIn(data));
        dispatch(authApi.util.upsertQueryData("getMe", undefined, data));
      },
    }),
    login: build.mutation<User, LoginBody>({
      query: (body) => ({ url: "/auth/login", method: "POST", body }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        const { data } = await queryFulfilled;
        dispatch(baseApi.util.resetApiState());
        dispatch(loggedIn(data));
        dispatch(authApi.util.upsertQueryData("getMe", undefined, data));
      },
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
    changePassword: build.mutation<void, ChangePasswordBody>({
      query: (body) => ({ url: "/users/me/password", method: "POST", body }),
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
  useRegisterMutation,
  useLoginMutation,
  useLogoutMutation,
  useGetMeQuery,
  useUpdateProfileMutation,
  useChangePasswordMutation,
  useGetProfileQuery,
} = authApi;
