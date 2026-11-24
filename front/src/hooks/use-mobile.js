import * as React from "react"

const MOBILE_BREAKPOINT = 768

/**
 * Vrai sous le seuil passé — 768 px par défaut, celui de shadcn.
 *
 * Le seuil est un PARAMÈTRE et non plus une constante privée : la barre
 * latérale bascule en tiroir à 900 px, et la valeur doit venir de l'appelant
 * pour rester accordée à la requête de média qui montre le panneau.
 */
export function useIsMobile(seuil = MOBILE_BREAKPOINT) {
  const [isMobile, setIsMobile] = React.useState(undefined)

  React.useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${seuil - 1}px)`)
    const onChange = () => {
      setIsMobile(window.innerWidth < seuil)
    }
    mql.addEventListener("change", onChange)
    setIsMobile(window.innerWidth < seuil)
    return () => mql.removeEventListener("change", onChange)
  }, [seuil])

  return !!isMobile
}
