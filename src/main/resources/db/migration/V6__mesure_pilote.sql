-- Instrumentation du pilote (§5.7 du cahier des charges).
--
-- Ces deux objets portent le chiffre qui décide du projet. Le critère d'arrêt
-- de la semaine 10 est un taux d'auto-enregistrement inférieur à 30 % ; sans
-- de quoi le calculer, le pilote ne peut pas être jugé et la décision se
-- prendrait à l'impression, ce que le cahier des charges interdit
-- explicitement.

-- D'où vient le ticket. Jusqu'ici l'information existait en mémoire, le temps
-- d'appliquer le plafond de dépense, puis se perdait : impossible de
-- distinguer après coup un ticket scanné par le client d'un ticket saisi par
-- l'accueil. Le numérateur du taux était donc inconnaissable.
ALTER TABLE ticket ADD COLUMN origine text NOT NULL DEFAULT 'COMPTOIR'
  CHECK (origine IN ('COMPTOIR', 'ACCUEIL'));

-- Le dénominateur, lui, ne peut pas être déduit : le système ne voit que les
-- gens qui prennent un ticket, pas ceux qui franchissent la porte. Seule
-- l'accueil peut le dire, et c'est une saisie d'une seconde en fin de journée.
CREATE TABLE mesure_jour (
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  jour             date NOT NULL,
  arrivants_total  int  NOT NULL CHECK (arrivants_total >= 0),
  saisi_le         timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, jour)
);

ALTER TABLE mesure_jour ENABLE ROW LEVEL SECURITY;
CREATE POLICY mesure_jour_cloisonnement ON mesure_jour
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());

GRANT SELECT, INSERT, UPDATE, DELETE ON mesure_jour TO ${roleApplicatif};
