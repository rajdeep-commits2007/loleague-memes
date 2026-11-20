"use client";

import { forwardRef, useImperativeHandle, useRef, useState } from "react";

import { UserAvatar } from "@/components/user/user-avatar";
import { Textarea } from "@/components/ui/textarea";
import { useDebounced } from "@/lib/use-debounced";
import { cn } from "@/lib/utils";
import { useSearchMentionsQuery } from "@/store/api/memeApi";

/** The `@partial` right before the caret, if the caret is inside a mention. */
const TOKEN = /(?:^|[^A-Za-z0-9_.])@([A-Za-z0-9._]{0,20})$/;

type Props = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onCancel?: () => void;
  placeholder?: string;
  maxLength?: number;
  className?: string;
  autoFocus?: boolean;
};

/** Textarea that suggests batchmates when you type `@`. Enter sends, Shift+Enter makes a new line. */
export const MentionTextarea = forwardRef<HTMLTextAreaElement, Props>(function MentionTextarea(
  { value, onChange, onSubmit, onCancel, placeholder, maxLength = 500, className, autoFocus },
  forwarded
) {
  const ref = useRef<HTMLTextAreaElement>(null);
  useImperativeHandle(forwarded, () => ref.current as HTMLTextAreaElement);

  const [query, setQuery] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const debounced = useDebounced(query, 200);
  const { data: candidates = [] } = useSearchMentionsQuery(debounced ?? "", { skip: debounced === null });
  const open = query !== null && candidates.length > 0;

  function detect(text: string, caret: number) {
    const m = TOKEN.exec(text.slice(0, caret));
    setQuery(m ? m[1] : null);
    setActive(0);
  }

  function pick(username: string) {
    const el = ref.current;
    const caret = el?.selectionStart ?? value.length;
    const before = value.slice(0, caret).replace(/@([A-Za-z0-9._]{0,20})$/, `@${username} `);
    const next = (before + value.slice(caret)).slice(0, maxLength);
    onChange(next);
    setQuery(null);
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(before.length, before.length);
    });
  }

  return (
    <div className="relative flex-1">
      <Textarea
        ref={ref}
        value={value}
        autoFocus={autoFocus}
        onChange={(e) => {
          const text = e.target.value.slice(0, maxLength);
          onChange(text);
          detect(text, e.target.selectionStart ?? text.length);
        }}
        onClick={(e) => detect(value, e.currentTarget.selectionStart ?? value.length)}
        onBlur={() => setTimeout(() => setQuery(null), 150)}
        onKeyDown={(e) => {
          if (open) {
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
              e.preventDefault();
              const d = e.key === "ArrowDown" ? 1 : -1;
              setActive((i) => (i + d + candidates.length) % candidates.length);
              return;
            }
            if (e.key === "Enter" || e.key === "Tab") {
              e.preventDefault();
              pick(candidates[active].username);
              return;
            }
            if (e.key === "Escape") {
              e.preventDefault();
              setQuery(null);
              return;
            }
          }
          if (e.key === "Escape" && onCancel) {
            e.preventDefault();
            onCancel();
          } else if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            onSubmit();
          }
        }}
        rows={1}
        placeholder={placeholder}
        className={cn("min-h-11", className)}
      />
      {open && (
        <ul
          role="listbox"
          className="absolute top-full left-0 z-50 mt-1 w-64 max-w-full overflow-hidden rounded-xl border-2 border-border bg-popover p-1 shadow-brutal"
        >
          {candidates.map((c, i) => (
            <li key={c.id} role="option" aria-selected={i === active}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  pick(c.username);
                }}
                onMouseEnter={() => setActive(i)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm",
                  i === active && "bg-accent"
                )}
              >
                <UserAvatar name={c.displayName} src={c.avatarUrl} className="size-7" />
                <span className="min-w-0">
                  <span className="block truncate font-bold">{c.displayName}</span>
                  <span className="block truncate text-xs text-muted-foreground">@{c.username}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
});
