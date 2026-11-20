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
      {/* Masonry columns keep each GIF's real aspect ratio, so nothing is cropped or stretched. */}
      <div className="h-72 overflow-y-auto scrollbar-thin pr-1">
        {isFetching && !data && (
          <div className="grid h-full place-items-center">
            <Loader2 className="size-6 animate-spin text-bubblegum" />
          </div>
        )}
        {isError && <p className="pt-6 text-center text-sm text-muted-foreground">GIFs are napping. Try later.</p>}
        {data && data.length === 0 && !isFetching && (
          <p className="pt-6 text-center text-sm text-muted-foreground">No GIFs for that. Try another word.</p>
        )}
        <div className="columns-2 gap-2">
          {data?.map((gif) => (
            <button
              key={gif.id}
              type="button"
              onClick={() => onPick(gif)}
              className="mb-2 block w-full break-inside-avoid overflow-hidden rounded-lg border-2 border-border bg-grape-soft transition hover:border-grape focus-visible:border-grape focus-visible:outline-none"
              style={{ aspectRatio: gif.width > 0 && gif.height > 0 ? `${gif.width} / ${gif.height}` : "1 / 1" }}
              title={gif.title}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={gif.previewUrl}
                alt={gif.title}
                loading="lazy"
                decoding="async"
                className="block size-full object-contain"
              />
            </button>
          ))}
        </div>
      </div>
      <p className="text-right text-[10px] font-bold tracking-wide text-muted-foreground uppercase">Powered by GIPHY</p>
    </div>
  );
}
