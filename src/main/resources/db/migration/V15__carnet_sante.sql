-- EF-906 : carnet de santé, QR carnet à jeton révocable, rappels de vaccins.
--
-- UNE CONTRADICTION DU CAHIER DES CHARGES, ET LA LECTURE RETENUE.
-- EC-05 dit « aucune donnée de santé n'est traitée en V0, V1a ni V1b », et
-- EF-906 — classé V1b — demande un carnet de santé. Les deux ne peuvent pas
-- être vrais ensemble. Le §3.4 tranche : il met hors périmètre « toute donnée
-- de santé HUMAINE, et tout hébergement HDS ». Un carnet de vaccination de
-- chat est une donnée de santé ANIMALE, qui n'est pas une catégorie
-- particulière au sens du RGPD et n'appelle aucun agrément HDS. C'est cette
-- lecture qui est implémentée. Si l'intention était d'exclure aussi le
-- vétérinaire, il faut retirer EF-906 du périmètre, pas l'écrire à moitié.
CREATE TABLE animal (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  nom           text NOT NULL CHECK (length(trim(nom)) > 0),
  espece        text NOT NULL,
  -- Le mobile du propriétaire, pour les rappels de vaccin. Il N'EST PAS purgé
  -- à J+1 comme celui d'un ticket : un rappel de vaccin se déclenche des mois
  -- plus tard, et l'effacer viderait la fonction de son sens. La finalité
  -- n'est pas la même, donc la durée de conservation non plus — c'est
  -- exactement ce que le RGPD demande d'articuler.
  telephone_e164 text,
  -- Le jeton du QR imprimé sur la médaille ou collé dans le carnet papier.
  -- RÉVOCABLE : un QR se photographie, et le propriétaire doit pouvoir couper
  -- l'accès sans qu'on lui refasse sa médaille.
  jeton_carnet  text UNIQUE,
  jeton_pose_le timestamptz,
  actif         boolean NOT NULL DEFAULT true,
  cree_le       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX animal_par_clinique ON animal (tenant_id, nom);
CREATE INDEX animal_par_telephone ON animal (tenant_id, telephone_e164)
  WHERE telephone_e164 IS NOT NULL;

CREATE TABLE vaccination (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  animal_id   uuid NOT NULL REFERENCES animal(id) ON DELETE CASCADE,
  vaccin      text NOT NULL CHECK (length(trim(vaccin)) > 0),
  fait_le     date NOT NULL,
  -- NULL = pas de rappel prévu pour ce vaccin (primo-injection unique).
  rappel_le   date,
  rappel_envoye_le timestamptz,
  note        text,
  cree_le     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT vaccination_rappel_apres CHECK (rappel_le IS NULL OR rappel_le >= fait_le)
);
CREATE INDEX vaccination_par_animal ON vaccination (animal_id, fait_le DESC);
-- Les rappels à envoyer : fraction minuscule de la table.
CREATE INDEX vaccination_a_rappeler ON vaccination (rappel_le)
  WHERE rappel_le IS NOT NULL AND rappel_envoye_le IS NULL;

ALTER TABLE animal ENABLE ROW LEVEL SECURITY;
CREATE POLICY animal_cloisonnement ON animal
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON animal TO ${roleApplicatif};

ALTER TABLE vaccination ENABLE ROW LEVEL SECURITY;
CREATE POLICY vaccination_cloisonnement ON vaccination
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON vaccination TO ${roleApplicatif};

-- Même mécanisme que pour les tickets et les rendez-vous : connaître la
-- clinique AVANT de pouvoir lier le tenant.
CREATE OR REPLACE FUNCTION attenso_tenant_de_carnet(jeton text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER
  SET search_path = public, pg_temp
AS $$ SELECT tenant_id FROM animal WHERE jeton_carnet = jeton AND actif $$;

REVOKE ALL ON FUNCTION attenso_tenant_de_carnet(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION attenso_tenant_de_carnet(text) TO ${roleApplicatif};
