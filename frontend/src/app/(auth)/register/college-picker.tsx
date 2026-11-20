"use client";

import { useState } from "react";
import { Check, ChevronsUpDown, School } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetCollegesQuery } from "@/store/api/authApi";
import { cn } from "@/lib/utils";

/** Searchable dropdown fed by colleges.json on the auth-service. */
export function CollegePicker({
  value,
  onChange,
  invalid,
  id,
}: {
  value: string;
  onChange: (id: string) => void;
  invalid?: boolean;
  id?: string;
}) {
  const [open, setOpen] = useState(false);
  const { data: colleges, isLoading, isError, refetch } = useGetCollegesQuery();
  const selected = colleges?.find((c) => c.id === value);

  if (isLoading) return <Skeleton className="h-11 w-full" />;
  if (isError)
    return (
      <Button type="button" variant="outline" onClick={() => refetch()}>
        Couldn&apos;t load colleges. Retry
      </Button>
    );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-invalid={invalid}
          className={cn(
            "h-11 w-full justify-between font-sans font-normal shadow-none",
            !selected && "text-muted-foreground",
            invalid && "border-destructive"
          )}
        >
          <span className="flex min-w-0 items-center gap-2">
            <School className="text-bubblegum" />
            <span className="truncate">{selected ? selected.name : "Search your college..."}</span>
          </span>
          <ChevronsUpDown className="opacity-60" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
        <Command
          filter={(itemValue, search) => (itemValue.toLowerCase().includes(search.toLowerCase()) ? 1 : 0)}
        >
          <CommandInput placeholder="Type a college or city..." />
          <CommandList>
            <CommandEmpty>No college found. Ask us to add it!</CommandEmpty>
            <CommandGroup>
              {colleges?.map((c) => (
                <CommandItem
                  key={c.id}
                  value={`${c.name} ${c.city}`}
                  onSelect={() => {
                    onChange(c.id);
                    setOpen(false);
                  }}
                >
                  <Check className={cn("text-bubblegum", value === c.id ? "opacity-100" : "opacity-0")} />
                  <div className="min-w-0">
                    <p className="truncate font-medium">{c.name}</p>
                    <p className="text-xs text-muted-foreground">{c.city}</p>
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
