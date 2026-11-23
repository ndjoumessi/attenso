# Attenso

File d'attente virtuelle pour cabinets à flux libre. **V0 : la file seule.**

Le périmètre, les décisions et les critères d'arrêt sont dans
[`docs/cahier-des-charges.md`](docs/cahier-des-charges.md) ; la conception de
ce V0 dans [`docs/spec-v0-file-attente.md`](docs/spec-v0-file-attente.md).
Ce fichier-ci ne dit que comment le faire tourner, et quelles décisions du code
diffèrent de la spec.

---

## Démarrer

```bash
docker compose up -d                      # PostgreSQL sur le port 5434
export ATTENSO_JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run
```

Installer la première clinique, une seule fois :

```bash
ATTENSO_JWT_SECRET="$(openssl rand -base64 48)" \
MDP_ACCUEIL='choisir-un-mot-de-passe-solide' \
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
--attenso.amorce.active=true \
--attenso.amorce.slug=cerisiers \
--attenso.amorce.nom='Clinique des Cerisiers' \
--attenso.amorce.email=accueil@cerisiers.fr \
--attenso.amorce.mot-de-passe=$MDP_ACCUEIL"
```

Puis :

| Écran | URL | Qui |
| --- | --- | --- |
| Tableau de l'accueil | `/` | l'ASV, sur tablette |
| QR du comptoir | `/t/cerisiers` | le client, à l'arrivée |
| Suivi du client | `/s/{reference}` | le client, par SMS |
| Écran du mur | `/ecran/cerisiers` | la salle d'attente |

Tests : `./mvnw test`. Ils exigent Docker — les invariants de ce système sont
des contraintes PostgreSQL (index unique, index partiel, *row level security*),
et une base en mémoire ne les porte pas.

## Variables d'environnement

| Variable | Obligatoire | Rôle |
| --- | --- | --- |
| `ATTENSO_JWT_SECRET` | **oui** | signature des jetons du personnel ; sans valeur par défaut, l'application refuse de démarrer |
| `ATTENSO_BASE_URL` | en production | base des liens SMS ; doit rester courte |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | en production | rôle **applicatif**, soumis au RLS |
| `DB_OWNER`, `DB_OWNER_PASSWORD` | en production | rôle **propriétaire**, pour Flyway uniquement |

Les deux rôles ne sont pas une coquetterie : les politiques RLS ne s'appliquent
pas au propriétaire des tables. Avec un rôle unique, le cloisonnement serait
décoratif — et les tests qui le vérifient seraient verts pour rien.

---

## Ce que le code décide, et que la spec ne disait pas

Quatre écarts assumés, trouvés en écrivant le code. Chacun est documenté à
l'endroit où il s'applique.

### 1. La réévaluation suit *chaque* transition

La spec §4 la déclenchait sur `TERMINE`, `PARTI`, `ANNULE`, `ABSENT` et
l'urgence. **Cette liste est à l'envers.** Le ticket quitte la file à
`EN_CONSULTATION`, pas à `TERMINE` — l'index partiel `ticket_file` ne retient
que quatre états ouverts. Donc `TERMINE` ne déplace personne, alors que
« présent », absent de la liste, remonte toute la salle d'un cran : c'est
précisément l'instant où quelqu'un franchit le seuil de prévenance.

Réévaluer après chaque transition coûte un `count` sur une quinzaine de lignes
et ne fait rien quand rien n'a bougé. En échange, la classe entière des « on a
oublié un déclencheur » disparaît. Même raisonnement que le plafond de SMS mis
dans la base plutôt que dans un `if`.
→ `ServiceFile`, et le test `laPrevenanceSeDeclencheQuandLaConsultationCommence`.

### 2. Le lien du SMS n'est pas un jeton signé

La spec §5 voulait un JWT de 2 h dans le lien. Un JWT HS256 fait plus de 200
caractères : le SMS passe à deux ou trois segments et le modèle à deux SMS par
visite s'effondre. Le lien porte donc une **référence opaque de 12 caractères**
(~71 bits), qui vaut justificatif d'accès : elle ne donne accès qu'à une
position dans une file, elle est cloisonnée à une clinique et elle meurt avec la
journée. Le QR *imprimé* du comptoir est un autre objet, durable, et sera stocké
haché — ce n'est pas la même règle.
→ `GenerateurReference`, colonne `ticket.reference`.

### 3. Pas de SMS « revenez » au ticket qui vient d'être créé

Avec un seuil à 2, les deux premières personnes de la journée recevaient
« votre numéro est le 1 » **puis** « c'est presque à vous » dans la même
seconde — alors qu'elles sont debout au comptoir. Dans une clinique calme,
c'était un SMS gâché à presque chaque visite, soit la moitié du budget.
Le ticket neuf est donc promu `PREVENU` en silence.
→ `ServiceFile.reevaluer(..., sansNotification)`.

### 4. La limite de débit à l'enregistrement est volontairement large

Les clients scannent le QR depuis la salle d'attente, souvent sur le wifi de la
clinique : ils partagent alors une seule adresse publique. Une limite serrée par
IP bloquerait les vrais clients un jour de forte affluence. La protection utile
est **deux tickets par numéro et par jour**, tenue en base.
→ `ControleurPublic`.

### Au passage

Les accents des SMS sont filtrés sur l'alphabet GSM 7 bits : `é à è ù` passent,
`ô ê î â û` non. Un seul caractère hors alphabet fait tomber la capacité de 160
à 70 caractères. **Ne pas « corriger » les accents de `ModeleMessage`** — c'est
pour ça qu'on lit « presque à vous » et non « bientôt à vous ».
`CompteurSegmentsTest` garde la règle.

---

## Ce qui n'est pas fait

- **Adaptateur Octopush.** L'interface `ExpediteurSms` est là, l'implémentation
  de développement journalise et calcule le coût réel. L'adaptateur attend deux
  décisions qui ne sont pas prises : quel nom d'expéditeur affiche le SMS, et
  sur quel compte. Voir `docs/note-canal-sms.md`.
- **Vérification HLR** (0,005 €) avant émission du ticket. Le contrôle de format
  est en place et attrape les fautes de saisie ; le HLR attend le même compte.
- **Journal de commandes persistant côté client.** Le serveur est prêt
  (en-tête `Idempotency-Key`, table `commande`, clé primaire = idempotence) et
  la tablette empile déjà ses actions **en mémoire** : elle survit à une coupure
  réseau de quelques minutes, pas à un rechargement de page. La version
  IndexedDB est la suite.
- **Limiteur de débit partagé.** En mémoire, donc juste pour une instance.

## Carte du code

| Paquet | Ce qu'il porte |
| --- | --- |
| `ticket` | la machine à états, le moteur de file, l'estimation, les tâches |
| `notification` | le plafond de SMS, les modèles, le compteur de segments |
| `tenant` | le cloisonnement : contexte, source de données, résolution du slug |
| `securite` | référence du lien client, jeton du personnel |
| `web` | routes, SSE, limiteur de débit, traduction des erreurs |
| `journal` | journal d'accès et journal des commandes, en ajout seul |
| `amorce` | installation de la première clinique |

Les deux migrations se lisent comme de la documentation : `V1__socle.sql` porte
le schéma et dit pourquoi il n'y a ni colonne de rang ni table de compteurs,
`V2__rls.sql` porte le cloisonnement et dit pourquoi il faut deux rôles.
