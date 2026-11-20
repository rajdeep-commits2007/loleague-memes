import * as React from "react";
import { Slot } from "radix-ui";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/** Lime = the "1 part" reserved for buttons. Grape is the secondary. Chunky meme borders + hard shadow. */
const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-lg font-display text-sm font-bold transition-all outline-none select-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "border-2 border-ink bg-primary text-primary-foreground shadow-brutal-sm hover:-translate-x-px hover:-translate-y-px hover:shadow-brutal active:translate-x-0.5 active:translate-y-0.5 active:shadow-none dark:border-black",
        secondary:
          "border-2 border-ink bg-secondary text-secondary-foreground shadow-brutal-sm hover:-translate-x-px hover:-translate-y-px hover:shadow-brutal active:translate-x-0.5 active:translate-y-0.5 active:shadow-none dark:border-black",
        destructive:
          "border-2 border-ink bg-destructive text-white shadow-brutal-sm hover:bg-destructive/90 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none",
        outline:
          "border-2 border-ink bg-card text-foreground shadow-brutal-sm hover:bg-accent active:translate-x-0.5 active:translate-y-0.5 active:shadow-none",
        ghost: "hover:bg-accent hover:text-accent-foreground",
        link: "text-grape underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-4 py-2 has-[>svg]:px-3",
        sm: "h-8 gap-1.5 rounded-md px-3 has-[>svg]:px-2.5",
        lg: "h-12 rounded-xl px-6 text-base has-[>svg]:px-4",
        icon: "size-10",
        "icon-sm": "size-8",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  }) {
  const Comp = asChild ? Slot.Root : "button";

  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };
