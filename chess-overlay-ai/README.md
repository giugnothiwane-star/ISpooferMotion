# Chess Overlay AI Ultimate

Assistant d'échecs Android (Kotlin, Jetpack Compose) qui embarque le **vrai moteur Stockfish 17.1**,
compilé depuis les sources officielles pour ARM64 et exécuté **localement sur le téléphone**
(aucune clé d'API, aucun serveur, aucun abonnement). Interface entièrement en français, optimisée
pour le Samsung Galaxy S23 Ultra (Snapdragon 8 Gen 2, instructions ARMv8.2 *dotprod*).

> ⚖️ **Jeu loyal** : utiliser l'aide d'un moteur pendant une partie en ligne ou en tournoi contre un
> humain est interdit par toutes les plateformes et fédérations. L'application ne contourne aucune
> protection ; le déplacement automatique n'existe que dans les parties locales contre Stockfish.

---

## 1. Installer l'application depuis le téléphone (sans ordinateur)

L'APK est construit gratuitement par **GitHub Actions** (serveurs de GitHub), puis téléchargé
directement sur le téléphone.

1. Sur le S23 Ultra, ouvrez l'application **GitHub** (ou github.com dans Chrome/Samsung Internet,
   en mode « version pour ordinateur » si un bouton manque).
2. Ouvrez le dépôt, onglet **Actions** → **« Chess Overlay AI — APK Android »**.
   Le workflow démarre tout seul à chaque modification du dossier `chess-overlay-ai/`. Pour le lancer
   à la main : **Run workflow** → choisissez la branche → **Run workflow**.
3. Attendez la coche verte (≈ 10 à 15 min la première fois : compilation de Stockfish ; ≈ 5 min ensuite
   grâce au cache).
4. Ouvrez **Releases** (page d'accueil du dépôt, colonne de droite) → dernière version
   « Chess Overlay AI — build N » → touchez **ChessOverlayAI-N.apk**.
   *(Autre possibilité : en bas de la page de l'exécution du workflow, l'artefact « ChessOverlayAI-apk »
   — c'est un fichier .zip qu'il faut décompresser avec « Mes fichiers ».)*
5. Ouvrez le fichier téléchargé. Android demande d'autoriser l'installation depuis votre navigateur :
   **Paramètres → Autoriser depuis cette source**, puis **Installer**.
6. Les versions suivantes s'installent par-dessus la précédente (même signature) : vos parties et
   réglages sont conservés.

### Premier lancement

* **Menu flottant** : Accueil → « Autoriser l'affichage par-dessus » → activez *Chess Overlay AI*
  → revenez et touchez « Lancer le menu flottant ». Android 13+ demande aussi l'autorisation des
  notifications (la notification indique que le menu est actif et permet de le fermer).
* **Capture d'écran** : au premier « Capturer l'écran » du menu flottant, Android affiche sa demande
  officielle de partage d'écran (choisissez « Partager tout l'écran » sur One UI). Elle est valable
  jusqu'à ce que vous fermiez le menu ou arrêtiez le partage depuis la barre d'état.

---

## 2. Fonctionnalités réellement implémentées

| Domaine | Ce qui fonctionne |
|---|---|
| **Moteur** | Stockfish 17.1 (GPL v3) en UCI : profondeur, temps, MultiPV 1–5, threads, hachage, arrêt/reprise, « plus profond », détection des mats, `searchmoves`, limitation de force `UCI_Elo`. |
| **Analyse du meilleur coup** | Pièce, couleur, case de départ/arrivée, SAN français et international, UCI, type de coup (normal, capture, prise en passant, petit/grand roque, promotion), pièce capturée et sa case, explication, meilleure réponse adverse, suite de la variante — tout est dérivé du calcul du moteur et des règles. |
| **Variantes** | Les N meilleures lignes avec évaluation, variante complète, explication tactique, visualisation pas à pas sur l'échiquier, « jouer ». |
| **Captures** | Chaque prise possible évaluée par Stockfish (`searchmoves`) et par l'échange statique (SEE) : favorable / équilibrée / défavorable, conséquences. |
| **Tactique** | Pièces en prise, menacées, fourchettes, clouages, enfilades, attaques découvertes, échecs, mats en 1, menaces de mat. |
| **Échiquier** | Pièces vectorielles (3 styles), 6 thèmes, toucher ou glisser-déposer, destinations légales, animations, flèches, dernier coup, échec, coordonnées, rotation, choix de promotion. |
| **Règles** | Coups légaux complets (roques, prise en passant, promotion, échec, mat, pat, 50 coups, triple répétition, matériel insuffisant) — validés par *perft* sur 5 positions de référence. FEN, PGN (lecture/écriture, variantes ignorées). |
| **Reconnaissance** | Détection de l'échiquier sur une capture, 64 cases, couleurs et types de pièces, orientation, trait et prise en passant suggérés via les cases surlignées, cases incertaines signalées, **correction obligatoire dans l'éditeur avant toute analyse**, apprentissage à partir de vos corrections. |
| **Menu flottant** | Bulle déplaçable rangée contre le bord, panneau déplaçable, réduire/fermer, transparence et taille réglables, portrait/paysage, capture → vérification → analyse sans quitter l'application en cours. |
| **Entraînement** | Partie contre Stockfish (10 niveaux, indice, annulation, pilote automatique local), 25 exercices validés par Stockfish + exercices créés depuis vos erreurs, 3 865 ouvertures (base Lichess CC0), 11 finales, revue de partie (classification des coups, précision, graphique), relecture. |
| **Données** | Room (parties, positions, historique des analyses), DataStore (préférences). |

