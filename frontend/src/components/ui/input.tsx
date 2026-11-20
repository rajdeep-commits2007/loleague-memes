import * as React from "react";

import { cn } from "@/lib/utils";

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "flex h-11 w-full min-w-0 rounded-lg border-2 border-input bg-card px-3 py-1 text-base transition-[color,box-shadow] outline-none placeholder:text-muted-foreground selection:bg-grape selection:text-white file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
        "focus-visible:border-grape focus-visible:shadow-[3px_3px_0_0_var(--grape)]",
        "aria-invalid:border-destructive aria-invalid:shadow-[3px_3px_0_0_var(--destructive)]",
        className
      )}
      {...props}
    />
  );
}

export { Input };
