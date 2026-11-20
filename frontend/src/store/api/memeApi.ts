import type { Draft } from "@reduxjs/toolkit";
import { baseApi } from "@/store/api/baseApi";
import type { RootState } from "@/store/store";
import type {
  Ad,
  Author,
  ClaimAddress,
  Comment,
  Goodie,
  GoodieClaim,
  Leaderboard,
  Page,
  Post,
  PresignResponse,
  Wallet,
} from "@/lib/types";

export type FeedSort = "new" | "top";
export interface FeedArgs {
  sort: FeedSort;
}
export interface UserPostsArgs {
  username: string;
}

const PAGE_SIZE = 10;

export const memeApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    // ------------------------------------------------------------- uploads
    presign: build.mutation<PresignResponse, { purpose: "MEME" | "AVATAR"; contentType: string; contentLength: number }>({
      query: (body) => ({ url: "/media/presign", method: "POST", body }),
    }),

    // ------------------------------------------------------------- memes
    getFeed: build.infiniteQuery<Page<Post>, FeedArgs, string>({
      infiniteQueryOptions: {
        initialPageParam: "",
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
      },
      query: ({ queryArg, pageParam }) => ({
        url: "/posts",
        params: { sort: queryArg.sort, limit: PAGE_SIZE, ...(pageParam ? { cursor: pageParam } : {}) },
      }),
      providesTags: ["Feed"],
    }),
    getUserPosts: build.infiniteQuery<Page<Post>, UserPostsArgs, string>({
      infiniteQueryOptions: {
        initialPageParam: "",
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
      },
      query: ({ queryArg, pageParam }) => ({
        url: "/posts",
        params: { author: queryArg.username, limit: 12, ...(pageParam ? { cursor: pageParam } : {}) },
      }),
      providesTags: ["Feed"],
    }),
    getPost: build.query<Post, string>({
      query: (id) => `/posts/${id}`,
      providesTags: (_r, _e, id) => [{ type: "Post", id }],
    }),
    createPost: build.mutation<Post, { caption?: string; imageKey: string }>({
      query: (body) => ({ url: "/posts", method: "POST", body }),
      invalidatesTags: ["Feed"],
    }),
    deletePost: build.mutation<void, string>({
      query: (id) => ({ url: `/posts/${id}`, method: "DELETE" }),
      invalidatesTags: ["Feed", "Leaderboard"],
    }),
    toggleLike: build.mutation<{ liked: boolean; likeCount: number }, { id: string; like: boolean }>({
      query: ({ id, like }) => ({ url: `/posts/${id}/like`, method: like ? "PUT" : "DELETE" }),
      // Optimistic: flip the heart everywhere this meme is cached, roll back on error.
      async onQueryStarted({ id, like }, { dispatch, getState, queryFulfilled }) {
        const undo = patchPostEverywhere(dispatch, getState as () => RootState, id, (p) => {
          if (p.likedByMe === like) return;
          p.likedByMe = like;
          p.likeCount = Math.max(0, p.likeCount + (like ? 1 : -1));
        });
        try {
          const { data } = await queryFulfilled;
          patchPostEverywhere(dispatch, getState as () => RootState, id, (p) => {
            p.likeCount = data.likeCount;
            p.likedByMe = data.liked;
          });
        } catch {
          undo();
        }
      },
      invalidatesTags: ["Leaderboard"],
    }),

    // ------------------------------------------------------------- comments
    getComments: build.infiniteQuery<Page<Comment>, string, string>({
      infiniteQueryOptions: {
        initialPageParam: "",
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
      },
      query: ({ queryArg, pageParam }) => ({
        url: `/posts/${queryArg}/comments`,
        params: { limit: 20, ...(pageParam ? { cursor: pageParam } : {}) },
      }),
      providesTags: (_r, _e, postId) => [{ type: "Comments", id: postId }],
    }),
    getReplies: build.infiniteQuery<Page<Comment>, string, string>({
      infiniteQueryOptions: {
        initialPageParam: "",
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
      },
      query: ({ queryArg, pageParam }) => ({
        url: `/comments/${queryArg}/replies`,
        params: { limit: 10, ...(pageParam ? { cursor: pageParam } : {}) },
      }),
      providesTags: (_r, _e, parentId) => [{ type: "Comments", id: `replies:${parentId}` }],
    }),
    addComment: build.mutation<Comment, { postId: string; body: string; parentId?: string | null }>({
      query: ({ postId, body, parentId }) => ({
        url: `/posts/${postId}/comments`,
        method: "POST",
        body: { body, parentId: parentId ?? null },
      }),
      async onQueryStarted({ postId }, { dispatch, getState, queryFulfilled }) {
        try {
          await queryFulfilled;
          patchPostEverywhere(dispatch, getState as () => RootState, postId, (p) => {
            p.commentCount += 1;
          });
        } catch {
          /* toast handled by caller */
        }
      },
      // A reply refreshes the top-level list too, so the parent's reply count goes up.
      invalidatesTags: (r, _e, { postId }) => [
        { type: "Comments", id: postId },
        ...(r?.parentId ? [{ type: "Comments" as const, id: `replies:${r.parentId}` }] : []),
        "Leaderboard",
      ],
    }),
    editComment: build.mutation<Comment, { comment: Comment; body: string }>({
      query: ({ comment, body }) => ({ url: `/comments/${comment.id}`, method: "PATCH", body: { body } }),
      async onQueryStarted({ comment }, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          patchComment(dispatch, comment, () => data);
        } catch {
          /* toast handled by caller */
        }
      },
    }),
    reactToComment: build.mutation<Comment, { comment: Comment; emoji: string }>({
      query: ({ comment, emoji }) => ({ url: `/comments/${comment.id}/reactions`, method: "POST", body: { emoji } }),
      // Optimistic toggle; the server's answer replaces it.
      async onQueryStarted({ comment, emoji }, { dispatch, queryFulfilled }) {
        const undo = patchComment(dispatch, comment, (c) => {
          const r = c.reactions.find((x) => x.emoji === emoji);
          if (r?.mine) {
            r.count -= 1;
            r.mine = false;
            if (r.count <= 0) c.reactions = c.reactions.filter((x) => x.emoji !== emoji);
          } else if (r) {
            r.count += 1;
            r.mine = true;
          } else {
            c.reactions.push({ emoji, count: 1, mine: true });
          }
        });
        try {
          const { data } = await queryFulfilled;
          patchComment(dispatch, comment, () => data);
        } catch {
          undo();
        }
      },
    }),
    searchMentions: build.query<Author[], string>({
      query: (q) => ({ url: "/comments/mentions", params: { q } }),
      keepUnusedDataFor: 60,
    }),
    deleteComment: build.mutation<void, { id: string; postId: string; parentId?: string | null; replyCount?: number }>({
      query: ({ id }) => ({ url: `/comments/${id}`, method: "DELETE" }),
      async onQueryStarted({ postId, parentId, replyCount }, { dispatch, getState, queryFulfilled }) {
        try {
          await queryFulfilled;
          const removed = 1 + (parentId ? 0 : (replyCount ?? 0));
          patchPostEverywhere(dispatch, getState as () => RootState, postId, (p) => {
            p.commentCount = Math.max(0, p.commentCount - removed);
          });
        } catch {
          /* ignore */
        }
      },
      invalidatesTags: (_r, _e, { postId, parentId }) => [
        { type: "Comments", id: postId },
        ...(parentId ? [{ type: "Comments" as const, id: `replies:${parentId}` }] : []),
        "Leaderboard",
      ],
    }),

    // ------------------------------------------------------------- ads
    getAds: build.query<Ad[], number>({
      query: (count) => ({ url: "/ads", params: { count } }),
      keepUnusedDataFor: 300,
    }),
    trackAd: build.mutation<void, { id: string; event: "impression" | "click" }>({
      query: ({ id, event }) => ({ url: `/ads/${id}/${event}`, method: "POST" }),
    }),

    // ------------------------------------------------------------- leaderboard & coins
    getLeaderboard: build.query<Leaderboard, "current" | "previous">({
      query: (week) => ({ url: "/leaderboard", params: { week } }),
      providesTags: ["Leaderboard"],
    }),
    getWallet: build.query<Wallet, void>({
      query: () => "/wallet",
      providesTags: ["Wallet"],
    }),
    getGoodies: build.query<Goodie[], void>({
      query: () => "/goodies",
      providesTags: ["Goodies"],
    }),
    getClaims: build.query<GoodieClaim[], void>({
      query: () => "/goodies/claims",
      providesTags: ["Claims"],
    }),
    claimGoodie: build.mutation<GoodieClaim, { goodieId: string; address: ClaimAddress }>({
      query: ({ goodieId, address }) => ({ url: `/goodies/${goodieId}/claim`, method: "POST", body: address }),
      invalidatesTags: ["Wallet", "Goodies", "Claims"],
    }),
  }),
});

