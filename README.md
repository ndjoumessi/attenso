# Attenso

[![Tests](https://github.com/ndjoumessi/attenso/actions/workflows/ci.yml/badge.svg)](https://github.com/ndjoumessi/attenso/actions/workflows/ci.yml)

File d'attente virtuelle pour cabinets à flux libre. **V0, V1a et V1b implémentés.**

Ce fichier dit comment le faire tourner, et surtout **pourquoi le code est
écrit comme il l'est** : chaque décision non évidente est expliquée ici ou en
commentaire à l'endroit où elle s'applique.

> Les notes de conception et de stratégie — périmètre, critères d'arrêt,
> modèle économique, choix du canal SMS — ne sont pas publiées. Les décisions
> qui en découlent et qui touchent le code le sont toutes, ci-dessous. Les
> renvois à `docs/…` que tu croiseras dans les commentaires désignent ces
> notes ; elles ne sont pas dans ce dépôt.

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
--attenso.amorce.avec-ecran=true \
--attenso.amorce.mot-de-passe=$MDP_ACCUEIL"
```

Puis :

| Écran | URL | Qui |
| --- | --- | --- |
| Vitrine publique | `/` | le gérant, avant de signer |
| Tableau de l'accueil | `/accueil` | l'ASV, sur tablette |
| QR du comptoir | `/t/cerisiers` | le client, à l'arrivée |
| Suivi du client | `/s/{reference}` | le client, par SMS |
| Écran du mur | `/ecran/{jeton}` | la salle d'attente |
| Inscription autonome | `/inscription` | une clinique qui s'installe seule |
| Réservation | `/rdv/{slug}` | le client, pour un rendez-vous |
| Son rendez-vous | `/r/{reference}` | le client, par SMS |
| Carnet de santé | `/carnet/{jeton}` | le propriétaire, par QR |

Le jeton de l'écran est tiré à l'installation et journalisé **une seule fois**,
avec son URL. `--attenso.amorce.avec-ecran` vaut `false` par défaut : une
clinique sans téléviseur en salle n'a pas d'écran, et aucune route d'écran ne
répond pour elle. Pour en appairer un plus tard :

```sql
UPDATE tenant SET jeton_ecran = encode(gen_random_bytes(9), 'base64')
WHERE slug = 'cerisiers' RETURNING jeton_ecran;
```

Tests : `./mvnw test` lance les deux suites — serveur et écrans. Celle des
écrans seule : `npm test`. Elle n'a aucune dépendance, le runner intégré de
node suffit.

Sur GitHub, `.github/workflows/ci.yml` lance exactement la même commande à
chaque poussée et sur chaque pull request — une CI qui fait autre chose que le
développeur finit par diverger de lui, et c'est elle qu'on croit.

Les tests du serveur exigent Docker — les invariants de ce système sont
des contraintes PostgreSQL (index unique, index partiel, *row level security*),
et une base en mémoire ne les porte pas.

## Déployer (jalon L2)

```bash
cp .env.example .env     # puis remplir les secrets
set -a; source .env; set +a
docker compose -f compose.deploiement.yaml up -d
```

L'image se construit en deux étages : le JDK, Maven et le dépôt `.m2` restent
dans l'étage de construction, et l'image finale ne porte qu'un JRE et le jar —
environ 400 Mo de surface d'attaque en moins. Elle tourne sous un utilisateur
**sans privilège**, et son contrôle de vivacité interroge `/actuator/health`
plutôt que le port : Tomcat répond avant que Flyway n'ait fini de migrer, et un
conteneur déclaré prêt trop tôt reçoit du trafic qu'il ne peut pas servir.

**La base n'expose aucun port.** Seule l'application y accède, par le réseau
interne. Publier 5432 sur une machine publique est la faute la plus courante de
ce genre de fichier, et elle donnerait accès à toutes les cliniques d'un coup.

**L'application ne publie qu'en boucle locale.** Elle parle du HTTP en clair —
les jetons du personnel y passent — donc la lier à `0.0.0.0` sur une machine
publique exposerait ce clair à l'Internet. Le port est donc publié sur
`127.0.0.1`, et le TLS est terminé par un proxy inverse sur l'hôte, qui est
aussi ce qui porte le certificat du domaine. Ce proxy ne peut pas vivre dans ce
descripteur, qui ne connaît pas le domaine : c'est une pièce du jalon L2.

**Ce que le conteneur applicatif détient, et ce que ça coûte.** Il reçoit les
**deux** rôles : l'applicatif pour servir, le propriétaire pour que Flyway
migre au démarrage. Une exécution de code arbitraire dans l'application
contourne donc le RLS. Et ce n'est pas une affaire de superutilisateur : le
propriétaire d'une table n'est pas soumis à ses politiques, de sorte qu'un rôle
propriétaire dédié et non superutilisateur ne changerait rien à ce point précis
— seul `FORCE ROW LEVEL SECURITY` le ferait, et il casserait les migrations.
Ce n'est pas non plus ce que la seconde barrière protège : elle existe contre
un `WHERE tenant_id` oublié dans le code, pas contre un attaquant qui exécute
ce qu'il veut. Le vrai remède est de sortir Flyway de l'application — un
service à usage unique qui migre puis s'arrête, seul porteur du mot de passe
propriétaire, avec `spring.flyway.enabled=false` côté application. Pas fait :
ça ferait diverger le chemin de migration de celui du développement et des
tests, où Flyway tourne dans le processus, et cette divergence est exactement
ce qui a produit les deux pièges ci-dessous. À reprendre le jour où il y aura
plus d'une machine à migrer.

**Base Jammy et non Alpine**, délibérément : Temurin ne publie pas de JRE
Alpine pour arm64, et l'image ne se construirait donc ni sur un Mac Apple
Silicon ni sur un serveur ARM — qui sont justement les machines les moins
chères à louer en Europe. Quelques dizaines de mégaoctets contre une image qui
se construit partout.

Vérifié de bout en bout, pile complète et volume vierge : les 16 migrations
passent, l'application atteint `healthy`, sert la vitrine, tourne en
utilisateur `attenso`, ne journalise **aucune ligne d'erreur**, et le rôle
`attenso_app` est confirmé non privilégié — la seconde barrière survit donc à
la conteneurisation, ce qui est tout l'enjeu.

### Trois pièges rencontrés en le vérifiant

**Changer un mot de passe de base après le premier démarrage casse la pile, et
l'erreur ment.** Postgres n'applique `POSTGRES_PASSWORD` qu'à l'initialisation
d'un répertoire de données vide ; sur un volume existant il garde l'ancien. On
obtient alors `password authentication failed for user "postgres"`, qui
désigne l'authentification là où le vrai coupable est le volume. Pour changer
un mot de passe, il faut le changer *dans Postgres* (`ALTER ROLE`), pas
seulement dans l'environnement. Et attention au diagnostic : `psql` depuis
l'intérieur du conteneur réussit quand même, parce que l'image officielle fait
confiance aux connexions locales — le test doit donc venir du **réseau**.

**`docker compose down` exige les mêmes variables que `up`.** Les gardes `:?`
du descripteur refusent toute commande sans les secrets, démontage compris.
C'est voulu — un descripteur qui démarre sans mot de passe en démarrerait un
sans mot de passe — mais ça surprend, et un `down` qui échoue en silence laisse
un volume périmé derrière lui, ce qui ramène au piège précédent.

**L'indicateur de santé du courriel déclarait le produit en panne.**
`spring-boot-starter-mail` installe tout seul un contrôle qui tente de joindre
le serveur SMTP, et il fait passer `/actuator/health` à `DOWN` tant qu'aucun
SMTP n'est configuré — c'est-à-dire sur toute installation neuve, et c'est
précisément l'état documenté plus bas. Or `/actuator/health` est le contrôle de
vivacité du conteneur : le produit se serait déclaré en panne alors que la
file, les SMS et les écrans tournaient. Chaque sonde ouvrait au passage une
connexion vers un serveur inexistant et déversait cent cinquante lignes de
trace — au rythme du `HEALTHCHECK`, le journal devenait illisible. L'indicateur
est coupé (`management.health.mail.enabled=false`), et `SanteIntegrationTest`
empêche de le remettre par mégarde. Ne pas pouvoir envoyer un courriel de
réinitialisation n'est pas une panne du service : c'est une fonction
indisponible, et le démarrage le dit déjà en clair.

### Ce que ce fichier ne peut pas garantir : ENF-04

L'exigence demande un hébergement en France ou dans l'Union européenne. Aucun
descripteur ne décide de ça — c'est la machine qui l'exécute. Ce qui est fait,
en revanche, c'est de n'enfermer **aucune dépendance hors UE** : pas de base
gérée tierce, pas de CDN, pas de télémétrie sortante. Le seul appel sortant du
produit est celui de l'agrégateur SMS, et Octopush est français.

La seule exception est la déclaration des polices (voir plus bas), et c'est la
raison pour laquelle elle est signalée comme une dette plutôt que comme un
détail d'habillage.

### Ce qui reste hors du code

La moitié technique est là et vérifiée. L'autre moitié ne s'écrit pas : louer
une machine en UE, y pointer un nom de domaine, ouvrir le compte Stripe et
déposer l'autorisation d'expéditeur SMS auprès des opérateurs.

**Le pilote a été abandonné**, sur décision du commanditaire : pas de clinique
pilote, pas de phase gratuite. Le produit se vend à l'abonnement dès la mise en
ligne, et c'est ce que dit maintenant la vitrine. Le jalon L2 du cahier des
charges — « V0 déployé et exploité chez l'établissement pilote » — n'a donc
plus d'objet sous cette forme ; ce qui en reste, c'est le déploiement.

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

## L'affiche du comptoir, et pourquoi elle sort du produit

EF-101 fait entrer le client par un QR code affiché au comptoir. Le code en
dépendait depuis le premier jour — la route `/t/{slug}`, le slug lisible à voix
haute, la page de prise de ticket — mais **rien ne fabriquait le QR**. Tant
qu'il y avait une clinique pilote, quelqu'un passait le poser. Il n'y en a plus
: une clinique qui s'inscrit seule aurait dû chercher un générateur de QR sur
le web et y coller l'adresse de son comptoir, c'est-à-dire la confier à un
tiers quelconque, puis imprimer un résultat que personne n'aurait vérifié.

`/affiche` rend donc la feuille, prête à imprimer en A4, ouverte aux deux rôles
— réimprimer une affiche décollée est un geste de comptoir.

Trois décisions valent d'être écrites :

**Le serveur n'envoie pas de balise.** Il envoie un côté de grille et un tracé
(`Qr`), et c'est la page qui en fait un `<svg>` avec `createElementNS`. Rien de
ce qui vient du réseau n'est interprété comme de la structure sur cette page.
Le gain est petit — le tracé est fabriqué par nous à partir d'une URL bornée —
mais il est gratuit.

**L'affiche ne promet un SMS que si la clinique peut en envoyer.** Tant que
l'expéditeur n'est pas validé par les opérateurs, `expediteur_sms` est `NULL`
et aucun message ne partira ; la feuille dit alors « vous suivrez votre tour
depuis votre téléphone », qui est vrai dans les deux cas. Une affiche qui
promet un message ferait ranger le téléphone à un client qui n'en recevra
jamais.

**La feuille est du papier, pas de l'écran.** Elle reste noire sur blanc en
mode sombre, et c'est la seule exception aux jetons de couleur du produit
(`--papier`, `--sur-papier`) — pour la même raison que le mur de la salle
d'attente garde son fond sombre dans les deux modes. Deux pièges y ont été
rencontrés, tous deux invisibles à la lecture : le titre héritait du style
`h2` de l'application et tombait à **2,2:1** sur fond blanc, et la chaîne de
hauteurs qui permet de centrer verticalement avait un maillon manquant, ce qui
laissait un tiers de page blanche sous l'affiche.

Le QR est encodé par zxing en redondance Q (25 %). Le choix est gratuit : aux
niveaux M comme Q, l'URL d'un comptoir tient dans la même version de symbole,
donc dans le même nombre de modules — `GenerateurQrTest` le vérifie plutôt que
de le croire. Mesuré, zone de silence comprise : 37 modules de côté pour
`/t/cerisiers`, 49 pour le slug le plus long que l'inscription puisse
fabriquer. À 100 mm sur le papier, le pire cas donne des carrés de 2,0 mm.

Enfin, ce qu'aucune relecture ne peut établir : le tracé rendu par le
navigateur a été rastérisé puis **relu par un décodeur**, par le même chemin
qu'un appareil photo de téléphone, et il rend bien l'adresse du comptoir.

## L'écran du mur est appairé, et optionnel

La première version servait l'écran sur `/ecran/{slug}`, en accès libre. Le
slug, c'est le nom de la clinique : qui le devinait lisait sa file en direct, et
ouvrait un flux SSE au passage. Rien de nominatif n'y figure — l'écran n'affiche
que des numéros, c'est sa raison d'être — mais **l'affluence d'un cabinet est
une donnée commerciale**, et elle n'a pas à être servie à qui la demande.

La donnée de l'écran passe donc par un jeton d'appairage
(`/api/ecran/jeton/{jeton}`), colonne `tenant.jeton_ecran`. Elle est
**nullable**, et c'est elle qui porte EF-603 : pas de jeton, pas d'écran, 404.
Il n'y a rien à désactiver puisque rien n'est activé par défaut.

Un jeton inconnu et une clinique sans écran rendent **la même réponse**.
Les distinguer dirait à qui sonde laquelle des deux il vient de toucher, donc
quelles cliniques ont un écran. Un test le garde.

**Le flux SSE du tableau reste résolu par slug**, et c'est délibéré. Les deux
abonnés n'ont pas le même justificatif : le tableau est authentifié par JWT,
mais `EventSource` ne sait pas poser d'en-tête `Authorization`, et un jeton en
paramètre d'URL finirait dans les journaux d'accès du serveur et des proxys.
Surtout, une clinique sans écran n'a aucun jeton, et son tableau doit quand
même se rafraîchir. Ce flux **ne transporte aucune donnée** : il dit « ça a
bougé », et l'abonné relit par une route qui, elle, contrôle l'accès.

## La mesure du pilote

C'est le chiffre qui décide du projet en semaine 10, et il ne se déduit
d'aucune donnée que le système possède déjà. Attenso voit les gens qui
**prennent un ticket** ; il ne voit pas ceux qui franchissent la porte. Le
dénominateur du taux d'auto-enregistrement est donc une saisie humaine, faite
par l'ASV sur son tableau, et la table `mesure_jour` ne contient que ça.

Trois décisions qui tiennent tout le calcul :

- **Le numérateur ne compte que les tickets pris par le client lui-même.** Un
  ticket saisi par l'accueil pour quelqu'un sans smartphone prouve précisément
  que le client ne s'est *pas* enregistré seul : le compter gonflerait le taux
  et invaliderait la décision. D'où la colonne `ticket.origine`, qui distingue
  `COMPTOIR` de `ACCUEIL` — les urgences saisies par l'ASV comptent comme
  `ACCUEIL`.
- **Un dénominateur absent n'est pas un zéro.** Tant que personne n'a compté
  les arrivants, le taux vaut `null` et l'interface demande la saisie. Afficher
  « 0 % » ferait conclure à l'échec du pilote là où il manque seulement un
  comptage — et le critère d'arrêt est justement « sous 30 % ».
- **La jointure est pleine, dans les deux sens.** Une première version partait
  des tickets : une journée où l'accueil a compté vingt arrivants sans qu'aucun
  ticket ne soit pris — QR code décroché, affiche tombée — disparaissait
  purement et simplement de la vue et de l'export. C'est le 0 % le plus
  éloquent du pilote, et c'était le seul qu'on effaçait. Au passage, la saisie
  du matin s'évaporait dès qu'on rouvrait l'écran avant le premier client,
  ce qui aurait fait cesser de compter. Deux tests gardent les deux cas.

L'export CSV (`GET /api/mesure/export.csv`) est le critère de recette R9. Il
sépare au **point-virgule** et commence par une marque d'ordre des octets :
sans le premier, un tableur français ouvre tout en une colonne ; sans la
seconde, Excel lit l'UTF-8 en latin-1 et affiche « DÃ©parts ». Les valeurs
absentes restent **vides** et ne deviennent pas zéro, pour la raison ci-dessus.
Le téléchargement passe par `fetch` et non par un lien : la route exige
l'en-tête `Authorization`, et un jeton glissé dans l'URL finirait dans les
journaux d'accès du serveur et des proxys.

Le déplacement manuel dans la file (`monter` / `descendre`) échange deux rangs
**à l'intérieur d'une même bande de priorité** : une urgence ne se fait pas
doubler par un geste de confort. Aux extrémités, le geste ne fait rien et ne
lève pas d'erreur — bloquer l'ASV sur une action sans conséquence serait pire
que de l'ignorer. Il est refusé hors réseau, contrairement aux autres gestes :
l'ordre est arbitré par le serveur, et deux tablettes qui réordonnent à
l'aveugle pendant une panne produiraient deux files différentes, chacune
persuadée d'avoir raison.

## Les paliers V1a et V1b

Le V0 — la file seule — était le périmètre du pilote. Les paliers suivants ont
été construits ensuite, sur demande explicite, **avant** que le pilote n'ait
rendu son verdict. Le cahier des charges les conditionnait : V1a après une
mesure à ≥ 50 % d'auto-enregistrement en semaine 10, V1b après trois clients
payants sur V1a. Ces portes n'ont pas été franchies, elles ont été contournées,
et c'est une décision assumée du commanditaire — pas un oubli. Le pilote a
depuis été abandonné tout court : le produit se vend directement.

### Inscription autonome (EF-901), et ce qu'elle ne peut pas faire

Une clinique s'inscrit seule sur `/inscription` et repart avec l'adresse de son
QR. Le §15 du cahier des charges avait repéré la collision : l'expéditeur SMS
au nom de la clinique exige une autorisation écrite et une validation opérateur
de plusieurs jours, **ce qu'aucune inscription en ligne ne peut produire**.

La sortie était déjà dans le schéma : `expediteur_sms` reste `NULL`, la clinique
tourne *sans SMS* — file, écran et page de suivi compris — et la page de
confirmation le dit en toutes lettres plutôt que de laisser découvrir le
silence. C'est la seule étape qui n'est pas autonome, et elle est annoncée dès
la première minute.

**Ce qui manque encore, et qu'il faut savoir :** l'adresse de courriel n'est pas
vérifiée. Le compte est utilisable immédiatement, ce qui est le comportement
voulu, mais rien n'empêche quelqu'un d'inscrire une clinique sous une adresse
qui n'est pas la sienne. La limite de débit (trois inscriptions par adresse IP
et par heure) borne l'abus de masse, pas l'usurpation ponctuelle. Un envoi de
courriel de confirmation est le prochain pas, et il n'est pas fait.

### Facturation (EF-902) — et un défaut des prix du §13

Trois paliers, facturés au **volume de visites** et non au nombre de
praticiens, avec dépassement à 0,15 €. Les montants sont des **centimes
entiers** : un prix en flottant finit toujours par produire une facture à
68,99 €, ce qui n'est pas un arrondi acceptable quand le client la lit.

**Les prix du cahier des charges ont un palier dominé.** Avec le dépassement à
0,15 €, rester au palier Comptoir et payer le dépassement revient moins cher
que Clinique *à tous les volumes*, et Centre ne devient intéressant qu'au-delà
d'environ 1 434 visites :

| Visites/mois | Comptoir | Clinique | Centre | Le moins cher |
| --- | --- | --- | --- | --- |
| 800 | 144 € | 149 € | 239 € | Comptoir |
| 1 400 | 234 € | 239 € | 239 € | Comptoir |
| 1 500 | 249 € | 254 € | **239 €** | Centre |

Aucun client qui compte n'a de raison de prendre Clinique. Ce n'est pas corrigé
— les prix sont une décision commerciale, pas technique — mais le constat est
**figé dans un test** (`PalierTest`), qui échouera dès que la grille changera,
pour qu'on le revoie à ce moment-là.

Conséquence directe dans le code : le conseil de palier ne propose jamais un
changement qui coûterait plus cher, et ne conseille une *baisse* que sur un mois
complet — la première version poussait une clinique inscrite la veille à
descendre de formule, sur un compteur à zéro parce que le mois commençait.

**La vitrine annonçait autre chose que le produit.** Ses trois formules sont
écrites en dur — une page d'accueil n'a pas à attendre une API pour trois noms
et six nombres qui changent une fois par an — et personne ne les vérifiait. Elle
promettait « jusqu'à 400 visites » là où Comptoir en inclut 300, « 1 000 » là où
Clinique en inclut 800, et appelait « Cabinet » le palier nommé Comptoir partout
ailleurs. Une clinique pouvait donc s'abonner sur une promesse de 400 visites et
recevoir du dépassement dès la 301ᵉ. Corrigé, et `PrixAfficheTest` tient
désormais les trois colonnes — nom, prix, volume — plus le tarif de dépassement,
dans les deux sens : chaque palier doit être annoncé, et aucune valeur affichée
ne doit correspondre à rien.

Le même raisonnement a déplacé la mise en avant de la page : elle était sur
Clinique, c'est-à-dire sur la formule dominée. Elle est sur Comptoir, comme la
présélection de l'inscription.

### Souscrire, et payer (Stripe)

Le produit se vend à l'abonnement : une clinique s'inscrit seule, paie, et
ouvre. Il n'y a plus de pilote gratuit.

**Le paiement passe par une page hébergée par Stripe**, et c'est le choix
central : aucune donnée de carte n'entre jamais dans Attenso. Ce dépôt n'a donc
rien à protéger de ce côté — rien à chiffrer, rien à faire auditer —, et
l'authentification forte du payeur, les cartes européennes, le prélèvement SEPA
et les relances sont l'affaire du prestataire. Écrire un formulaire de carte
maison aurait ajouté la pire des surfaces d'attaque à un produit qui, par
ailleurs, ne collecte qu'un numéro de mobile.

**La clinique naît FERMÉE, et c'est le paiement qui l'ouvre.** `tenant.actif`
était déjà le drapeau honoré partout — connexion du personnel, QR du comptoir,
tâches planifiées — donc l'abonnement n'a pas eu besoin d'une seconde notion de
« clinique fermée » qu'il aurait fallu faire respecter dans chaque recoin. Effet
de bord qu'il a fallu traiter : une clinique inactive RÉSERVE quand même son
adresse, sinon la suivante du même nom recevrait le même slug et buterait sur
l'index unique au milieu de son inscription.

**Un impayé ne coupe rien, et c'est délibéré.** Attenso fait tourner une salle
d'attente. Couper la file un mardi matin parce qu'une carte a expiré laisserait
de vraies personnes debout devant une clinique qui ne peut plus les appeler, et
punirait les clients pour le moyen de paiement du vétérinaire. L'abonnement
passe en `IMPAYE`, ça se voit, et les relances sont l'affaire de Stripe puis la
nôtre — comme n'importe quel impayé entre professionnels. Ce qui ferme
réellement, c'est une résiliation, le lendemain du dernier jour payé : la
clinique garde la période qu'elle a réglée.

**Le prélèvement SEPA est encaissé après coup, et la clinique ouvre quand
même.** Une carte se valide en deux secondes ; un prélèvement met plusieurs
jours à se dénouer, et `checkout.session.completed` arrive avant l'argent.
Attendre le dénouement ferait patienter une clinique plusieurs jours devant une
file fermée, pour un moyen de paiement qui est justement l'usage courant en B2B
français. On ouvre donc à la confirmation de la session, et un rejet ultérieur
fait simplement passer l'abonnement en `IMPAYE` — exposition bornée à une
période, et cohérente avec la règle ci-dessus.

**La route du webhook est publique, et la signature est sa seule serrure.** Le
corps est donc lu en texte BRUT — la signature porte sur les octets exacts, et
un objet reconstruit puis ré-encodé ne les reproduit pas. Sans cette
vérification, n'importe qui posterait un `checkout.session.completed` et
s'offrirait un abonnement. Les événements sont dédupliqués en base : Stripe
rejoue à la moindre réponse lente, et la réservation de l'identifiant vit dans
la MÊME transaction que le traitement, pour qu'un échec rende l'événement à son
rejeu au lieu de le perdre.

**La clinique gère son abonnement elle-même**, depuis l'espace client hébergé
par le prestataire : changer de formule, mettre à jour le moyen de paiement,
télécharger ses factures, résilier. Quatre écrans que le prestataire sait déjà
faire, avec l'authentification forte qui va avec, contre quatre écrans à écrire,
tester et tenir à jour. Surtout, les conditions générales promettent une
résiliation « à tout moment » : sans ce bouton, la clause serait un mensonge.

Le bouton n'apparaît que s'il y a vraiment quelque chose à gérer — une clinique
créée avant la mise en vente n'a pas d'abonnement chez le prestataire, et mieux
vaut cacher le bouton que d'en afficher un qui s'excuse.

**Ce qui reste à faire de ton côté**, et que du code ne peut pas faire : créer
le compte Stripe, y définir trois tarifs mensuels, poser les cinq variables
`ATTENSO_STRIPE_*`, et déclarer le point de terminaison
`https://<domaine>/api/paiement/webhook`, et activer le portail client dans le
tableau de bord Stripe (c'est un réglage à cocher, sans quoi l'espace de gestion
rend une erreur). Vérifie aussi que la **version d'API**
du compte correspond à celle du SDK (`2026-10-28.endive` pour stripe-java
34.1.0) : sinon les charges arrivent illisibles, et le symptôme serait « les
abonnements ne s'activent plus », découvert par une clinique qui a payé. Le code
journalise cette cause-là explicitement.

Les tests ne demandent ni compte ni réseau — la passerelle est remplacée par une
fausse — à deux exceptions près, vérifiées pour de bon : la signature des
webhooks, avec un vrai HMAC, et le fait qu'un impayé ne ferme pas la clinique.

### Les comptes du personnel

Jusqu'ici une clinique n'avait **qu'un seul compte**, celui créé à
l'inscription : l'ASV et le vétérinaire partageaient le même mot de passe, et
un départ de salarié obligeait à tout réinitialiser. Le gérant peut désormais
inviter, changer un rôle, désactiver et réactiver.

**C'est le premier endroit où les deux rôles cessent d'être décoratifs.**
`ACCUEIL` et `GERANT` existaient depuis le V0 mais ouvraient exactement les
mêmes portes ; `/api/equipe/**` est réservé au gérant. Par conséquent, le
compte créé à l'inscription devient GÉRANT — c'est celui qui paie — là où il
naissait ACCUEIL, ce qui aurait fermé la gestion des comptes à la seule
personne capable de s'en servir.

**Un compte invité naît sans mot de passe utilisable.** Il porte un marqueur à
la place de l'empreinte : aucun mot de passe ne peut y correspondre, pas même
le marqueur lui-même, et un test le vérifie. L'invitation réutilise le
mécanisme de l'oubli — un compte neuf est exactement dans la situation d'un
oubli — avec une validité de sept jours au lieu d'une heure : une invitation
envoyée le vendredi soir doit encore marcher le lundi matin. L'alternative, un
mot de passe provisoire transmis par le gérant, ferait circuler un secret par
un canal qu'on ne maîtrise pas et qui resterait valable tant que personne n'y
pense.

**Il doit rester un gérant actif**, et le serveur le refuse autrement. Sans ce
garde, une clinique se verrouille dehors en un clic : plus personne pour
inviter, ni pour gérer l'abonnement, et la sortie serait une intervention en
base un samedi. Le message dit quoi faire — nommer quelqu'un d'autre d'abord —
parce que « refusé » tout court laisse chercher.

**Tout changement de rôle ou d'activation ferme les sessions du compte visé.**
Le rôle voyage dans le jeton : sans l'incrément de version, une rétrogradation
ou un départ ne prendrait effet qu'à l'expiration, jusqu'à douze heures plus
tard — or on rétrograde quelqu'un précisément quand on veut lui retirer un
droit maintenant. Au passage, `SessionsRevoquees` lit aussi `actif` dans la
même requête, donc gratuitement : un compte désactivé ne doit pas dépendre d'un
incrément qu'un futur chemin pourrait oublier.

**Pas de suppression, seulement une désactivation.** Un compte supprimé
emporterait le journal d'accès qui le désigne, or c'est précisément quand
quelqu'un part qu'on veut pouvoir relire ce qu'il a fait.

**Le rôle n'est pas lu côté navigateur pour décider de l'affichage** : la page
demande la liste, et masque la section si le serveur refuse. Un contrôle
d'accès qui vit dans la page se contourne avec la console, et finirait par être
pris pour la vraie règle. Le même appel révèle les trois zones du gérant —
comptes, abonnement, export — puisque le serveur les protège par le même rôle.

**L'export complet est passé côté gérant lui aussi**, et c'est le genre de
décision qu'on prend une fois pour toutes. Il emporte TOUT, numéros compris,
dans un fichier qui sort de la clinique ; c'est la porte de sortie du client,
et elle n'a pas à être à portée de clic de quiconque tient le comptoir un
après-midi. Le tableau de bord et la supervision restent ouverts aux deux
rôles : ce sont des chiffres du jour, utiles à qui fait avancer la file. Tout
verrouiller aurait été aussi faux que ne rien verrouiller.

**Une désactivation annule les invitations et demandes d'oubli en cours.** Sans
ça, une personne invitée puis désactivée avant d'avoir suivi son lien le
garderait valable sept jours : elle ne pourrait pas se connecter — la connexion
exige un compte actif — mais elle POSERAIT son mot de passe, et le jour d'une
réactivation le compte s'ouvrirait sur un secret qu'elle est seule à connaître.
La consommation d'un jeton exige en plus que le compte soit actif : la première
barrière suffit, la seconde est là pour le futur chemin de désactivation qui
oublierait la première.

### Son propre compte : savoir qui l'on est, et s'en aller

Trois manques sur la même tablette de comptoir, et ils se tiennent.

**Rien ne disait qui était connecté.** Sur un poste partagé par plusieurs
personnes, c'est la question qui précède toutes les autres. La ligne est
maintenant en haut du tableau : adresse et rôle, servis par `/api/compte`.

**Changer son mot de passe demandait de prétendre l'avoir oublié** : se
déconnecter, demander un lien, attendre un courriel. Ce détour suffisait à ne
jamais le faire — or c'est le geste qu'on veut facile le jour où quelqu'un
quitte la clinique en connaissant le mot de passe. Le formulaire est désormais
dans le tableau.

Deux décisions y sont enfouies. **Le mot de passe actuel est exigé bien que
l'appelant soit déjà authentifié** : le jeton prouve qu'une session est
ouverte, pas que c'est la bonne personne qui tient la tablette — et elle reste
ouverte douze heures. Et le refus est un **403, pas un 401** : un 401 sur une
route authentifiée dit à la page « ta session a expiré », elle renverrait vers
l'écran de connexion, et la personne croirait avoir été déconnectée alors
qu'elle a seulement mal retapé son mot de passe.

Le changement incrémente le compteur de version du jeton, donc ferme toutes
les sessions du compte — **y compris celle qui le demande**. La réponse rend
donc un jeton neuf, et la page recharge : le flux d'événements et la file de
rejeu portent l'ancien jeton dans leurs fermetures, et les laisser tourner
donnerait des refus inexplicables au premier geste hors réseau.

**Le jeton réémis est bâti sur la base, pas sur le jeton d'avant** — constat
d'une relecture de sécurité, et il était juste. Le filtre vérifie bien la
version à l'entrée, mais un gérant peut rétrograder ou désactiver ce compte
entre ce contrôle et l'écriture. Recopier le rôle du jeton d'appel rendait
alors un jeton valide douze heures portant le privilège qu'on venait de
retirer, et renouvelable à volonté puisqu'il suffit de changer son mot de
passe à nouveau : une fenêtre étroite, mais ce qui en sortait ne l'était pas.
L'écriture rend donc la version ET le rôle dans le même ordre SQL, avec un
`AND actif = true` qui garantit au passage qu'un compte désactivé ne voit pas
son mot de passe changer. Les deux états se construisent en SQL direct dans
les tests, sans toucher au compteur de version : c'est exactement ce que la
course produirait.

**La déconnexion existait, mais elle jetait les gestes en attente sans un
mot.** Elle vide le journal local — ce qui est voulu, on ne laisse pas le
tableau d'une clinique sur une tablette qu'on rend — et pendant une panne
réseau ce journal contient des gestes qui ne sont jamais arrivés au serveur.
Un garde les compte et demande un second clic. Le bouton a remonté du bas de
la page vers l'en-tête : au bout d'un tableau qu'il faut faire défiler debout,
il n'était pas une sortie.

L'écriture d'un mot de passe est passée dans un `MotDePasseDao` qui n'a que
cela à faire. Deux chemins y mènent maintenant — le lien reçu par courriel et
le changement volontaire — et séparés, l'un des deux aurait fini par oublier
de fermer les sessions ouvertes.

### Récupération de mot de passe

Sans elle, une clinique qui perd son mot de passe est enfermée dehors
**définitivement** : il n'existe aucun autre chemin vers son tableau. Pour un
produit qu'on vend, c'est un ticket de support au mieux, un client perdu au
plus probable.

**Le formulaire ne dit jamais si le compte existe.** Un message différent selon
le cas en ferait un annuaire : on essaie des adresses, et l'on apprend
lesquelles sont clientes — information qui se revend. La réponse est donc
identique pour une clinique inconnue, une adresse inconnue et un envoi réussi.
Le dépassement de la limite de débit est lui aussi **silencieux**, pour la même
raison : un 429 dirait « cette adresse existe et on lui a déjà écrit ».

**Répondre pareil ne suffit pas : il faut répondre aussi VITE.** La première
version faisait tout sur le fil de la requête, donc un envoi SMTP de plusieurs
centaines de millisecondes quand le compte existait, contre deux ou trois quand
il n'existait pas — un écart qui se chronomètre depuis n'importe où, et
l'énumération revenait par la porte de derrière. Tout le travail est maintenant
remis à un fil d'exécution, recherche de la clinique comprise : la requête
repart en temps constant. Aucun délai artificiel n'est ajouté ; il ralentirait
tout le monde sans rien garantir de plus.

**Et la clé de limitation est normalisée comme la recherche en base.** Sans ça,
`Asv@Clinique.fr`, `asv@clinique.fr` et la même adresse précédée d'une espace
étaient trois compteurs différents pour un seul compte : la limite de trois
devenait neuf, puis autant que l'imagination de l'appelant.

**Le jeton n'est jamais stocké en clair.** Il vaut un mot de passe pendant une
heure ; une base lue par un tiers — sauvegarde égarée, injection, curiosité d'un
exploitant — donnerait sinon la main sur tous les comptes en cours de
réinitialisation. L'empreinte est un SHA-256 nu et non un bcrypt, et c'est
délibéré : 24 caractères tirés au hasard n'ont ni dictionnaire ni réutilisation
à redouter, et un bcrypt par vérification coûterait du calcul pour une
résistance dont ce jeton n'a pas besoin.

**Un lien ne sert qu'une fois**, et une nouvelle demande annule la précédente —
trois demandes faites dans la panique laisseraient sinon trois liens actifs,
dont deux que personne ne surveille. La consommation se fait par
`UPDATE ... RETURNING` conditionnel : un SELECT puis un UPDATE laisserait deux
appels simultanés poser deux mots de passe différents, dont un seul serait connu
de son auteur.

**SMTP et non l'API d'un prestataire.** Le choix du serveur d'envoi reste à
l'exploitant, qui peut en prendre un européen sans que ce dépôt ait à le
connaître. Lier le code à une API américaine aurait démenti la politique de
confidentialité, qui promet qu'aucun prestataire hors UE n'est appelé pour faire
fonctionner le service.

**La réinitialisation ferme les sessions ouvertes**, et c'est tout l'intérêt :
on change son mot de passe parce qu'on le croit volé, et un mot de passe changé
qui laisse la session du voleur ouverte douze heures de plus ne sert à rien.
`utilisateur.mdp_change_le` porte la date, le jeton porte son instant
d'émission, et un jeton plus ancien est refusé. Oui, c'est une lecture en base
par requête authentifiée — le prix d'un jeton révocable — mais elle porte sur
une clé primaire dans une table de quelques lignes par clinique, et elle ne
touche QUE les requêtes du personnel : le chemin client, celui qui porte la
contrainte de latence d'ENF-01, n'a pas de jeton de personnel et n'est pas
concerné.

**Un compteur, et non une comparaison de dates** — et le détour vaut d'être
raconté, parce qu'il montre ce que coûte une granularité mal placée. La
première version comparait l'instant d'émission du jeton à la date du
changement. Or un JWT porte son émission à la SECONDE et la colonne portait les
millisecondes : comparer l'un à l'autre rejetait la reconnexion faite dans la
même seconde que la réinitialisation, c'est-à-dire le cas le plus courant.
Tronquer les deux côtés corrigeait ça mais laissait survivre un jeton émis
pendant cette seconde-là. Deux erreurs successives, pour une fenêtre d'une
seconde que personne n'avait demandée.

Un compteur n'a pas de granularité : `utilisateur.jeton_version` s'incrémente à
chaque changement, le jeton le porte, on compare pour égalité. Plus de fenêtre,
plus de raisonnement sur les arrondis — et les tests n'ont plus besoin de
manipuler l'horloge. La date du changement reste en base, mais comme
information : ce n'est plus elle qui ferme les sessions.

**Un piège de MIGRATION s'y cachait, et il méritait mieux qu'une valeur par
défaut.** Les jetons émis avant la mise à jour ne portent pas la revendication,
donc le code la lit comme zéro — ce qui les laisse valables jusqu'à leur
expiration, et c'est voulu : déconnecter toutes les cliniques au milieu de leurs
consultations pour installer une mise à jour serait une drôle de façon
d'améliorer la sécurité. Mais partir TOUT LE MONDE à zéro ressusciterait les
sessions qu'un correctif précédent avait déjà révoquées : un compte réinitialisé
entre les deux versions verrait le jeton de son voleur redevenir valable. La
migration part donc à 1 les comptes dont le mot de passe a déjà changé. C'est
une ligne, et c'est la différence entre un correctif et un correctif qui en
annule un autre.

**Un piège rencontré pour la troisième fois dans ce dépôt**, et désormais
nommé dans le code : `@Transactional` ouvre la connexion AVANT que l'on sache de
quelle clinique on parle, donc lier le `ThreadLocal` ne suffit pas — les
politiques RLS masquaient la table `utilisateur` et la demande d'oubli ne
trouvait jamais personne. Le réglage se pose sur la connexion courante, en
portée transactionnelle. Même parade qu'à l'inscription et dans le webhook de
paiement.

### Pages légales, et ce qu'elles ne peuvent pas être

Vendre en ligne en France impose de publier des mentions légales, des
conditions générales et une politique de confidentialité. Les trois pages
existent, sont servies sans authentification, et sont liées depuis le pied de
page — une page légale parfaite derrière un 404 ne protège personne, et un test
vérifie les deux.

**L'identité de l'éditeur ne vit pas dans le dépôt.** Raison sociale, siège,
SIRET, nom du directeur de publication : ce sont des données réelles, et ce
dépôt est public. Les écrire dans du HTML versionné les publierait sur GitHub
avant même la mise en ligne, et les rendrait quasi ineffaçables de l'historique.
Elles arrivent donc par l'environnement (`ATTENSO_EDITEUR_*`), comme les
secrets, à cette différence près qu'elles ne sont pas secrètes : simplement, ce
n'est pas au dépôt de les porter. Un champ absent s'affiche « à compléter »
plutôt que de disparaître — un blanc dans des mentions légales donne l'air
d'une page en règle sans l'être.

**Le démarrage le dit, et le niveau dépend de ce qui est branché.** Sans
souscription configurée, une identité incomplète est normale et tient en une
ligne d'information. Avec la souscription ouverte, c'est une ERREUR : le
serveur encaisse, et vendre sans ces informations est une infraction.

**L'acceptation des conditions est horodatée en base** (`tenant.cgv_acceptees_le`).
Une case cochée ne vaut rien si l'on ne peut pas dire quand elle l'a été, et le
serveur refuse l'inscription sans elle — une case HTML se contourne en trois
secondes.

**Ce que ces pages ne sont pas : un avis juridique.** Elles sont écrites à
partir du fonctionnement réel du produit — ce qui est collecté, combien de temps,
par qui — et c'est leur force : rien n'y est inventé, la durée de purge à J+1 et
l'absence d'IP journalisée sont vérifiées par des tests. Mais la qualification
du contrat, les clauses de responsabilité et la répartition
responsable/sous-traitant méritent une relecture par un juriste avant la
première vente. Le point le plus structurant y est déjà posé : pour les
personnes reçues en clinique, **c'est la clinique qui est responsable de
traitement**, et l'éditeur n'est que sous-traitant.

### Supervision (EF-903)

Trois contrôles, choisis parce qu'ils correspondent à trois pannes réelles et
non à des métriques génériques : la base répond, les SMS partent, l'ordonnanceur
tourne. Le deuxième et le troisième sont les sournois — l'application peut
répondre parfaitement pendant que la file d'envoi est bloquée ou que plus aucun
absent n'est détecté.

L'alerte ne part qu'au **changement** d'état. Répéter le message toutes les
minutes pendant une panne de nuit fait couper les notifications, et la panne
suivante passe alors inaperçue. Un webhook est facultatif ; sans lui, tout est
journalisé, ce qu'un agrégateur de journaux exploite très bien.

### Export complet (EF-904)

Une archive ZIP de CSV, en un clic, sans rien demander à personne. C'est un
argument de vente autant qu'une fonction : le §14 traite la réticence face à un
éditeur seul en montrant la porte de sortie, et une porte qu'on promet sans la
montrer ne rassure personne.

**Les empreintes de mots de passe n'y sont pas**, et la requête de la table
`utilisateur` nomme donc ses colonnes au lieu de prendre `*`. Un export circule :
il part par courriel, atterrit sur une clé USB, finit dans un dossier partagé.

### Agenda et réservation (EF-905)

Un modèle hebdomadaire produit des créneaux **matérialisés** par date : une
clinique qui change ses horaires ne doit pas voir disparaître les rendez-vous
déjà pris sur l'ancienne grille.

Le quota tient par un `UPDATE ... WHERE prises < places` atomique, pas par un
compte suivi d'une insertion — même raisonnement que le budget HLR, et un test
lance vingt réservations simultanées contre un quota de trois pour le prouver.
L'annulation **rend la place**, sans quoi l'agenda se viderait tout seul et la
clinique refuserait des clients devant des créneaux qu'elle croit pleins.

L'agenda **ne remplace pas la file** : un rendez-vous réserve une heure, et c'est
l'accueil qui crée le ticket quand la personne se présente. Faire entrer
automatiquement un rendez-vous dans la file à l'heure dite produirait des
tickets fantômes pour tous les clients en retard.

### Carnet de santé (EF-906) — et une contradiction du cahier des charges

**EC-05 dit « aucune donnée de santé n'est traitée en V0, V1a ni V1b », et
EF-906 — classé V1b — demande un carnet de santé.** Les deux ne peuvent pas
être vrais ensemble. Le §3.4 tranche : il met hors périmètre « toute donnée de
santé **humaine**, et tout hébergement HDS ». Un carnet de vaccination de chat
est une donnée de santé *animale*, qui n'est pas une catégorie particulière au
sens du RGPD et n'appelle aucun agrément HDS. C'est cette lecture qui est
implémentée. Si l'intention était d'exclure aussi le vétérinaire, il faut
retirer EF-906 du périmètre plutôt que de l'écrire à moitié.

**Le QR est révocable, et c'est le cœur de l'exigence.** Un QR imprimé sur une
médaille se photographie : révoquer fait tourner le jeton, l'ancienne adresse
cesse de répondre, et le propriétaire recolle une médaille neuve sans qu'on lui
refasse quoi que ce soit. Révoquer *sans* réémettre ferme le carnet
définitivement — c'est ce que demande quelqu'un qui ne veut plus d'accès par QR
du tout, et le lui refuser viderait le mot « révocable » de son sens.

**La durée de conservation n'est pas celle de la file**, et c'est voulu. Le
numéro d'un ticket est effacé à J+1 ; celui d'un carnet ne l'est pas, parce
qu'un rappel de vaccin se déclenche des mois plus tard. Deux finalités, deux
durées : c'est exactement ce que le RGPD demande d'articuler, et non une entorse
à la minimisation.

Le SMS de rappel ne nomme **ni l'animal ni le vaccin** : il s'affiche sur un
écran verrouillé, et le détail reste derrière le lien, lui-même révocable.

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

## Exigences non fonctionnelles : ce qui est tenu, et ce qui ne peut pas l'être ici

Onze ENF au cahier des charges. Six sont portées par le code et vérifiées ;
quatre sont opérationnelles et ne se décident pas dans un dépôt ; une est
tenue par construction.

| Réf. | État | Preuve ou raison |
| --- | --- | --- |
| ENF-01 | **mesuré** | p95 à **68 ms** côté serveur (médiane 30 ms, p99 91 ms), 600 lectures sur 20 clients simultanés, base à 225 000 tickets |
| ENF-02 | tenu | le tick est à 5 s, donc toujours sous les 10 s exigées |
| ENF-03 | **mode dégradé fait** | la disponibilité est opérationnelle ; le mode dégradé, lui, est obligatoire et il existe |
| ENF-04 | **rien à corriger côté code** | plus aucune dépendance hors UE : ni CDN, ni polices distantes, ni télémétrie |
| ENF-05 | vérifié | RLS **et** politique sur les 8 tables porteuses de `tenant_id`, contrôlé dans la base réelle |
| ENF-06 | vérifié | référence opaque dans le lien SMS, jeton opaque pour l'écran, aucun identifiant brut |
| ENF-07 | vérifié | `journal_acces` sur chaque transition ; `notification` porte canal, statut, identifiant fournisseur et coût |
| ENF-08 | vérifié | aucune syntaxe postérieure à Safari iOS 16 dans les modules |
| ENF-09 | vérifié | contrastes mesurés et testés, cibles à 56 px, aucune taille de police en px fixe |
| ENF-10 | vérifié | index unique `(ticket_id, type)` ; `cout_centimes` et `cout_fournisseur` en base |
| ENF-11 | **éprouvé** | 50 établissements, 3 mois, 225 000 tickets (81 Mo) : aucun balayage séquentiel, tout passe par index |

### Ce que la campagne de charge a mesuré

Les chiffres ci-dessus viennent d'une base chargée à la cible d'ENF-11 : 50
établissements, 1 500 visites par mois chacun, trois mois d'historique, soit
225 000 tickets et 81 Mo. Mesuré sous le rôle **applicatif**, donc avec le RLS
actif — mesurer en propriétaire aurait contourné les politiques et donné des
chiffres faux :

| Requête | Temps | Plan |
| --- | --- | --- |
| Tableau du jour (lecture principale de l'accueil) | 0,27 ms | index |
| Position d'un client par sa référence | 0,07 ms | index |
| Numéro du jour suivant (chemin d'insertion) | 0,06 ms | index |
| Tableau de bord sur 30 jours | 2,4 ms | index |
| Mesure du pilote, jointure pleine sur 30 jours | 0,8 ms | index |
| Export complet d'une clinique (4 500 tickets) | 3,8 ms | index |

Aucune de ces requêtes ne tombe en balayage séquentiel sur `ticket` à ce
volume. **ENF-11 tient**, et sans refonte.

**Ce que la mesure d'ENF-01 ne prouve pas.** Les 68 ms sont la part du serveur,
mesurée en local : il reste 930 ms de budget pour le réseau mobile dégradé que
l'exigence vise. Le serveur consomme donc 7 % de l'enveloppe. La part réseau,
elle, dépend du poids de la page et du nombre d'allers-retours — ce qui mène au
point suivant.

### Les polices distantes ont été retirées

Les neuf pages chargeaient Lexend et Source Sans 3 depuis `fonts.googleapis.com`.
Le rendu n'était pas bloqué — `display=swap` et une pile système étaient en
place — donc ce n'était pas un problème de vitesse. C'en était un de
**transfert** : chaque chargement de page envoyait l'adresse IP du client à un
tiers hors UE, sur un produit dont toute la position tient en « nous ne
collectons qu'un mobile et un numéro de ticket, effacés le lendemain ».

Les déclarations sont supprimées et la pile système est devenue le choix assumé.
Ce qui a été rattrapé au passage, parce que ça venait des polices et non du
CSS : les **chiffres tabulaires**, désormais déclarés explicitement — sans eux
la file tressaute quand un rang passe de 9 à 10 et le grand nombre du mur danse
à chaque appel — et les graisses **650 et 750**, qui n'existent dans aucune
police système et retombaient sur 400, aplatissant la hiérarchie des titres.

**Pour revenir aux polices de marque, il faut les AUTO-HÉBERGER**, pas rétablir
le lien distant. Les deux sont sous licence SIL OFL, qui autorise la
redistribution : déposer les `woff2` dans `app/polices/` et déclarer les
`@font-face` localement. La note est dans `attenso.css`, à l'endroit où
quelqu'un serait tenté de recoller le lien.

Mesure de la page client après retrait : **44 Ko sur 7 requêtes, une seule
origine**, et tout passe au cache du service worker après la première visite.
Les deux origines tierces supprimées, c'est deux poignées de main DNS + TLS en
moins avant que le texte ne soit mis en forme — ce qui compte précisément sur
le « réseau mobile dégradé » que vise ENF-01.

**La colonne `journal_acces.ip` reste vide, et ce n'est pas un oubli.** EC-03
limite la collecte à un numéro de mobile et un numéro de ticket : y ajouter une
adresse IP serait une troisième donnée personnelle, interdite par le cahier des
charges. L'adresse sert uniquement, en mémoire, à la limite de débit. Un test
vérifie qu'aucune IP n'est consignée — précisément pour que personne ne
« répare » cette colonne.

**EC-04 manquait.** Le formulaire du comptoir disait la finalité et la durée de
conservation, mais ne nommait pas le **responsable de traitement** — et ce
responsable est la clinique, pas l'éditeur : c'est elle qui décide de la
finalité. Son nom vient donc du serveur (`GET /t/{slug}/clinique`), et la
mention est dépliable sous le bouton plutôt que derrière un lien qui ferait
quitter la prise de ticket. Si le nom ne se charge pas, la mention reste vraie
mais générique : on n'affiche jamais un faux responsable.

## Ce qui n'est pas fait

- **Limiteur de débit partagé.** En mémoire, donc juste pour une instance.

## Le design, et ce qu'il refuse

Cinq écrans, un seul socle : `app/jetons.css`. Aucune feuille n'écrit de valeur
brute — elles nomment un rôle. C'est ce qui permet au mode sombre d'exister
sans dupliquer une règle, et à un contraste de se corriger en un point plutôt
qu'en trente.

**Les couleurs ne sont pas choisies à l'œil.** Chaque paire texte/fond est
mesurée, le ratio est en commentaire à côté de la valeur, et
`src/test/js/jetons.test.js` les revérifie à chaque `./mvnw test`. L'audit
initial a trouvé une vraie faute : la bordure des champs de saisie était à
**1,23:1** contre les 3:1 qu'exige WCAG 1.4.11 — un contour invisible pour une
vue faible. Les bordures de *contrôle* et les séparations *décoratives* sont
désormais deux jetons distincts, parce que la règle ne s'applique qu'aux
premières.

**Le mode sombre n'est pas une inversion.** Un vert foncé lisible sur papier
tombe à 2,16:1 sur fond sombre : chaque accent a donc sa propre valeur, mesurée
à part. Et une surface qui doit trancher avec le fond doit y être plus
*claire*, pas plus foncée — le premier jet faisait l'inverse et la bande
disparaissait. Un test le garde maintenant dans les deux sens.

Une seconde faute est venue de la même famille, trouvée en ajoutant le bloc de
mesure : le texte des bandeaux d'information est posé sur le *voile* d'accent,
pas sur le fond, et en mode sombre la paire ne donnait que **4,28:1**. Le vert
d'un aplat et le vert d'un texte posé sur ce voile sont deux rôles distincts —
d'où `--accent-texte`, séparé de `--accent`. La classe concernée porte aussi le
bandeau des gestes en attente, donc l'écran de panne, là où la lecture compte le
plus. La paire est maintenant dans le test.

**La couleur n'est jamais seule à porter l'information.** Chaque étiquette de
la file a un pictogramme en plus de sa teinte : une flèche pour « appelé », un
point pour « prévenu », une flèche de retour pour « absent ». Un daltonien lit
la même file que les autres.

**Ce que le système de design a recommandé et que j'ai refusé.** Le motif
proposé pour une vitrine réclame témoignages, logos clients et preuve sociale.
Attenso n'a aucun client : les inventer serait fabriquer des preuves. La page
dit donc ce qui est vrai — le produit est jeune, il n'y a rien à citer — et
propose à la place ce qui se vérifie : trois minutes pour s'inscrire, une
résiliation en un clic, et un compteur de départs qui dit dès la première
semaine si l'outil sert. L'appel à l'action mène à l'inscription payante.

Les cibles tactiles sont à 56 px, au-dessus des 44 d'Apple et des 48 de
Material. Ce n'est pas du zèle : le tableau est touché au doigt, sur une
tablette posée sur un comptoir, par quelqu'un qui tient un animal de l'autre
main.

## Les tests des écrans

La logique des pages vit dans des modules (`app/vue-file.js`, `app/vue-suivi.js`,
`app/vue-ecran.js`, `app/formats.js`) et non dans des balises `<script>` : ce
qui est enfermé dans du HTML ne se teste pas, et c'est là que des défauts se
sont logés — la page client annonçait « 2 personnes avant vous » à quelqu'un
qu'on venait d'appeler.

Les modules s'importent **relativement** (`./formats.js`), ce qui les rend
chargeables à l'identique par le navigateur et par node. Les pages, elles,
importent en absolu (`/app/…`) puisque leur URL varie (`/s/abc`, `/t/slug`).

**Le test qui compte le plus lit le code Java.** `vue-file.js` porte un miroir
de règles du serveur — la liste des états ouverts, et les transitions que les
boutons proposent. Un miroir qui dérive ment à l'ASV pendant une panne, au
moment précis où elle n'a que lui. Le test analyse donc `Statut.java` et vérifie
que les deux coïncident. Vérifié en le cassant exprès dans les deux sens : un
bouton pour une transition refusée, et un changement d'état côté serveur.

**Ce qui n'est pas couvert, et reste vérifié à la main :** le journal IndexedDB
et son rejeu, et le service worker. Les deux exigent un vrai navigateur ; la
procédure de répétition est plus haut, et elle a été jouée dans Chrome.

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
| `mesure` | instrumentation du pilote : origine, arrivants, taux, export |
| `affiche` | le QR du comptoir : encodage et tracé (EF-101) |
| `amorce` | installation de la première clinique |

L'expéditeur SMS d'une clinique se règle à l'installation
(`--attenso.amorce.expediteur-sms=Cerisiers`) ou en base ensuite.

Côté client, `app/journal.js` porte IndexedDB (les gestes et le dernier
tableau), `app/attenso.js` la file de rejeu et l'état de la liaison, `sw.js` la
coquille hors réseau.

Les migrations se lisent comme de la documentation : `V1__socle.sql` porte le
schéma et dit pourquoi il n'y a ni colonne de rang ni table de compteurs,
`V2__rls.sql` porte le cloisonnement et dit pourquoi il faut deux rôles, et
`V6__mesure_pilote.sql` ajoute l'origine du ticket et le comptage des
arrivants — avec sa politique RLS, qu'un test de cloisonnement vérifie comme
les autres.
