import { useEffect, useRef } from 'react';
import { MANQUE, PageLegale, useEditeur } from './composants/legal.jsx';

/**
 * Une page légale dont le corps est du texte juridique AUTHENTIFIÉ.
 *
 * <p>Ce texte n'est pas transcrit en JSX, et c'est une décision : des
 * conditions générales et une politique de confidentialité engagent, et les
 * recopier à la main dans un autre langage de balisage, c'est accepter qu'une
 * virgule se perde en route. Il reste donc dans un fichier HTML, importé en
 * brut à la CONSTRUCTION — aucune donnée de requête n'y entre jamais, ce qui
 * est la seule condition qui rende cette insertion sûre.
 *
 * <p>Deux éléments y sont renseignés depuis le serveur, comme avant : la
 * dénomination de l'éditeur et son adresse de contact. Elles ne vivent pas
 * dans le dépôt, qui est public.
 */
export function ProseLegale({ titre, html }) {
  const editeur = useEditeur();
  const hote = useRef(null);

  useEffect(() => {
    if (!hote.current) return;
    const denomination = hote.current.querySelector('#denomination');
    if (denomination) {
      denomination.textContent = editeur.denomination?.trim() || MANQUE;
    }
    for (const lien of hote.current.querySelectorAll('[data-courriel]')) {
      lien.textContent = editeur.email || MANQUE;
      if (editeur.email) lien.href = `mailto:${editeur.email}`;
    }
  }, [editeur]);

  return (
    <PageLegale titre={titre}>
      <div ref={hote} dangerouslySetInnerHTML={{ __html: html }} />
    </PageLegale>
  );
}
