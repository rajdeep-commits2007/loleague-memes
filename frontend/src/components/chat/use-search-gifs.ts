import { useSearchGifsQuery } from "@/store/api/chatApi";

export function useSearchGifs(query: string) {
  return useSearchGifsQuery(query);
}
