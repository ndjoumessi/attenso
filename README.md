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

### 5. `navigator.onLine` est le mauvais signal dans une clinique

Il dit seulement que l'appareil a une interface réseau active. Quand le routeur
wifi de la clinique fonctionne mais que la fibre est tombée — la panne la plus
courante — il reste à `true`, et le bandeau « hors ligne » ne se serait jamais
affiché. L'état de la liaison est donc déduit du succès ou de l'échec des
appels réels.
→ `etatLiaison` dans `app/attenso.js`.

### 6. Le service worker n'était jamais enregistré

Le fichier existait, aucune page n'appelait `register()` : du code mort.
Vérifié en conditions réelles, la conséquence était sévère — recharger la page
pendant une panne ne servait rien du tout, la page mourait, et avec elle la
boucle qui devait rejouer les gestes. Le journal survivait dans IndexedDB sans
que rien ne puisse le lire. Le service worker sert maintenant la coquille, avec
un repli de navigation qui reproduit les routes de `ControleurPages`.
→ `sw.js`, et l'enregistrement dans `accueil.html` et `suivi.html`.

### 7. Un absent qui se présente entre directement en consultation

`ABSENT → EN_CONSULTATION` est désormais une transition permise. En salle, elle
évite de faire refaire « j'appelle » puis « présent » à l'ASV pour quelqu'un qui
est devant elle. Et elle ferme une course réelle : après une panne plus longue
que le délai de grâce, le tick passe `ABSENT` un ticket au redémarrage, et le
« présent » rejoué par la tablette aurait été refusé — alors que c'est la
tablette qui était dans la salle, pas le serveur.
→ `Statut`, et le test `lePresentRejoueApresUneGraceExpireeEstAccepte`.

### Au passage

Les accents des SMS sont filtrés sur l'alphabet GSM 7 bits : `é à è ù` passent,
`ô ê î â û` non. Un seul caractère hors alphabet fait tomber la capacité de 160
à 70 caractères. **Ne pas « corriger » les accents de `ModeleMessage`** — c'est
pour ça qu'on lit « presque à vous » et non « bientôt à vous ».
`CompteurSegmentsTest` garde la règle.

---

## Le mode dégradé, et comment le répéter

La tablette de l'accueil continue de fonctionner serveur coupé. Chaque geste
est écrit dans IndexedDB **avant** d'être tenté, avec un UUID envoyé en
`Idempotency-Key` ; le serveur le garde en clé primaire, donc un rejeu
n'applique rien deux fois. Le tableau s'affiche depuis le dernier état connu,
les rangs sont recalculés localement avec la règle du serveur, et les gestes
pas encore partis portent une étiquette « pas encore envoyé ».

Ce qui est refusé pendant la panne : insérer une urgence, parce que le numéro
du ticket est attribué par le serveur. Le bouton se désactive et le dit.

Au rejeu, un `409` ou un `404` est abandonné **en silence** : l'état a bougé
entre-temps, et afficher une erreur par geste périmé après vingt minutes de
panne noierait le seul message utile. Les autres refus sont montrés. Les gestes
d'un autre jour sont jetés au démarrage, et leur nombre annoncé — la file est
journalière.

Répétition à faire avec l'ASV en semaine 1, dans cet ordre :

```bash
pkill -f spring-boot:run      # la panne
# faire deux ou trois gestes sur la tablette, puis RECHARGER la page
./mvnw spring-boot:run        # le retour
```

Ce qu'il faut voir : le bandeau apparaît, le tableau reste utilisable, le
rechargement fonctionne quand même, et les gestes repartent seuls dans l'ordre
au retour du réseau. Vérifié de bout en bout dans Chrome.

**Limite connue :** le panneau navigateur intégré de certains outils bloque les
service workers. Le mode dégradé a donc été validé dans Chrome, pas dans ce
panneau — si une vérification future échoue à cet endroit, regarder d'abord si
le service worker est bien enregistré.

## Les SMS

Deux expéditeurs, choisis par `attenso.sms.expediteur` :

| Valeur | Effet |
| --- | --- |
| `journal` (défaut) | rien ne part, tout est journalisé, le coût est calculé pour de vrai |
| `octopush` | envoi réel ; exige `ATTENSO_OCTOPUSH_LOGIN` et `ATTENSO_OCTOPUSH_CLE` |

**L'expéditeur affiché est le nom de la clinique**, pas celui de l'éditeur : le
SMS « revenez » n'a de valeur que s'il est lu, et il est lu parce qu'il vient du
vétérinaire. Conséquence, et elle est lourde : ce n'est pas une configuration
unique mais **un enregistrement par clinique**, avec autorisation écrite et
validation opérateur de plusieurs jours (réglementation du 1er mars 2026).

