import * as React from "react"
import { cn } from "@/lib/utils"

function Input({
  className,
  type,
  ...props
}) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-9 w-full min-w-0 rounded-md border border-bordure-controle bg-transparent px-3 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none selection:bg-accent selection:text-sur-accent file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-sur-fond placeholder:text-sur-fond-doux disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm dark:bg-bordure-controle/30",
        "focus-visible:border-anneau focus-visible:ring-[3px] focus-visible:ring-anneau/50",
        "aria-invalid:border-alerte aria-invalid:ring-alerte/20 dark:aria-invalid:ring-alerte/40",
        className
      )}
      {...props}
    />
  )
}

export { Input }
