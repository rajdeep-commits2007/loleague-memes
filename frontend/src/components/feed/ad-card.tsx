"use client";

import { useEffect, useRef } from "react";
import { ExternalLink, Megaphone } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useTrackAdMutation } from "@/store/api/memeApi";
import { useInView } from "@/lib/use-in-view";
import type { Ad } from "@/lib/types";

/** Sponsored card that sits between memes (Instagram style). Counts an impression once, at 60% visible. */
export function AdCard({ ad }: { ad: Ad }) {
  const [track] = useTrackAdMutation();
  const { ref, inView } = useInView<HTMLElement>({ once: true, threshold: 0.6 });
  const tracked = useRef(false);

  useEffect(() => {
    if (inView && !tracked.current) {
      tracked.current = true;
      track({ id: ad.id, event: "impression" });
    }
  }, [inView, ad.id, track]);

  return (
    <article
      ref={ref}
      className="overflow-hidden rounded-2xl border-2 border-dashed border-grape bg-grape-soft shadow-brutal-sm"
    >
      <div className="flex items-center gap-2 px-4 py-3">
        <div className="grid size-9 place-items-center rounded-full border-2 border-ink bg-card">
          <Megaphone className="size-4 text-bubblegum" />
        </div>
        <p className="font-display font-bold">{ad.title}</p>
        <Badge variant="secondary" className="ml-auto">
          Sponsored
        </Badge>
      </div>
      {ad.imageUrl && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={ad.imageUrl} alt={ad.title} loading="lazy" className="max-h-80 w-full border-y-2 border-border object-cover" />
      )}
      <div className="flex flex-wrap items-center gap-3 px-4 py-3">
        {ad.body && <p className="flex-1 text-sm">{ad.body}</p>}
        <Button asChild size="sm">
          <a
            href={ad.targetUrl}
            target="_blank"
            rel="noopener noreferrer sponsored"
            onClick={() => track({ id: ad.id, event: "click" })}
          >
            {ad.ctaText} <ExternalLink />
          </a>
        </Button>
      </div>
    </article>
  );
}
