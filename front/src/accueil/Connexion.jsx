import { useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import { vider as viderJournal } from '../../../src/main/resources/static/app/journal.js';
import {
  doitOublierLeJournal,
} from '../../../src/main/resources/static/app/vue-file.js';
import { BandeLegale, Retour } from '../composants/chrome.jsx';
import { AIDE, CHAMP, ETIQUETTE, Erreur, Info, Principal } from '../composants/formulaire.jsx';
import { CLE_CLINIQUE, CLE_JETON, CLE_SLUG } from './session.js';

export function Connexion({ surConnexion }) {
  const [erreur, setErreur] = useState(null);
  const [oubliEnvoye, setOubliEnvoye] = useState(false);
  const [envoi, setEnvoi] = useState(false);

  async function entrer(evenement) {
    evenement.preventDefault();
    const f = evenement.currentTarget;
    setEnvoi(true);
    setErreur(null);
    try {
      const reponse = await appel('/api/connexion', {
        method: 'POST',
        body: JSON.stringify({
          slug: f.slug.value.trim(),
          email: f.email.value.trim(),
          motDePasse: f.mdp.value,
        }),
      });
      // Clinique différente : on ne garde ni le tableau ni les gestes en
      // attente de la précédente. Les laisser montrerait la file de l'une à
      // l'autre sur la même tablette, et rejouerait les gestes de la première
      // avec le jeton de la seconde.
      if (doitOublierLeJournal(localStorage.getItem(CLE_SLUG), reponse.slug)) {
        await viderJournal();
      }
      localStorage.setItem(CLE_JETON, reponse.jeton);
      localStorage.setItem(CLE_SLUG, reponse.slug);
      localStorage.setItem(CLE_CLINIQUE, reponse.clinique);
      // Le mot de passe quitte la mémoire de la page dès qu'il est parti.
      f.mdp.value = '';
      surConnexion(reponse.jeton);
    } catch (echec) {
      setErreur(echec.reseau ? 'Serveur injoignable.' : echec.message);
      setEnvoi(false);
    }
  }

  async function oublier(formulaire) {
    const slug = formulaire.slug.value.trim();
    const email = formulaire.email.value.trim();
    if (!slug || !email) {
      setErreur('Indiquez la clinique et votre adresse, puis redemandez le lien.');
      return;
    }
    try {
      await appel('/api/connexion/oubli', {
        method: 'POST',
        body: JSON.stringify({ slug, email }),
      });
    } catch { /* la réponse est la même dans tous les cas, voir ci-dessous */ }
    setOubliEnvoye(true);
  }

  return (
    // `min-h-dvh` et non `min-h-screen` : sur mobile, `100vh` compte la barre
    // d'adresse qui se rétracte, et la mise en page saute au défilement.
    //
    // Centré sans condition. J'avais d'abord réservé le centrage aux écrans
    // larges, par crainte qu'un téléphone en paysage ne voie le haut de la
    // carte rogné. Mesuré à 844 × 390 : la carte commence à +40 px, la page
    // défile, le haut reste atteignable — quand le contenu dépasse, la rangée
    // de grille grandit et le centrage n'a plus d'effet. La précaution était
    // inutile, et la condition portait d'ailleurs sur la LARGEUR là où le
    // risque venait de la hauteur.
    <main className="grid min-h-dvh place-items-center px-5 py-10">
      {/* `shadow-2` et non une ombre écrite à la main : `jetons.css` n'expose
          que deux niveaux d'élévation, et une valeur en dur ne suit pas la
          compensation du thème sombre — l'alpha y est multiplié par sept,
          faute de quoi l'ombre est invisible sur le fond. */}
      <div className="w-full max-w-md">
        {/* La seule sortie. On arrive ici depuis la vitrine — son en-tête et
            son pied y mènent tous deux — et rien n'en ressortait : qui atterrit
            là sans compte n'avait que le bouton « précédent ». */}
        <Retour />

        <div className="rounded-[var(--rayon-lg)] border border-bordure-douce
                        bg-surface p-6 shadow-2 sm:p-8">
        <h1 className="text-3xl font-bold tracking-tight">Attenso</h1>
        <p className="mt-1 text-sur-fond-doux">Tableau de l'accueil.</p>

        <form onSubmit={entrer} className="mt-8 space-y-5">
        <div>
          <label className={ETIQUETTE} htmlFor="slug">Clinique</label>
          <input id="slug" name="slug" autoComplete="organization" required
                 className={CHAMP} />
        </div>
        <div>
          <label className={ETIQUETTE} htmlFor="email">Adresse</label>
          <input id="email" name="email" type="email" autoComplete="username"
                 required className={CHAMP} />
        </div>
        <div>
          <label className={ETIQUETTE} htmlFor="mdp">Mot de passe</label>
          <input id="mdp" name="mdp" type="password"
                 autoComplete="current-password" required className={CHAMP} />
        </div>

        <Erreur>{erreur}</Erreur>

        <Principal type="submit" disabled={envoi}>
          {envoi ? 'Un instant…' : 'Entrer'}
        </Principal>

        <p>
          <button type="button"
                  onClick={(e) => oublier(e.currentTarget.form)}
                  className="cible-min text-accent-texte underline">
            Mot de passe oublié ?
          </button>
        </p>

        {/* Même message qu'il y ait un compte ou non : sinon ce formulaire
            devient un annuaire des cliniques clientes. */}
        {oubliEnvoye && (
          <Info>
            Si un compte correspond à cette clinique et à cette adresse, un lien
            de réinitialisation vient d'être envoyé. Il est valable une heure.
          </Info>
        )}
        </form>
        </div>

        {/* Pour qui atterrit ici sans compte. La vitrine mène à la connexion
            par son en-tête ET son pied ; l'inverse n'existait pas. */}
        <p className="mt-5 text-center text-sm text-sur-fond-doux">
          Pas encore de compte ?{' '}
          {/* `inline-flex` et non un lien en ligne : `cible-min` règle une
              hauteur MINIMALE, qui n'a aucun effet sur un élément en ligne.
              Mesuré avant correction : 17 px de haut. */}
          <a href="/inscription" className="cible-min inline-flex items-center
                                            font-semibold text-accent-texte
                                            underline underline-offset-2">
            Inscrire ma clinique
          </a>
        </p>

        <BandeLegale />
      </div>
    </main>
  );
}
