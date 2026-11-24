import * as React from "react"
import { cva } from "class-variance-authority";
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"

const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 rounded-md text-sm font-medium whitespace-nowrap transition-all outline-none focus-visible:border-anneau focus-visible:ring-[3px] focus-visible:ring-anneau/50 disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-alerte aria-invalid:ring-alerte/20 dark:aria-invalid:ring-alerte/40 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-accent text-sur-accent hover:bg-accent/90",
        destructive:
          "bg-alerte text-white hover:bg-alerte/90 focus-visible:ring-alerte/20 dark:bg-alerte/60 dark:focus-visible:ring-alerte/40",
        outline:
          "border bg-fond shadow-xs hover:bg-surface-douce hover:text-sur-fond dark:border-bordure-controle dark:bg-bordure-controle/30 dark:hover:bg-bordure-controle/50",
        secondary:
          "bg-surface-douce text-sur-fond hover:bg-surface-douce/80",
        ghost:
          "hover:bg-surface-douce hover:text-sur-fond dark:hover:bg-surface-douce/50",
        link: "text-accent underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-4 py-2 has-[>svg]:px-3",
        xs: "h-6 gap-1 rounded-md px-2 text-xs has-[>svg]:px-1.5 [&_svg:not([class*='size-'])]:size-3",
        sm: "h-8 gap-1.5 rounded-md px-3 has-[>svg]:px-2.5",
        lg: "h-10 rounded-md px-6 has-[>svg]:px-4",
        icon: "size-9",
        "icon-xs": "size-6 rounded-md [&_svg:not([class*='size-'])]:size-3",
        "icon-sm": "size-8",
        "icon-lg": "size-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