Le nom vit dans `tenant.expediteur_sms`, 3 à 11 caractères alphanumériques sans
accent — « Clinique des Cerisiers » ne rentre pas, « Cerisiers » oui. Tant qu'il
est vide, **la clinique fonctionne sans SMS** : la file, l'écran et la page de
suivi marchent, les notifications sont abandonnées avec un motif lisible plutôt
que réessayées pendant des jours, et le démarrage journalise un avertissement
nommant la clinique. C'est ce qui rend une installation progressive possible.

Trois détails de l'adaptateur viennent de la documentation de l'API et
n'auraient pas pu être devinés :

- **`request_id`** porte l'identifiant de la notification, ce qui donne 24 h
  d'idempotence côté Octopush. Sans lui, une coupure survenant *après*
  l'acceptation du message ferait repartir le réessai — et payer le SMS deux
  fois. La boucle de réessai créait elle-même la fuite.
- **L'expéditeur est validé avant l'appel.** L'API, devant un paramètre mal
  formé, le « corrige » en supprimant les caractères interdits au lieu de le
  refuser : un SMS partirait sous un expéditeur que personne n'a enregistré.
- **`purpose = alert`** (transactionnel) : ni mention STOP, ni restriction
  horaire. Déclarer « wholesale » ferait refuser les messages (code 121) et
  interdirait le dimanche, jour où les cliniques de garde travaillent.

`attenso.octopush.simulation=true` utilise le mode bac à sable de l'API : la
requête est validée, rien n'est envoyé ni facturé. C'est le mode de la
répétition avant le pilote.

**Question ouverte qui touche le prix :** l'unité du champ `total_cost` renvoyé
par l'API n'est pas documentée (l'exemple officiel montre `5.005` pour un seul
contact). Le chiffre brut est donc enregistré dans
`notification.cout_fournisseur`, à côté de notre estimation dans
`cout_centimes`. La première facture réelle tranchera.

## La vérification du numéro

Deux vérificateurs, choisis par `attenso.hlr.verificateur` :

| Valeur | Effet |
| --- | --- |
| `format` (défaut) | contrôle de forme seul ; le verdict est « indéterminé » |
| `octopush` | interrogation HLR réelle, ~0,005 € l'appel, même compte que les SMS |

L'intérêt n'est pas d'économiser 4,5 centimes : c'est d'attraper la faute de
frappe **pendant que le client est encore au comptoir** et peut la corriger. Un
chiffre inversé, et il attend dans sa voiture un message qui ne viendra jamais —
exactement la promesse rompue que tout ce produit cherche à éviter.

**Trois verdicts et non deux**, parce que les deux erreurs n'ont pas le même
prix : refuser à tort renvoie un client qui est venu, accepter à tort coûte
4,5 centimes. On ne refuse donc que sur un négatif **certain** — ligne morte,
fixe, format illisible, SIM sans messagerie. Tout le reste laisse entrer.

Le cas qui décide de tout : `absent_subscriber` veut dire que la SIM existe mais
que le téléphone est éteint ou hors couverture. C'est la situation la plus
banale d'une salle d'attente, et le SMS arrivera au rallumage. Le confondre avec
`dead` reviendrait à renvoyer du comptoir quelqu'un dont le portable dort dans
sa poche.

Une panne de l'agrégateur ou un dépassement de délai donnent aussi
« indéterminé » : le ticket est émis sans vérification plutôt que de faire
attendre. Le délai de lecture est court et assumé (3 s), contre 15 s pour
l'envoi de SMS qui, lui, tourne en arrière-plan.

**Le budget se compte sur les vérifications, pas sur les tickets.** La première
version comptait des tickets et prétendait ainsi protéger la dépense : c'était
faux, et la revue de sécurité l'a relevé. Un numéro **refusé** ne crée aucun
ticket, donc le compteur ne bougeait jamais dans le seul cas qui coûte — un
numéro mort martelé donnait des appels payants à l'infini.

La table `verification_numero` porte donc le compteur et le cache. Elle est
cloisonnée comme les autres, et purgée à J+1 : elle contient des numéros de
téléphone, même règle que `ticket`.

- `attenso.hlr.max-par-jour` (300) plafonne les appels payants par clinique.
  Au-delà, on rend « indéterminé » sans appeler — **le ticket part quand même**.
  Un plafond de dépense n'a pas à fermer la salle d'attente.
- La réservation est **atomique** : un `UPDATE ... WHERE appels < max` sur une
  ligne de `budget_hlr`, verrouillée par PostgreSQL le temps de la transaction.
  Compter puis appeler — ce que faisait la version d'avant — laissait passer
  ensemble toutes les requêtes ayant lu le compteur avant qu'aucune ne
  l'incrémente : le plafond ne bornait plus que la latence. Un test lance
  quarante demandes simultanées contre un plafond de dix et vérifie qu'il en
  passe exactement dix.
