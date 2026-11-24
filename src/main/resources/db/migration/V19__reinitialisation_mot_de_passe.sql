-- Récupération de mot de passe.
--
-- Sans elle, une clinique qui perd son mot de passe est enfermée dehors
-- DÉFINITIVEMENT : il n'existe aucun autre chemin d'accès au tableau. Pour un
-- produit qu'on vend, c'est au mieux un ticket de support, au pire un client
-- perdu.

CREATE TABLE reinitialisation (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      uuid NOT NULL REFERENCES tenant(id),
  utilisateur_id uuid NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,

  -- L'EMPREINTE du jeton, jamais le jeton. Celui-ci vaut un mot de passe le
  -- temps de sa validité : une base lue par un tiers — sauvegarde égarée,
  -- injection, curiosité d'un exploitant — lui donnerait sinon la main sur
  -- tous les comptes dont une réinitialisation est en cours.
  jeton_hash     text NOT NULL,

  expire_le      timestamptz NOT NULL,
  -- Usage unique : un lien déjà consommé ne doit pas resservir, même s'il
  -- traîne dans une boîte aux lettres ou dans l'historique d'un navigateur.
  utilise_le     timestamptz,
  cree_le        timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT reinitialisation_hash_unique UNIQUE (jeton_hash)
);

CREATE INDEX reinitialisation_par_utilisateur
  ON reinitialisation (utilisateur_id, cree_le DESC);

ALTER TABLE reinitialisation ENABLE ROW LEVEL SECURITY;
CREATE POLICY reinitialisation_cloisonnement ON reinitialisation
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON reinitialisation TO ${roleApplicatif};

-- Le porteur du lien n'a RIEN d'autre que son jeton : ni slug, ni compte. Même
-- parade que pour les références de tickets et de rendez-vous — une fonction
-- SECURITY DEFINER qui ne rend qu'un identifiant de clinique, et seulement à
-- qui détient déjà l'empreinte du jeton.
CREATE OR REPLACE FUNCTION attenso_tenant_de_reinitialisation(empreinte text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT tenant_id FROM reinitialisation
  WHERE jeton_hash = empreinte AND utilise_le IS NULL AND expire_le > now()
  LIMIT 1;
$$;

REVOKE ALL ON FUNCTION attenso_tenant_de_reinitialisation(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION attenso_tenant_de_reinitialisation(text) TO ${roleApplicatif};
