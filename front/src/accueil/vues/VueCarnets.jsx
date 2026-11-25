import { AIDE } from '../../composants/formulaire.jsx';
import { Carnets } from '../Sections.jsx';

/**
 * La vue Carnets de santé : le formulaire d'ajout, la liste, et quoi dire
 * quand il n'y a encore aucun animal.
 *
 * <p>Elle enveloppe la section sans la modifier. Le formulaire est AU-DESSUS
 * de la liste : l'état vide le désigne donc comme « ci-dessus », et dit ce que
 * produit le QR, parce que c'est ce qu'une clinique neuve cherche à savoir.
 */
export function VueCarnets({ jeton, surErreur }) {
  return (
    <>
      <p className={AIDE}>
        Le carnet de chaque animal, auquel son propriétaire accède en scannant
        un QR code.
      </p>
      <Carnets jeton={jeton} surErreur={surErreur} vide={(
        <p className={`${AIDE} rounded-[var(--rayon)] border border-dashed
                       border-bordure-douce p-4`}>
          Aucun carnet de santé pour l'instant. Ajoutez un premier animal avec
          le formulaire ci-dessus : en laissant « Émettre un QR de carnet »
          coché, son propriétaire retrouvera le carnet en le scannant.
        </p>
      )} />
    </>
  );
}