### Limites connues (honnêtement)

* **Reconnaissance d'image** : livrée avec des gabarits de 7 jeux de pièces libres et un décodage qui
  respecte les règles (un roi par camp, pas de pion en 1re/8e rangée) ; sur des jeux de pièces jamais vus
  (tests internes), ≈ 97 % des pièces sont reconnues et les cases incertaines sont encadrées. Si l'échiquier
  n'est pas trouvé, vous pouvez le **cadrer à la main** sur la capture. En cas de problème, Réglages →
  *Diagnostic* → « Copier le journal » donne le détail technique à transmettre.
  Elle est conçue pour les échiquiers **numériques plats** (captures d'écran
  d'applications et de sites). Les photos d'un vrai échiquier ou en perspective ne sont pas prises en
  charge. Les types de pièces sont reconnus grâce à des gabarits : ceux des 3 jeux intégrés sont
  préchargés ; pour un autre jeu de pièces, capturez une fois une **position initiale** (apprentissage
  automatique) ou corrigez la première reconnaissance — l'application apprend de vos corrections.
* Le **trait** n'est déductible d'une image que si le dernier coup est surligné ; sinon vous le choisissez.
* Le binaire Stockfish est compilé pour **ARM64** uniquement (tous les téléphones récents, dont le S23 Ultra).
* Langue : français uniquement.
* L'interface a été vérifiée par compilation, tests JVM et rendus Compose (Paparazzi), mais pas sur un
  appareil physique depuis l'environnement de développement : signalez tout problème rencontré sur le S23 Ultra.

---

## 3. Architecture

```
chess-overlay-ai/
├── app/            Application : écrans Compose, menu flottant (service + MediaProjection), navigation
├── core/chess      Règles, FEN, PGN, SAN (Kotlin pur, testé par perft)
├── core/engine     Pilote UCI de Stockfish (processus, flux d'analyse, arrêt/annulation)
├── core/analysis   Description des coups, tactique, explications, captures, revue, ouvertures, exercices, finales
├── core/vision     Reconnaissance d'échiquier sur image (Kotlin pur)
├── core/data       Room + DataStore
├── core/ui         Thème premium, composants « verre », échiquier interactif, pièces vectorielles
└── scripts/build_stockfish.sh   Compilation officielle de Stockfish (NDK) + vérification SHA-256 des réseaux NNUE
```

Le binaire est placé dans `app/src/main/jniLibs/arm64-v8a/libstockfish.so` : Android l'extrait dans le
dossier des bibliothèques natives, seul endroit où un programme peut être exécuté depuis Android 10.

### Compiler soi-même (ordinateur Linux, optionnel)

```bash
cd chess-overlay-ai
ANDROID_HOME=/chemin/sdk scripts/build_stockfish.sh android    # Stockfish ARM64
scripts/build_stockfish.sh host                                # Stockfish x86-64 pour les tests
./gradlew test :app:assembleRelease
```

---

## 4. Licences

* Application : **GNU GPL v3** (voir `LICENSE`).
* **Stockfish 17.1** : GPL v3 — auteurs dans `STOCKFISH_AUTHORS` ; sources exactes :
  <https://github.com/official-stockfish/Stockfish/tree/sf_17.1> ; réseaux NNUE
  `nn-1c0000000000.nnue` et `nn-37f18f62d772.nnue` (dépôt officiel `official-stockfish/networks`).
* Pièces : *Cburnett* (Colin M.L. Burnett, GPLv2+), *Merida* (Armando Hernandez Marroquin, GPLv2+),
  *Chessnut* (Alexis Luengas, Apache 2.0) — via le projet Lichess.
* Ouvertures : `lichess-org/chess-openings`, domaine public (CC0).
* Sons : synthétisés pour l'application.
