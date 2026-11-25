import { useState } from 'react';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import { AIDE, CHAMP, ETIQUETTE, Erreur, Info, Principal } from './composants/formulaire.jsx';

const PERIME = "Ce lien n'est plus valable. Les liens expirent au bout d'une "
  + "heure et ne servent qu'une fois : redemandez-en un depuis la page de "
  + 'connexion.';

export function Reinitialisation() {
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);
  const [fait, setFait] = useState(false);

  async function soumettre(evenement) {
    evenement.preventDefault();
    const champ = evenement.currentTarget.motDePasse;
    // Le jeton vient de l'URL : c'est le lien du courriel, et rien d'autre ne
    // l'accompagne — ni slug, ni compte.
    const jeton = location.pathname.split('/').filter(Boolean)[1] ?? '';
    setEnvoi(true);
    setErreur(null);
    try {
      const reponse = await appel('/api/connexion/reinitialiser', {
        method: 'POST',
        body: JSON.stringify({ jeton, motDePasse: champ.value }),
      });
      // Le mot de passe quitte la mémoire de la page dès qu'il est parti.
      champ.value = '';
      if (!reponse.fait) {
        // Lien périmé, déjà utilisé, ou inventé : le dire est utile ici, et ne
        // révèle rien — il faut déjà détenir un jeton pour poser la question.
        setErreur(PERIME);
        setEnvoi(false);
        return;
      }
      setFait(true);
    } catch (echec) {
      setErreur(echec.reseau
        ? 'Serveur injoignable : réessayez dans un instant.' : echec.message);
      setEnvoi(false);
    }
  }

  if (fait) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-10">
        <h1 className="text-3xl font-bold tracking-tight">C'est fait</h1>
        <div className="mt-4">
          <Info>Votre mot de passe est changé. Connectez-vous avec le nouveau.</Info>
        </div>
        <a href="/connexion"
           className="mt-6 flex w-full cible items-center justify-center
                      rounded-[var(--rayon)] bg-accent px-6 text-lg font-bold
                      text-sur-accent no-underline shadow-1 hover:brightness-110">
          Me connecter
        </a>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      {/* On arrive ici par un lien de courriel, et souvent sur un lien PÉRIMÉ :
          ils ne valent qu'une heure et qu'une fois. Le message d'expiration
          disait « redemandez-en un depuis la page de connexion » sans jamais y
          mener — la personne devait deviner l'adresse, sur un téléphone, après
          un échec. */}
      <a href="/connexion" className="cible-min mb-4 inline-flex items-center gap-1.5
                                      text-sm font-semibold text-accent-texte">
        <span aria-hidden="true">←</span> Me connecter
      </a>
      <h1 className="text-3xl font-bold tracking-tight">
        Choisir un nouveau mot de passe
      </h1>
      <form onSubmit={soumettre} className="mt-8 space-y-6">
        <div>
          <label className={ETIQUETTE} htmlFor="mdp">Nouveau mot de passe</label>
          <input id="mdp" name="motDePasse" type="password" required minLength={12}
                 maxLength={200} autoComplete="new-password" className={CHAMP} />
          <p className={AIDE}>
            12 caractères au minimum. C'est le seul identifiant de votre
            établissement&nbsp;: notez-le dans votre gestionnaire de mots de passe.
          </p>
        </div>
        <Erreur>{erreur}</Erreur>
        <Principal type="submit" disabled={envoi}>
          {envoi ? 'Un instant…' : 'Enregistrer'}
        </Principal>
      </form>
    </main>
  );
}