- Une réservation n'est pas rendue si l'appel échoue ensuite : un délai dépassé
  peut correspondre à une requête que l'opérateur a reçue et facturée. Mieux
  vaut sur-compter une dépense que la sous-compter — c'est un plafond, pas une
  comptabilité.
- Le cache couvre les verdicts **négatifs** aussi : repasser le même numéro
  refusé ne coûte plus rien, et n'apprend rien de neuf à qui le tenterait.
- Le plafond de tickets et la limite par numéro restent vérifiés avant l'appel,
  mais ils ne sont plus présentés comme le garde-fou de la dépense.

**L'oracle de validité.** Un enregistrement qui répond « numéro injoignable »
distingue une ligne morte d'une ligne vivante : c'est un vérificateur de numéros
gratuit pour qui l'interroge et payé par la clinique. Les **refus** sont donc
comptés par adresse, à part et serré (5 par 10 min), là où les enregistrements
réussis gardent leur limite large. Un vrai client se trompe une ou deux fois ;
quelqu'un qui nettoie une liste volée dépasse le seuil immédiatement, et n'a
alors plus ni réponse ni appel payant. Le compteur n'est alimenté que par les
échecs, donc il ne coûte rien à personne d'honnête.

Le jeton de refus est **pris avant** la vérification et rendu si
l'enregistrement réussit. Vérifier d'abord et compter après aurait laissé
passer d'un coup autant de requêtes qu'on peut en lancer en parallèle. Seul un
refus de numéro retient le jeton : un plafond atteint, une panne ou une clinique
inconnue le rendent, parce que ce n'est pas l'adresse qui est en cause.

Ce n'est pas une fermeture complète : un attaquant disposant de nombreuses
adresses peut encore sonder, borné par le budget quotidien. C'est le budget qui
protège la clinique financièrement, et c'est la partie qui lui fait vraiment
mal.

### Un garde-fou qu'on a choisi de ne PAS poser

Une revue a proposé de refuser les enregistrements une fois le budget de
vérification épuisé, plutôt que de laisser passer sans contrôler. C'est refusé,
et il vaut mieux l'écrire que de le redécouvrir.

Le HLR n'est pas un contrôle de sécurité : c'est un attrape-faute-de-frappe et
une économie de SMS. Fermer l'enregistrement d'une clinique entière parce qu'un
budget de quelques euros est épuisé inverserait les priorités — la file est le
produit, le SMS est une fonction, la vérification est une optimisation de cette
fonction. Un plafond de dépense qui ferme la salle d'attente coûte infiniment
plus cher que ce qu'il économise.

Ce qui est retenu de ce constat, en revanche : l'épuisement du budget est
journalisé en avertissement, nommément, pour qu'il se voie.

Les deux réglages sont indépendants : on peut activer le HLR sans l'envoi réel,
par exemple pour mesurer la qualité des numéros d'une clinique avant d'ouvrir
les SMS.

## Garde-fous

- **La référence d'un lien SMS ne vaut que pour sa journée**, vérifié et non
  supposé : la ligne `ticket` survit à la journée (c'est elle qui porte le
  compteur de départs), donc rien n'expire tout seul.
- **`X-Forwarded-For` se lit par la droite.** Les entrées de gauche viennent du
  client : les croire rendrait le quota par adresse décoratif. Réglage
  `attenso.reseau.relais-de-confiance`.
- **Plafond de tickets par clinique et par jour** (`200` par défaut). Ce n'est
  pas une limite métier — une clinique en fait quarante — mais un plafond de
  dépense : chaque ticket déclenche un SMS payant et l'agrégateur n'en offre
  aucun. Tous les tickets **comptent**, seul l'enregistrement anonyme est
  **bloqué** : l'accueil passe toujours. Un plafond qui empêcherait l'ASV
  d'insérer une urgence parce qu'un script a épuisé le quota coûterait plus
  cher que la facture qu'il évite.

Pas de CAPTCHA à l'enregistrement, et c'est un choix : il détruirait les dix
secondes de prise de ticket qui sont tout le produit. La protection utile est
la limite de deux tickets par numéro, tenue en base.

## Ce qui n'est pas fait

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
| `numero` | vérification HLR et interprétation des verdicts |
| `amorce` | installation de la première clinique |

L'expéditeur SMS d'une clinique se règle à l'installation
(`--attenso.amorce.expediteur-sms=Cerisiers`) ou en base ensuite.

Côté client, `app/journal.js` porte IndexedDB (les gestes et le dernier
tableau), `app/attenso.js` la file de rejeu et l'état de la liaison, `sw.js` la
coquille hors réseau.

Les deux migrations se lisent comme de la documentation : `V1__socle.sql` porte
le schéma et dit pourquoi il n'y a ni colonne de rang ni table de compteurs,
`V2__rls.sql` porte le cloisonnement et dit pourquoi il faut deux rôles.
