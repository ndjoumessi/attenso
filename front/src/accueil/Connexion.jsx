import { useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
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
      localStorage.setItem(CLE_JETON, reponse.jeton);
      localStorage.setItem(CLE_SLUG, reponse.slug);
      localStorage.setItem(CLE_CLINIQUE, reponse.clinique);
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
    <main className="mx-auto w-full max-w-md px-5 py-10">
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
    </main>
  );
}
