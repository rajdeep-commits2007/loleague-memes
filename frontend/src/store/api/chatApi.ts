import { baseApi } from "@/store/api/baseApi";
import type { ChatMessage, Gif, WsTicket } from "@/lib/types";

export const chatApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getWsTicket: build.mutation<WsTicket, void>({
      query: () => ({ url: "/chat/ws-ticket", method: "GET" }),
    }),
    getChatHistory: build.mutation<{ items: ChatMessage[]; nextBefore: string | null }, { before?: string | null }>({
      query: ({ before }) => ({ url: "/chat/messages", params: before ? { before, limit: 40 } : { limit: 40 } }),
    }),
    getOnline: build.mutation<{ online: number }, void>({
      query: () => ({ url: "/chat/online", method: "GET" }),
    }),
    searchGifs: build.query<Gif[], string>({
      query: (q) => ({ url: "/chat/gifs", params: q ? { q } : {} }),
      keepUnusedDataFor: 600,
    }),
  }),
});

export const { useGetWsTicketMutation, useGetChatHistoryMutation, useGetOnlineMutation, useSearchGifsQuery } = chatApi;