type AppDispatchLike = (action: unknown) => unknown;

/** Applies `recipe` to the meme in every cached feed page and the single-post cache. Returns an undo fn. */
function patchPostEverywhere(
  dispatch: AppDispatchLike,
  getState: () => RootState,
  postId: string,
  recipe: (post: Draft<Post>) => void
): () => void {
  const undos: Array<() => void> = [];
  const entries = memeApi.util.selectInvalidatedBy(getState(), ["Feed", { type: "Post", id: postId }]);
  for (const entry of entries) {
    if (entry.endpointName === "getFeed") {
      const patch = dispatch(
        memeApi.util.updateQueryData("getFeed", entry.originalArgs as FeedArgs, (draft) => {
          draft.pages.forEach((page) => page.items.forEach((p) => p.id === postId && recipe(p)));
        })
      ) as { undo: () => void };
      undos.push(patch.undo);
    } else if (entry.endpointName === "getUserPosts") {
      const patch = dispatch(
        memeApi.util.updateQueryData("getUserPosts", entry.originalArgs as UserPostsArgs, (draft) => {
          draft.pages.forEach((page) => page.items.forEach((p) => p.id === postId && recipe(p)));
        })
      ) as { undo: () => void };
      undos.push(patch.undo);
    } else if (entry.endpointName === "getPost") {
      const patch = dispatch(
        memeApi.util.updateQueryData("getPost", entry.originalArgs as string, (draft) => {
          recipe(draft);
        })
      ) as { undo: () => void };
      undos.push(patch.undo);
    }
  }
  return () => undos.forEach((u) => u());
}

