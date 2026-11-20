import { Skeleton } from "@/components/ui/skeleton";

export function PostSkeleton() {
  return (
    <div className="overflow-hidden rounded-2xl border-2 border-border bg-card">
      <div className="flex items-center gap-3 p-4">
        <Skeleton className="size-9 rounded-full" />
        <div className="grid gap-1.5">
          <Skeleton className="h-3.5 w-32" />
          <Skeleton className="h-3 w-20" />
        </div>
      </div>
      <Skeleton className="h-72 rounded-none" />
      <div className="flex gap-3 p-4">
        <Skeleton className="h-6 w-14" />
        <Skeleton className="h-6 w-14" />
      </div>
    </div>
  );
}
