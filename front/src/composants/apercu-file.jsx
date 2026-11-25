/**
 * Une figure de la file du jour, pour la page d'accueil.
 *
 * <p>DESSINÉE, pas capturée. Une capture serait juste dans un thème et fausse
 * dans l'autre — la page suit `prefers-color-scheme` —, ajouterait une requête
 * et du poids sur une page tenue à une seconde, et se périmerait à la première
 * retouche de couleur. Celle-ci lit les mêmes jetons que le produit.
 *
 * <p>La contrepartie d'un dessin, c'est qu'il peut mentir là où une capture ne
 * le peut pas. Les chiffres sont donc ceux de la clinique de démonstration, et
 * un test les tient.
 *
 * <p>`aria-hidden` sur la figure entière : lue à voix haute, elle annoncerait
 * de vrais patients en attente sur une page commerciale. C'est le texte autour
 * qui porte le sens.
 */

const TUILES = [
  ['En salle', '4'],
  ['Partis sans être vus', '2'],
  ['Consultation médiane', '17 min'],
];

const LIGNES = [
  { numero: '7', etat: 'appelé', motif: 'Chat stressé en caisse', attente: '13 min', prioritaire: true },
  { numero: '8', etat: 'prévenu', motif: 'Personne âgée', attente: '11 min' },
  { numero: '9', etat: 'en attente', attente: '6 min' },
];

function Pastille({ children, accent = false }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
      accent ? 'bg-accent-voile text-accent-texte' : 'bg-surface-douce text-sur-fond-doux'}`}>
      {children}
    </span>
  );
}

export function ApercuFile() {
  return (
    <div aria-hidden="true"
         className="rounded-[var(--rayon-lg)] border border-bordure-douce bg-surface
                    p-4 shadow-[0_24px_60px_-28px_rgb(0_0_0/0.28)] sm:p-5">
      <div className="mb-4 grid grid-cols-3 gap-2 sm:gap-3">
        {TUILES.map(([libelle, valeur]) => (
          <div key={libelle}
               className="rounded-[var(--rayon)] border border-bordure-douce
                          bg-surface-douce px-3 py-2">
            <span className="block text-[11px] uppercase tracking-wide text-sur-fond-doux">
              {libelle}
            </span>
            <b className="text-xl font-bold tracking-tight sm:text-2xl">{valeur}</b>
          </div>
        ))}
      </div>

      <ul className="space-y-2">
        {LIGNES.map((ligne) => (
          <li key={ligne.numero}
              className={`flex flex-wrap items-center gap-2 rounded-[var(--rayon)] border
                          px-3 py-2 ${ligne.prioritaire
                            ? 'border-accent-voile bg-accent-voile/40'
                            : 'border-bordure-douce bg-surface'}`}>
            <b className="w-6 text-lg font-bold tracking-tight">{ligne.numero}</b>
            <Pastille accent={ligne.prioritaire}>{ligne.etat}</Pastille>
            {ligne.motif && <Pastille accent>{ligne.motif}</Pastille>}
            <small className="ml-auto text-xs text-sur-fond-doux">{ligne.attente}</small>
          </li>
        ))}
      </ul>
    </div>
  );
}