/**
 * Updates one comment in place, wherever it lives: the post's top-level list or its parent's replies.
 * `recipe` may mutate the draft or return a full replacement. Returns an undo fn.
 */
function patchComment(
  dispatch: AppDispatchLike,
  comment: Comment,
  recipe: (c: Draft<Comment>) => Comment | void
): () => void {
  const apply = (pages: Draft<Page<Comment>>[]) => {
    for (const page of pages) {
      const i = page.items.findIndex((c) => c.id === comment.id);
      if (i >= 0) {
        const replaced = recipe(page.items[i]);
        if (replaced) page.items[i] = replaced;
      }
    }
  };
  const patch = comment.parentId
    ? dispatch(memeApi.util.updateQueryData("getReplies", comment.parentId, (d) => apply(d.pages)))
    : dispatch(memeApi.util.updateQueryData("getComments", comment.postId, (d) => apply(d.pages)));
  return (patch as { undo: () => void }).undo;
}

export const {
  usePresignMutation,
  useGetFeedInfiniteQuery,
  useGetUserPostsInfiniteQuery,
  useGetPostQuery,
  useCreatePostMutation,
  useDeletePostMutation,
  useToggleLikeMutation,
  useGetCommentsInfiniteQuery,
  useGetRepliesInfiniteQuery,
  useAddCommentMutation,
  useEditCommentMutation,
  useReactToCommentMutation,
  useSearchMentionsQuery,
  useDeleteCommentMutation,
  useGetAdsQuery,
  useTrackAdMutation,
  useGetLeaderboardQuery,
  useGetWalletQuery,
  useGetGoodiesQuery,
  useGetClaimsQuery,
  useClaimGoodieMutation,
} = memeApi;
