import Link from "next/link";
import { cn } from "@/lib/utils";

/** The laughing grape blob + wordmark. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden className={cn("size-9", className)}>
      <rect x="6" y="8" width="56" height="54" rx="14" fill="var(--shadow-ink)" />
      <rect x="2" y="2" width="56" height="54" rx="14" fill="#6b3cf6" stroke="#1a1033" strokeWidth="3" />
      <circle cx="21" cy="24" r="5" fill="#fff" />
      <circle cx="39" cy="24" r="5" fill="#fff" />
      <circle cx="22" cy="25" r="2.4" fill="#1a1033" />
      <circle cx="40" cy="25" r="2.4" fill="#1a1033" />
      <path d="M16 35 Q30 50 44 35 Z" fill="#c6f432" stroke="#1a1033" strokeWidth="3" strokeLinejoin="round" />
      <circle cx="50" cy="12" r="5" fill="#ff3d8b" stroke="#1a1033" strokeWidth="2" />
    </svg>
  );
}

export function Logo({ href = "/", className }: { href?: string; className?: string }) {
  return (
    <Link href={href} className={cn("inline-flex items-center gap-2", className)}>
      <LogoMark />
      <span className="font-display text-2xl font-extrabold tracking-tight">
        LOL<span className="text-grape">eague</span>
      </span>
    </Link>
  );
}
