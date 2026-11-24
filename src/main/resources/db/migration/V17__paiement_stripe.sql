-- Souscription payante : l'abonnement cesse d'être une ligne déclarative pour
-- devenir le reflet d'un abonnement Stripe.
--
-- Pourquoi Stripe plutôt qu'un encaissement maison : aucune donnée de carte
-- n'entre jamais dans Attenso. Le client paie sur une page hébergée par
-- Stripe, et ce dépôt n'a donc rien à protéger de ce côté — ce qui est
-- cohérent avec un produit dont toute la position tient en « nous ne
-- collectons qu'un mobile et un numéro de ticket ».

ALTER TABLE abonnement
  -- EN_ATTENTE : la clinique est créée, la page de paiement est ouverte, mais
  -- Stripe n'a pas encore confirmé. Cet état existe parce que l'inscription et
  -- le paiement ne peuvent pas être atomiques : entre les deux, le client peut
  -- fermer l'onglet.
  --
  -- IMPAYE ne coupe RIEN du service. Voir ServicePaiement : couper une file
  -- d'attente un mardi matin parce qu'une carte a expiré laisserait de vraies
  -- personnes debout dans une vraie salle d'attente.
  ADD COLUMN statut text NOT NULL DEFAULT 'ACTIF'
    CHECK (statut IN ('EN_ATTENTE', 'ACTIF', 'IMPAYE', 'RESILIE')),
  ADD COLUMN stripe_client_id     text,
  ADD COLUMN stripe_abonnement_id text;

-- Un abonnement Stripe ne vaut que pour une clinique. L'index est partiel
-- parce que les abonnements d'avant Stripe — et ceux en attente de paiement —
-- n'en portent aucun.
CREATE UNIQUE INDEX abonnement_stripe_unique
  ON abonnement (stripe_abonnement_id)
  WHERE stripe_abonnement_id IS NOT NULL;

-- Le webhook arrive SANS contexte de clinique : Stripe ne connaît pas nos
-- identifiants de cloisonnement. Même parade que pour les références de
-- tickets et de rendez-vous — une fonction SECURITY DEFINER qui ne rend qu'un
-- identifiant de clinique, et rien d'autre.
CREATE OR REPLACE FUNCTION attenso_tenant_de_abonnement_stripe(abonnement_stripe text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT tenant_id FROM abonnement
  WHERE stripe_abonnement_id = abonnement_stripe
  LIMIT 1;
$$;

REVOKE ALL ON FUNCTION attenso_tenant_de_abonnement_stripe(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION attenso_tenant_de_abonnement_stripe(text) TO ${roleApplicatif};

-- Idempotence des webhooks. Stripe REJOUE ses événements — à la moindre
-- réponse lente ou en erreur — et sans cette table un « paiement échoué »
-- rejoué trois fois enverrait trois relances, ou pire, un changement de
-- formule serait appliqué deux fois.
--
-- Pas de cloisonnement RLS sur cette table, et c'est délibéré : elle ne
-- contient AUCUNE donnée de clinique, seulement des identifiants d'événements
-- Stripe et leur type. Lui donner une colonne tenant_id obligerait le webhook
-- à résoudre la clinique avant même de savoir s'il doit traiter l'événement.
CREATE TABLE paiement_evenement (
  id       text PRIMARY KEY,
  type     text NOT NULL,
  recu_le  timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT ON paiement_evenement TO ${roleApplicatif};

-- Purge : garder un an d'événements suffit largement à diagnostiquer un
-- doublon, et Stripe ne rejoue jamais au-delà de quelques jours.
CREATE INDEX paiement_evenement_par_date ON paiement_evenement (recu_le);
