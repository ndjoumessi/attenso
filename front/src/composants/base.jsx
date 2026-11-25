// Les primitives de la vitrine. Écrites à la main, et c'est une décision :
// 21st.dev et les trois registres shadcn rendent des composants qui supposent
// une palette Tailwind par défaut (slate, zinc, primary). Attenso a la sienne,
// dont chaque couleur est MESURÉE contre son fond et revérifiée par un test.
// Y coller un composant du catalogue demanderait de réécrire ses classes une
// par une — plus long que de l'écrire, et au prix d'une seconde source de
// couleurs qui dériverait sans que le test s'en aperçoive.

export function Colonne({ children, className = '' }) {
  return <div className={`mx-auto w-full max-w-5xl px-5 sm:px-8 ${className}`}>{children}</div>;
}

/**
 * Un appel à l'action.
 *
 * La hauteur ne descend jamais sous 44 px, plancher d'une cible tactile, et
 * `touch-action: manipulation` retire le délai de 300 ms que les navigateurs
 * mobiles gardent pour guetter un double-tap.
 */
export function Appel({ href, children, variante = 'plein', taille = 'normal' }) {
  const formes = {
    // L'ombre monte d'un cran au survol : le bouton se soulève sous le doigt,
    // et `active` le repose. Un simple éclaircissement ne disait pas « cliquable ».
    plein: 'bg-accent text-sur-accent border-accent shadow-1 hover:shadow-2 '
      + 'hover:brightness-110 active:translate-y-px active:shadow-1 active:brightness-95',
    discret: 'bg-transparent text-accent-texte border-bordure-controle '
      + 'hover:border-accent hover:bg-accent-voile active:bg-accent-voile',
  };
  const tailles = {
    normal: 'min-h-[var(--cible)] px-8 text-lg',
    petit: 'min-h-[var(--cible-min)] px-4 text-base',
  };
  return (
    <a
      href={href}
      className={`inline-flex items-center justify-center gap-2 rounded-[var(--rayon)]
        border font-semibold no-underline [touch-action:manipulation]
        transition-[filter,background-color,border-color,box-shadow,transform]
        duration-[var(--duree-courte)] ease-[var(--courbe-entree)]
        focus-visible:outline-2 focus-visible:outline-offset-2
        focus-visible:outline-[var(--anneau)]
        ${formes[variante]} ${tailles[taille]}`}
    >
      {children}
    </a>
  );
}

export function Fleche() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/** Une bande de page : le rythme vertical du site tient dans cette seule classe. */
export function Bande({ children, id, aria, className = '' }) {
  return (
    <section id={id} aria-labelledby={aria}
             className={`py-[var(--pas-16)] sm:py-[var(--pas-24)] ${className}`}>
      <Colonne>{children}</Colonne>
    </section>
  );
}

/** 60 à 75 caractères par ligne : au-delà, l'œil perd la ligne suivante. */
export function Mesure({ children, className = '' }) {
  return <div className={`max-w-[38rem] ${className}`}>{children}</div>;
}
