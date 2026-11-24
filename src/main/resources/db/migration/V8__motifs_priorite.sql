-- EF-407 : priorités paramétrables au-delà de l'urgence.
--
-- Une table par clinique plutôt qu'une énumération dans le code : « personne
-- âgée » et « personne handicapée » sont les exemples du cahier des charges,
-- pas la liste. Une clinique rurale voudra « animal de ferme en bétaillère »,
-- une clinique urbaine « chat en caisse de transport depuis 40 min ». Figer la
-- liste obligerait à déployer pour ajouter un motif.
CREATE TABLE motif_priorite (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  libelle     text NOT NULL,
  rang        smallint NOT NULL,
  ordre       smallint NOT NULL DEFAULT 0,
  actif       boolean NOT NULL DEFAULT true,
  cree_le     timestamptz NOT NULL DEFAULT now(),

  -- Bornes de la bande « prioritaire » : au-dessus de la normale (0), en
  -- dessous de l'urgence (10). Un motif qui atteindrait 10 doublerait une
  -- urgence vitale, ce qu'aucun réglage ne doit permettre.
  CONSTRAINT motif_rang_dans_la_bande CHECK (rang BETWEEN 1 AND 9),
  CONSTRAINT motif_libelle_non_vide   CHECK (length(trim(libelle)) > 0),
  CONSTRAINT motif_unique_par_clinique UNIQUE (tenant_id, libelle)
);

CREATE INDEX motif_priorite_actifs ON motif_priorite (tenant_id, ordre)
  WHERE actif;

ALTER TABLE motif_priorite ENABLE ROW LEVEL SECURITY;
CREATE POLICY motif_priorite_cloisonnement ON motif_priorite
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON motif_priorite TO ${roleApplicatif};

-- Le motif est copié sur le ticket, pas référencé : il doit rester lisible
-- dans le journal et l'export même si la clinique renomme ou supprime le motif
-- ensuite. Un libellé affiché à l'accueil n'est pas une clé étrangère.
ALTER TABLE ticket ADD COLUMN motif_priorite text;

-- Les deux exemples du cahier des charges, pour toute clinique déjà installée.
-- Elles peuvent les renommer, les désactiver ou en ajouter.
INSERT INTO motif_priorite (tenant_id, libelle, rang, ordre)
SELECT id, 'Personne âgée', 5, 1 FROM tenant
UNION ALL
SELECT id, 'Personne handicapée', 5, 2 FROM tenant;
