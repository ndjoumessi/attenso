-- Réservation atomique du budget de vérifications HLR.
--
-- La version précédente comptait les lignes de verification_numero puis
-- appelait l'opérateur. Entre les deux, rien n'empêchait dix requêtes
-- simultanées de lire toutes le même compteur à zéro et de partir ensemble :
-- le plafond ne bornait plus que la latence. Un compteur de ligne mis à jour
-- par un UPDATE conditionnel est verrouillé par PostgreSQL le temps de la
-- transaction, ce qui rend la réservation indivisible.
CREATE TABLE budget_hlr (
  tenant_id  uuid NOT NULL REFERENCES tenant(id),
  jour       date NOT NULL,
  appels     int  NOT NULL DEFAULT 0 CHECK (appels >= 0),
  PRIMARY KEY (tenant_id, jour)
);

ALTER TABLE budget_hlr ENABLE ROW LEVEL SECURITY;
CREATE POLICY budget_hlr_cloisonnement ON budget_hlr
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());

GRANT SELECT, INSERT, UPDATE, DELETE ON budget_hlr TO ${roleApplicatif};
