import { useEffect, useState } from 'react';

/** Ce qui manque est dit, pas masqué : un blanc dans des mentions légales
 *  ressemble à une page en règle, et ne l'est pas. */
export const MANQUE = 'à compléter';

/**
 * L'identité de l'éditeur, chargée depuis le serveur.
 *
 * Elle ne vit PAS dans le HTML : ce dépôt est public, et une raison sociale,
 * un siège et un nom de directeur de publication n'ont rien à faire dans un
 * historique Git que personne ne peut effacer.
 */
export function useEditeur() {
  const [editeur, setEditeur] = useState({});
  useEffect(() => {
    fetch('/api/editeur')
      .then((r) => (r.ok ? r.json() : {}))
      .then(setEditeur)
      // Hors réseau, la page reste lisible : seuls les champs d'identité
      // manquent, et le reste du texte est ce qui engage.
      .catch(() => {});
  }, []);
  return editeur;
}

export function Valeur({ children }) {
  const vide = !children || !String(children).trim();
  return (
    <span className={vide ? 'text-sur-fond-doux italic' : undefined}>
      {vide ? MANQUE : children}
    </span>
  );
}

/** La mise en page commune aux trois pages légales. */
export function PageLegale({ titre, children }) {
  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-10">
      <p className="text-sm">
        <a href="/" className="text-accent-texte underline">← Attenso</a>
      </p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">{titre}</h1>
      {/* La mesure est bornée à 70 caractères : au-delà, l'œil perd la ligne
          suivante, et un texte juridique est déjà assez difficile à suivre. */}
      <div className="mt-8 max-w-[70ch] space-y-4 leading-relaxed
                      [&_a]:text-accent-texte [&_a]:underline
                      [&_dd]:mb-2 [&_dt]:mt-3 [&_dt]:font-semibold
                      [&_h2]:mt-10 [&_h2]:text-xl [&_h2]:font-bold
                      [&_h3]:mt-6 [&_h3]:font-semibold
                      [&_li]:mt-1 [&_ul]:list-disc [&_ul]:pl-6">
        {children}
      </div>
      <p className="mt-12 text-sm text-sur-fond-doux">
        <a href="/mentions-legales" className="text-accent-texte underline">
          Mentions légales</a>{' · '}
        <a href="/cgv" className="text-accent-texte underline">
          Conditions générales</a>{' · '}
        <a href="/confidentialite" className="text-accent-texte underline">
          Confidentialité</a>
      </p>
    </main>
  );
}
