-- Cache et budget des vérifications HLR.
--
-- Pourquoi une table dédiée plutôt que de se fier aux tickets, comme le faisait
-- la première version : un numéro REFUSÉ ne crée aucun ticket. Les garde-fous
-- qui comptaient des tickets ne comptaient donc rien du tout dans le seul cas
-- qui coûte — un numéro mort martelé donnait des appels payants à l'infini, et
-- le compteur ne bougeait jamais. Le budget doit se compter là où la dépense a
-- lieu.
--
-- La clé primaire fait aussi office de cache : une seconde demande pour le même
-- numéro le même jour ne repaie rien, qu'il ait été accepté ou refusé.
CREATE TABLE verification_numero (
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  jour            date NOT NULL,
  telephone_e164  text NOT NULL,
  verdict         text NOT NULL CHECK (verdict IN ('JOIGNABLE','INJOIGNABLE','INDETERMINE')),
  detail          text,
  cree_le         timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, jour, telephone_e164)
);

-- Le comptage du budget du jour.
CREATE INDEX verification_numero_jour ON verification_numero (tenant_id, jour);

-- Cloisonnement, comme toutes les tables qui portent un tenant_id.
ALTER TABLE verification_numero ENABLE ROW LEVEL SECURITY;
CREATE POLICY verification_numero_cloisonnement ON verification_numero
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());

GRANT SELECT, INSERT, UPDATE, DELETE ON verification_numero TO ${roleApplicatif};
