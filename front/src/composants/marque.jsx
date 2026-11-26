// La marque d'Attenso : trois traits décroissants, une file qui raccourcit.
//
// L'application en portait DEUX. L'icône installée — celle du manifeste PWA —
// dessinait déjà ces trois barres ; l'en-tête de la vitrine, lui, dessinait
// une horloge. Le site et l'icône ne se ressemblaient pas.
//
// La géométrie vit ici et dans `static/app/icone.svg`, et une garde vérifie
// que les deux disent la même chose — deux dessins qui se ressemblent
// aujourd'hui divergent demain, ce qui vient précisément d'arriver.

/**
 * Les trois traits, en coordonnées d'un carré de 24.
 *
 * Tracés et non remplis : c'est le vocabulaire des autres icônes du site, et
 * un trait arrondi garde sa forme à 16 px comme à 512.
 */
export const TRAITS = ['M5 7h13', 'M5 12h9', 'M5 17h5'];

export function Marque({ taille = 28, className = '' }) {
  return (
    <svg width={taille} height={taille} viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth="2.75" strokeLinecap="round"
         aria-hidden="true" className={`shrink-0 ${className}`}>
      {TRAITS.map((d) => <path key={d} d={d} />)}
    </svg>
  );
}
