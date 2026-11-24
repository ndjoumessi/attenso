import * as React from "react"

const MOBILE_BREAKPOINT = 768

/** La requête de média du seuil. `max-width` est lu depuis Safari 9. */
const requete = (seuil) =>
  // 0,02 px sous le seuil : l'épaisseur conventionnelle qui exclut le seuil
  // lui-même sans ouvrir de trou devant les largeurs fractionnaires.
  (typeof window === "undefined" || !window.matchMedia
    ? null
    : window.matchMedia(`(max-width: ${seuil - 0.02}px)`))

/**
 * Vrai sous le seuil passé — 768 px par défaut, celui de shadcn.
 *
 * Le seuil est un PARAMÈTRE et non plus une constante privée : la barre
 * latérale bascule en tiroir à 900 px, et depuis la correction du défaut
 * Safari 16.0 ce prédicat est la SEULE autorité sur la forme de la barre —
 * aucune requête de média en CSS ne le double.
 */
export function useIsMobile(seuil = MOBILE_BREAKPOINT) {
  // Lu DÈS LA PREMIÈRE RENDITION, et non dans un effet.
  //
  // La version de shadcn partait d'`undefined` et attendait l'effet, qui court
  // après la première peinture : la barre s'affichait donc un instant dans sa
  // forme « large » sur un téléphone. Tant que c'était le CSS qui tranchait,
  // cela ne se voyait pas ; depuis que c'est ce prédicat qui décide seul, le
  // clignotement serait visible à chaque chargement de l'écran central.
  const [isMobile, setIsMobile] = React.useState(() => !!requete(seuil)?.matches)

  React.useEffect(() => {
    // UNE SEULE source de vérité : `mql.matches`, la requête elle-même.
    //
    // La version de shadcn s'abonnait à `max-width: seuil - 1` mais décidait
    // sur `innerWidth < seuil` — deux prédicats qui ne coïncident pas sur les
    // largeurs fractionnaires que produisent le zoom du navigateur et la mise
    // à l'échelle du système. À 899,5 px la requête était fausse et la
    // décision vraie : un redimensionnement vers 1200 px ne traversait alors
    // aucune frontière, donc aucun événement n'était émis, et l'interface
    // restait en mode tiroir à 1200 px avec son bouton masqué.
    const mql = requete(seuil)
    if (!mql) return undefined
    const onChange = () => setIsMobile(mql.matches)
    mql.addEventListener("change", onChange)
    onChange()
    return () => mql.removeEventListener("change", onChange)
  }, [seuil])

  return !!isMobile
}
