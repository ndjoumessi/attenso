-- EF-905 : agenda à quota de créneaux, réservation en ligne, rappel J-1.
--
-- Deux tables et pas une, parce que le MODÈLE hebdomadaire et les CRÉNEAUX
-- réels d'une date sont deux choses différentes. Une clinique qui change ses
-- horaires ne doit pas voir disparaître les rendez-vous déjà pris sur
-- l'ancienne grille : les créneaux sont matérialisés, donc figés une fois nés.
CREATE TABLE plage_horaire (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    uuid NOT NULL REFERENCES tenant(id),
  -- 1 = lundi … 7 = dimanche (ISO-8601, comme java.time.DayOfWeek).
  jour_semaine smallint NOT NULL CHECK (jour_semaine BETWEEN 1 AND 7),
  debut        time NOT NULL,
  fin          time NOT NULL,
  duree_minutes smallint NOT NULL CHECK (duree_minutes BETWEEN 5 AND 240),
  -- Le quota est par créneau : deux salles de consultation = 2 places à 9 h 00.
  places       smallint NOT NULL CHECK (places BETWEEN 1 AND 20),
  actif        boolean NOT NULL DEFAULT true,

  CONSTRAINT plage_ordonnee CHECK (fin > debut)
);
CREATE INDEX plage_horaire_actives ON plage_horaire (tenant_id, jour_semaine) WHERE actif;

CREATE TABLE creneau (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  jour        date NOT NULL,
  heure       time NOT NULL,
  places      smallint NOT NULL CHECK (places >= 0),
  -- Le compteur des places prises. C'est LUI qui tient le quota, par un
  -- « UPDATE ... WHERE prises < places » atomique : compter puis insérer
  -- laisserait passer ensemble toutes les réservations ayant lu le compteur
  -- avant qu'aucune ne l'incrémente. Même raisonnement que budget_hlr.
  prises      smallint NOT NULL DEFAULT 0 CHECK (prises >= 0),

  CONSTRAINT creneau_unique UNIQUE (tenant_id, jour, heure),
  CONSTRAINT creneau_pas_de_surbooking CHECK (prises <= places)
);
CREATE INDEX creneau_a_venir ON creneau (tenant_id, jour, heure);

CREATE TABLE rendez_vous (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid NOT NULL REFERENCES tenant(id),
  creneau_id  uuid NOT NULL REFERENCES creneau(id),
  jour        date NOT NULL,
  heure       time NOT NULL,
  -- Référence opaque, même objet que celle d'un ticket : c'est elle qui vaut
  -- justificatif pour consulter ou annuler, sans compte ni mot de passe.
  reference   text NOT NULL UNIQUE,
  telephone_e164 text,
  motif       text,
  statut      text NOT NULL DEFAULT 'CONFIRME'
              CHECK (statut IN ('CONFIRME', 'ANNULE', 'HONORE', 'ABSENT')),
  rappel_envoye_le timestamptz,
  cree_le     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX rendez_vous_du_jour ON rendez_vous (tenant_id, jour, heure);
-- Les rendez-vous à rappeler : petite fraction de la table, index partiel.
CREATE INDEX rendez_vous_a_rappeler ON rendez_vous (jour)
  WHERE statut = 'CONFIRME' AND rappel_envoye_le IS NULL;

ALTER TABLE plage_horaire ENABLE ROW LEVEL SECURITY;
CREATE POLICY plage_horaire_cloisonnement ON plage_horaire
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON plage_horaire TO ${roleApplicatif};

ALTER TABLE creneau ENABLE ROW LEVEL SECURITY;
CREATE POLICY creneau_cloisonnement ON creneau
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON creneau TO ${roleApplicatif};

ALTER TABLE rendez_vous ENABLE ROW LEVEL SECURITY;
CREATE POLICY rendez_vous_cloisonnement ON rendez_vous
  USING (tenant_id = attenso_tenant_courant())
  WITH CHECK (tenant_id = attenso_tenant_courant());
GRANT SELECT, INSERT, UPDATE, DELETE ON rendez_vous TO ${roleApplicatif};
