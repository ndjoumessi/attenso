-- Attenso V0 — socle. Référence : docs/spec-v0-file-attente.md §2.
--
-- Deux invariants de ce fichier portent des décisions de conception, et non
-- de simples détails de stockage :
--   1. aucune colonne de rang : l'ordre est (priorite DESC, ordre ASC) et le
--      rang est compté à la lecture ;
--   2. notification_plafond : un seul SMS de chaque type par ticket, rendu
--      impossible à contourner par du code.

CREATE TABLE tenant (
  id                      uuid PRIMARY KEY,
  slug                    text NOT NULL UNIQUE,
  nom                     text NOT NULL,
  fuseau                  text NOT NULL DEFAULT 'Europe/Paris',
  delai_grace_secondes    int  NOT NULL DEFAULT 180,
  seuil_prevenir          int  NOT NULL DEFAULT 2,
  actif                   boolean NOT NULL DEFAULT true,
  cree_le                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT tenant_delai_grace_positif CHECK (delai_grace_secondes BETWEEN 0 AND 3600),
  CONSTRAINT tenant_seuil_positif       CHECK (seuil_prevenir BETWEEN 1 AND 20)
);

CREATE TABLE utilisateur (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  email         text NOT NULL,
  mdp_hash      text NOT NULL,
  role          text NOT NULL CHECK (role IN ('ACCUEIL','GERANT')),
  actif         boolean NOT NULL DEFAULT true,
  cree_le       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, email)
);

CREATE TABLE ticket (
  id                uuid PRIMARY KEY,
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  jour              date NOT NULL,
  numero_jour       int  NOT NULL,
  -- Référence du lien SMS. Courte et opaque, PAS un JWT : un JWT HS256 fait
  -- plus de 200 caractères et ferait basculer chaque SMS à deux ou trois
  -- segments, ce qui détruit le modèle à deux SMS par visite. Le navigateur
  -- l'échange contre un jeton de 2 h ; elle ne vit qu'une journée et n'expose
  -- qu'une position dans une file.
  reference         text NOT NULL,
  telephone_e164    text,
  telephone_valide  boolean,
  priorite          smallint NOT NULL DEFAULT 0,
  ordre             bigint   NOT NULL,
  statut            text NOT NULL CHECK (statut IN (
                      'EN_ATTENTE','PREVENU','APPELE','ABSENT',
                      'EN_CONSULTATION','TERMINE','PARTI','ANNULE')),
  cree_le           timestamptz NOT NULL DEFAULT now(),
  appele_le         timestamptz,
  grace_expire_le   timestamptz,
  consultation_le   timestamptz,
  clos_le           timestamptz,
  UNIQUE (tenant_id, jour, numero_jour),
  UNIQUE (reference),
  CONSTRAINT ticket_priorite_connue CHECK (priorite IN (0, 5, 10)),
  CONSTRAINT ticket_reference_longueur CHECK (char_length(reference) BETWEEN 10 AND 32)
);

-- L'index qui porte l'ordre de la file. Partiel : seuls les états ouverts
-- comptent, et c'est exactement ce que lit le calcul de position.
CREATE INDEX ticket_file ON ticket (tenant_id, jour, priorite DESC, ordre ASC)
  WHERE statut IN ('EN_ATTENTE','PREVENU','APPELE','ABSENT');

-- Le tableau du jour de l'accueil, et le tick de grâce.
CREATE INDEX ticket_jour  ON ticket (tenant_id, jour);
CREATE INDEX ticket_grace ON ticket (grace_expire_le) WHERE statut = 'APPELE';
-- La limite de deux tickets par numéro et par jour.
CREATE INDEX ticket_numero ON ticket (tenant_id, jour, telephone_e164)
  WHERE telephone_e164 IS NOT NULL;

CREATE TABLE notification (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  ticket_id       uuid NOT NULL REFERENCES ticket(id),
  type            text NOT NULL CHECK (type IN ('TICKET','PREVENIR','CORRECTIF')),
  canal           text NOT NULL CHECK (canal IN ('SMS','PUSH')),
  destinataire    text NOT NULL,
  corps           text NOT NULL,
  statut          text NOT NULL CHECK (statut IN ('EN_ATTENTE','ENVOYE','LIVRE','ECHEC')),
  fournisseur_id  text,
  cout_centimes   numeric(6,3),
  erreur          text,
  tentatives      int NOT NULL DEFAULT 0,
  cree_le         timestamptz NOT NULL DEFAULT now(),
  envoye_le       timestamptz,
  livre_le        timestamptz
);

-- LE plafond. Un seul SMS de chaque type par ticket, pour toujours, quel que
-- soit le code qui le demande. Un doublon lève une violation de contrainte,
-- que l'émetteur avale : c'est le mécanisme, pas un accident.
CREATE UNIQUE INDEX notification_plafond ON notification (ticket_id, type);
-- La file d'envoi.
CREATE INDEX notification_a_envoyer ON notification (cree_le) WHERE statut = 'EN_ATTENTE';

CREATE TABLE journal_acces (
  id              bigserial PRIMARY KEY,
  tenant_id       uuid NOT NULL REFERENCES tenant(id),
  utilisateur_id  uuid REFERENCES utilisateur(id),
  ticket_id       uuid REFERENCES ticket(id),
  action          text NOT NULL,
  ip              text,
  horodatage      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX journal_acces_tenant ON journal_acces (tenant_id, horodatage DESC);

-- Rejeu idempotent des actions faites hors ligne (spec §6). La clé primaire
-- EST l'idempotence : l'UUID est généré par la tablette de l'accueil, et une
-- seconde arrivée de la même commande échoue sur la clé, donc ne fait rien.
CREATE TABLE commande (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  ticket_id     uuid REFERENCES ticket(id),
  action        text NOT NULL,
  recu_le       timestamptz NOT NULL DEFAULT now()
);

-- --- Rôle applicatif ----------------------------------------------------
-- Créé ici pour que `docker compose up` + démarrage suffisent en local. En
-- production, les rôles sont gérés hors de Flyway ; cette migration est alors
-- sans effet puisqu'elle est idempotente.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${roleApplicatif}') THEN
    CREATE ROLE ${roleApplicatif} LOGIN PASSWORD '${mdpRoleApplicatif}';
  END IF;
END
$$;

DO $$ BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO ${roleApplicatif}', current_database());
END $$;
GRANT USAGE  ON SCHEMA public     TO ${roleApplicatif};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO ${roleApplicatif};
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA public TO ${roleApplicatif};
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${roleApplicatif};
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ${roleApplicatif};
