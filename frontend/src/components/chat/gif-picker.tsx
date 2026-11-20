"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";

import { Input } from "@/components/ui/input";
import { useSearchGifs } from "@/components/chat/use-search-gifs";
import type { Gif } from "@/lib/types";

export function GifPicker({ onPick }: { onPick: (gif: Gif) => void }) {
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setQuery(text.trim()), 350);
    return () => clearTimeout(t);
  }, [text]);

  const { data, isFetching, isError } = useSearchGifs(query);

  return (
    <div className="grid w-[min(92vw,340px)] gap-3">
      <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="Search GIPHY..." autoFocus />
      <div className="grid h-72 grid-cols-2 gap-2 overflow-y-auto scrollbar-thin pr-1">
        {isFetching && !data && (
          <div className="col-span-2 grid place-items-center">
            <Loader2 className="size-6 animate-spin text-bubblegum" />
          </div>
        )}
        {isError && <p className="col-span-2 text-center text-sm text-muted-foreground">GIFs are napping. Try later.</p>}
        {data?.map((gif) => (
          <button
            key={gif.id}
            type="button"
            onClick={() => onPick(gif)}
            className="overflow-hidden rounded-lg border-2 border-border bg-grape-soft transition hover:scale-[1.03]"
            title={gif.title}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={gif.previewUrl} alt={gif.title} loading="lazy" className="h-28 w-full object-cover" />
          </button>
        ))}
      </div>
      <p className="text-right text-[10px] font-bold tracking-wide text-muted-foreground uppercase">Powered by GIPHY</p>
    </div>
  );
}
