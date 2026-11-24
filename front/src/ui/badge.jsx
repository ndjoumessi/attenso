import * as React from "react"
import { cva } from "class-variance-authority";
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"

const badgeVariants = cva(
  "inline-flex w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-[color,box-shadow] focus-visible:border-anneau focus-visible:ring-[3px] focus-visible:ring-anneau/50 aria-invalid:border-alerte aria-invalid:ring-alerte/20 dark:aria-invalid:ring-alerte/40 [&>svg]:pointer-events-none [&>svg]:size-3",
  {
    variants: {
      variant: {
        default: "bg-accent text-sur-accent [a&]:hover:bg-accent/90",
        secondary:
          "bg-surface-douce text-sur-fond [a&]:hover:bg-surface-douce/90",
        destructive:
          "bg-alerte text-sur-alerte focus-visible:ring-alerte/20 dark:focus-visible:ring-alerte/40 [a&]:hover:bg-alerte/90",
        outline:
          "border-bordure-douce text-sur-fond [a&]:hover:bg-surface-douce [a&]:hover:text-sur-fond",
        ghost: "[a&]:hover:bg-surface-douce [a&]:hover:text-sur-fond",
        link: "text-accent underline-offset-4 [a&]:hover:underline",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  asChild = false,
  ...props
}) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
