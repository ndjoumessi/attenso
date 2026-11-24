-- EF-902 : abonnement par palier de volume de visites, SMS inclus.
--
-- La facturation suit le VOLUME DE VISITES et non le nombre de praticiens :
-- c'est le volume qui induit le coût, puisque chaque visite déclenche des SMS
-- payants. Facturer les praticiens ferait payer cher une clinique à trois
-- vétérinaires et peu de passage, et bradé un centre à flux continu.
CREATE TABLE abonnement (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  palier      text NOT NULL CHECK (palier IN ('COMPTOIR', 'CLINIQUE', 'CENTRE')),
  debut       date NOT NULL,
  -- NULL = en cours. Un abonnement fermé garde sa trace : un client conserve
  -- son prix sur son palier, et il faut pouvoir montrer lequel s'appliquait
  -- à quelle période quand il conteste une facture.
  fin         date,
  cree_le     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT abonnement_periode_coherente CHECK (fin IS NULL OR fin >= debut)
);

-- Un seul abonnement en cours par clinique : deux factureraient deux fois.
CREATE UNIQUE INDEX abonnement_un_en_cours ON abonnement (tenant_id)
  WHERE fin IS NULL;
CREATE INDEX abonnement_par_clinique ON abonnement (tenant_id, debut);

CREATE TABLE facture (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  numero          text NOT NULL,
  periode         date NOT NULL,           -- premier jour du mois facturé
  palier          text NOT NULL,
  visites         int NOT NULL CHECK (visites >= 0),
  visites_incluses int NOT NULL CHECK (visites_incluses >= 0),
  -- Les montants sont stockés en CENTIMES entiers. Un prix en flottant finit
  -- toujours par produire une facture à 68,99 € : ce n'est pas une erreur
  -- d'arrondi acceptable quand le client la lit.
  abonnement_centimes  int NOT NULL CHECK (abonnement_centimes >= 0),
  depassement_centimes int NOT NULL CHECK (depassement_centimes >= 0),
  total_centimes       int NOT NULL CHECK (total_centimes >= 0),
  -- Coût réel des SMS de la période, pour que la marge se lise sans calcul.
  cout_sms_centimes    numeric(12,3) NOT NULL DEFAULT 0,
  emise_le        timestamptz NOT NULL DEFAULT now(),

  -- Idempotence : la tâche mensuelle peut repasser sans facturer deux fois.
  CONSTRAINT facture_une_par_periode UNIQUE (tenant_id, periode),
  CONSTRAINT facture_numero_unique   UNIQUE (numero),
  CONSTRAINT facture_total_coherent
    CHECK (total_centimes = abonnement_centimes + depassement_centimes)
);

CREATE INDEX facture_par_clinique ON facture (tenant_id, periode DESC);

ALTER TABLE abonnement ENABLE ROW LEVEL SECURITY;
CREATE POLICY abonnement_cloisonnement ON abonnement
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON abonnement TO ${roleApplicatif};

ALTER TABLE facture ENABLE ROW LEVEL SECURITY;
CREATE POLICY facture_cloisonnement ON facture
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON facture TO ${roleApplicatif};
