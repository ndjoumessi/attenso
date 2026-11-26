
import { ANNEAU } from './formulaire.jsx';// La coquille des pages publiques : le retour, et la bande légale.
//
// Les deux étaient recopiés page par page — la bande trois fois, le retour
// quatre — et une seule copie portait les classes qui donnent 44 px. Mesuré
// en production : le pied de la vitrine rendait cinq cibles de 17 px de haut,
// et la coquille des pages légales autant.
//
// `cible-min` règle une hauteur MINIMALE, sans aucun effet sur un élément en
// ligne : il faut `inline-flex` pour qu'elle s'applique. C'est l'erreur qui a
// donné 17 px alors que la bonne classe était bien posée, et la raison pour
// laquelle ces liens vivent désormais à un seul endroit.
//
// OÙ LE PLANCHER S'ARRÊTE, parce que la question revient. ENF-09 exige 44 px
// pour les cibles tactiles ; WCAG 2.2 SC 2.5.8 exempte les liens posés DANS
// une phrase, et les grossir casserait la ligne. Restent donc sous 44 px, et
// à bon droit, trois liens mesurés : « contact@attenso.fr » dans « Une
// question d'abord ? … », et « politique de confidentialité » et « GitHub »
// dans le corps des pages légales. La règle : coquille et actions au
// plancher, prose exemptée.

const LIEN = 'cible-min inline-flex items-center text-accent-texte underline '
  + `underline-offset-2 ${ANNEAU}`;

/** Les trois pages légales, joignables depuis toute page. */
const LEGAL = [
  ['/mentions-legales', 'Mentions légales'],
  ['/cgv', 'Conditions générales'],
  ['/confidentialite', 'Confidentialité'],
];

/**
 * La sortie d'une page publique.
 *
 * <p>Par défaut le retour au site ; la réinitialisation, elle, renvoie vers
 * la connexion, parce que c'est là que mène un mot de passe qu'on vient de
 * changer.
 */
export function Retour({ href = '/', children = 'Attenso', className = '' }) {
  return (
    <a href={href} className={`cible-min mb-4 inline-flex items-center gap-1.5
                               text-sm font-semibold text-accent-texte ${className} ${ANNEAU}`}>
      <span aria-hidden="true">←</span> {children}
    </a>
  );
}

/**
 * La bande légale. `variante` ne change que l'arrangement, jamais les
 * classes du lien : ce sont elles qui portent le plancher tactile.
 */
export function BandeLegale({ variante = 'centree' }) {
  const liste = variante === 'rangee'
    ? 'flex flex-wrap gap-x-6 gap-y-2 text-sm'
    : 'mt-6 flex flex-wrap justify-center gap-x-5 gap-y-2 text-xs text-sur-fond-doux';
  return (
    <ul className={liste}>
      {LEGAL.map(([href, texte]) => (
        <li key={href}>
          <a href={href} className={LIEN}>{texte}</a>
        </li>
      ))}
    </ul>
  );
}
