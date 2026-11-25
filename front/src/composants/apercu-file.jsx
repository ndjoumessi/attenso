/**
 * Une figure de la file du jour, pour la page d'accueil.
 *
 * <p>DESSINÉE, pas capturée. Une capture serait juste dans un thème et fausse
 * dans l'autre — la page suit `prefers-color-scheme` —, ajouterait une requête
 * et du poids sur une page tenue à une seconde, et se périmerait à la première
 * retouche de couleur.
 *
 * <p>La contrepartie d'un dessin, c'est qu'il peut mentir là où une capture ne
 * le peut pas. Ce n'est pas théorique : la première version affichait deux
 * départs alors que la journée de la clinique de démonstration n'en compte
 * aucun. Les onze valeurs sont maintenant tenues par un test.
 *
 * <p>Les rôles sont ceux de `accueil/File.jsx` — ambre pour le prévenu et le
 * motif, `border-accent` pour la rangée appelée. Une figure qui invente ses
 * propres teintes ne montre pas le produit : elle montre un autre écran, et
 * une retouche des jetons ne la suivrait pas.
 *
 * <p>Seul le DESSIN porte `aria-hidden` : lu à voix haute, il annoncerait de
 * vrais patients en attente sur une page commerciale. La légende, elle, est
 * lue — sans elle, la figure n'existe pas pour un lecteur d'écran, et le
 * héros retombe sur le défaut qu'elle corrige.
 */

const TUILES = [
  ['En salle', '4'],
  ['Partis sans être vus', '0'],
  ['Consultation médiane', '17 min'],
];

const LIGNES = [
  { numero: '7', etat: 'appelé', ton: 'accent', motif: 'Chat stressé en caisse', attente: '13 min' },
  { numero: '8', etat: 'prévenu', ton: 'attention', motif: 'Personne âgée', attente: '11 min' },
  { numero: '9', etat: 'en attente', ton: 'doux', attente: '6 min' },
];

/** Mêmes tons que `ETIQUETTES` de `accueil/File.jsx`. */
const TONS = {
  accent: 'border-accent bg-accent-voile text-accent-texte',
  attention: 'border-attention bg-attention-voile text-attention',
  doux: 'border-bordure-douce text-sur-fond-doux',
};

function Etiquette({ ton, children }) {
  return (
    <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${TONS[ton]}`}>
      {children}
    </span>
  );
}

export function ApercuFile() {
  return (
    <figure className="m-0">
      <div aria-hidden="true"
           className="rounded-[var(--rayon-lg)] border border-bordure-douce bg-surface
                      p-4 shadow-2 sm:p-5">
        <div className="mb-4 grid grid-cols-3 gap-2 sm:gap-3">
          {TUILES.map(([libelle, valeur]) => (
            <div key={libelle}
                 className="rounded-[var(--rayon)] border border-bordure-douce
                            bg-surface-douce px-3 py-2">
              <span className="block text-xs font-semibold uppercase tracking-wide
                               text-sur-fond-doux">
                {libelle}
              </span>
              <b className="text-xl font-bold tabular-nums tracking-tight sm:text-2xl">
                {valeur}
              </b>
            </div>
          ))}
        </div>

        <ul className="space-y-2">
          {LIGNES.map((ligne) => (
            <li key={ligne.numero}
                className={`flex flex-wrap items-center gap-2 rounded-[var(--rayon)] border
                            px-3 py-2 ${ligne.ton === 'accent'
                              ? 'border-accent bg-accent-voile'
                              : ligne.ton === 'attention'
                                ? 'border-attention'
                                : 'border-bordure-douce'}`}>
              <b className="w-6 text-lg font-bold tabular-nums tracking-tight">{ligne.numero}</b>
              <Etiquette ton={ligne.ton}>{ligne.etat}</Etiquette>
              {ligne.motif && <Etiquette ton={ligne.ton}>{ligne.motif}</Etiquette>}
              <small className="ml-auto text-xs tabular-nums text-sur-fond-doux">
                {ligne.attente}
              </small>
            </li>
          ))}
        </ul>
      </div>
      <figcaption className="mt-3 text-sm text-sur-fond-doux">
        L'écran que votre accueil aura sous les doigts.
      </figcaption>
    </figure>
  );
}
