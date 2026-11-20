"use client";

import dynamic from "next/dynamic";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { ImagePlay, Loader2, SendHorizontal, Smile, Users, Wifi, WifiOff } from "lucide-react";
import type { EmojiClickData, Theme } from "emoji-picker-react";
import { useTheme } from "next-themes";

import { GifPicker } from "@/components/chat/gif-picker";
import { useChatConnection } from "@/components/chat/use-chat-connection";
import { UserAvatar } from "@/components/user/user-avatar";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import { chatApi } from "@/store/api/chatApi";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { olderLoaded } from "@/store/slices/chatSlice";
import type { ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";

const EmojiPicker = dynamic(() => import("emoji-picker-react"), {
  ssr: false,
  loading: () => (
    <div className="grid h-[380px] w-[320px] place-items-center">
      <Loader2 className="animate-spin text-bubblegum" />
    </div>
  ),
});

const GROUP_WINDOW_MS = 5 * 60 * 1000;

function sameGroup(a: ChatMessage | undefined, b: ChatMessage) {
  return !!a && a.userId === b.userId && new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() < GROUP_WINDOW_MS;
}

function clock(iso: string) {
  return new Date(iso).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
}

export function ChatRoom() {
  const dispatch = useAppDispatch();
  const { messages, status, online, olderCursor, historyLoaded } = useAppSelector((s) => s.chat);
  const me = useAppSelector((s) => s.auth.user);
  const { sendText, sendGif } = useChatConnection();
  const { resolvedTheme } = useTheme();

  const [text, setText] = useState("");
  const [gifOpen, setGifOpen] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const prevHeight = useRef<number | null>(null);

  // keep pinned to the newest message unless the user scrolled up
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    if (prevHeight.current !== null) {
      el.scrollTop = el.scrollHeight - prevHeight.current; // after loading older messages
      prevHeight.current = null;
    } else if (stickToBottom.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onScroll = () => {
      stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    };
    el.addEventListener("scroll", onScroll, { passive: true });
    return () => el.removeEventListener("scroll", onScroll);
  }, []);

  async function loadOlder() {
    if (!olderCursor || loadingOlder) return;
    setLoadingOlder(true);
    prevHeight.current = scrollRef.current?.scrollHeight ?? null;
    try {
      const page = await dispatch(chatApi.endpoints.getChatHistory.initiate({ before: olderCursor })).unwrap();
      dispatch(olderLoaded(page));
    } catch {
      prevHeight.current = null;
    } finally {
      setLoadingOlder(false);
    }
  }

  function submit() {
    const value = text.trim();
    if (!value) return;
    stickToBottom.current = true;
    if (sendText(value)) setText("");
  }

  return (
    <div className="flex h-[calc(100dvh-11rem)] min-h-[420px] flex-col overflow-hidden rounded-2xl border-2 border-border bg-card shadow-brutal md:h-[calc(100dvh-7.5rem)]">
      {/* header */}
      <div className="flex items-center gap-3 border-b-2 border-border bg-grape px-4 py-3 text-white">
        <div className="min-w-0">
          <p className="truncate font-display text-lg font-extrabold"># {me?.collegeName ?? "college"}-adda</p>
          <p className="text-xs opacity-85">Be nice. Everyone here is your batchmate.</p>
        </div>
        <div className="ml-auto flex items-center gap-2 text-xs font-bold">
          <span className="inline-flex items-center gap-1 rounded-full border-2 border-ink bg-card px-2 py-0.5 whitespace-nowrap text-foreground">
            <Users className="size-3.5 text-bubblegum" /> {online} online
          </span>
          {status === "connected" ? (
            <Wifi className="size-4 text-lime" aria-label="Connected" />
          ) : (
            <WifiOff className="size-4 animate-pulse text-lime" aria-label="Connecting" />
          )}
        </div>
      </div>

      {/* messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto scrollbar-thin px-3 py-4">
        {olderCursor && (
          <div className="mb-3 flex justify-center">
            <Button variant="outline" size="sm" onClick={loadOlder} disabled={loadingOlder}>
              {loadingOlder && <Loader2 className="animate-spin" />} Load older messages
            </Button>
          </div>
        )}
        {!historyLoaded && (
          <div className="grid h-full place-items-center">
            <Loader2 className="size-6 animate-spin text-bubblegum" />
          </div>
        )}
        {historyLoaded && messages.length === 0 && (
          <div className="grid h-full place-items-center text-center">
            <div>
              <p className="text-4xl">🦗</p>
              <p className="mt-2 font-display font-bold">Crickets... start the adda!</p>
            </div>
          </div>
        )}
        <ul className="grid gap-0.5">
          {messages.map((m, i) => {
            const grouped = sameGroup(messages[i - 1], m);
            const mine = m.userId === me?.id;
            return (
              <li key={m.id} className={cn("group flex gap-3 rounded-lg px-2 py-0.5 hover:bg-accent/50", !grouped && "mt-3")}>
                <div className="w-9 shrink-0">
                  {!grouped && <UserAvatar name={m.username} />}
                </div>
                <div className="min-w-0 flex-1">
                  {!grouped && (
                    <p className="flex items-baseline gap-2">
                      <span className={cn("font-display font-bold", mine && "text-grape")}>{m.username}</span>
                      <span className="text-[11px] text-muted-foreground">{clock(m.createdAt)}</span>
                    </p>
                  )}
                  {m.kind === "TEXT" ? (
                    <p className="text-[15px] leading-relaxed whitespace-pre-wrap break-words">{m.content}</p>
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={m.content}
                      alt="GIF"
                      loading="lazy"
                      width={m.gifWidth ?? undefined}
                      height={m.gifHeight ?? undefined}
                      className="mt-1 max-h-56 w-auto max-w-full rounded-lg border-2 border-border"
                    />
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      {/* composer */}
      <div className="flex items-end gap-2 border-t-2 border-border p-3">
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Emoji">
              <Smile className="size-5 text-bubblegum" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto border-0 p-0 shadow-none" align="start" side="top">
            <EmojiPicker
              theme={(resolvedTheme === "dark" ? "dark" : "light") as Theme}
              lazyLoadEmojis
              onEmojiClick={(e: EmojiClickData) => setText((t) => t + e.emoji)}
            />
          </PopoverContent>
        </Popover>
        <Popover open={gifOpen} onOpenChange={setGifOpen}>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="GIF">
              <ImagePlay className="size-5 text-bubblegum" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto" align="start" side="top">
            <GifPicker
              onPick={(gif) => {
                stickToBottom.current = true;
                if (sendGif(gif)) setGifOpen(false);
              }}
            />
          </PopoverContent>
        </Popover>
        <Textarea
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, 1000))}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          rows={1}
          placeholder="Message the adda..."
          className="max-h-32 min-h-10"
        />
        <Button size="icon" onClick={submit} disabled={!text.trim() || status !== "connected"} aria-label="Send">
          <SendHorizontal />
        </Button>
      </div>
    </div>
  );
}
