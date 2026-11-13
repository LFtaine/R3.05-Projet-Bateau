# Simulation concurrente d'un pont mobile

Projet R3.05 Programmation système — **Louis FONTAINE et Armand PIVERT**.

Chaque véhicule et bateau possède son propre thread Java. Le moniteur `Pont` coordonne les deux sens routiers et les deux sens fluviaux avec `synchronized`, `wait()` et `notifyAll()`.

## Diagramme UML



Les héritages, interfaces Java, compositions et multiplicités sont représentés explicitement. Les principales opérations publiques et les attributs métier sont montrés ; les constructeurs et les méthodes privées d'implémentation sont omis. Le SVG est généré directement depuis le fichier PlantUML, afin de garder les deux versions cohérentes.

## Exécuter

Java 8 ou supérieur suffit. Depuis la racine du dépôt :

```sh
cd FONTAINE_Louis_PIVERT_Armand
java -jar projet.jar
```

Pour une démonstration accélérée ou un journal sans animation :

```sh
java -jar projet.jar --rapide
java -jar projet.jar --rapide --texte --seed=42
```

Le mode normal respecte **60 secondes entre une fermeture complète et l'ouverture suivante**. Le mode `--rapide` réduit ce délai à 1,5 seconde pour la démonstration. La simulation lance 45 véhicules et 10 bateaux, attend leur fin puis la fermeture complète. Une graine fixe les tirages, mais pas l'ordonnancement des threads.

L'animation utilise des séquences ANSI ; employer un terminal compatible ou `--texte`. Vérifier `java -version` si la commande Java n'est pas reconnue.

## Compiler et tester

Un JDK 8 ou supérieur, avec `javac`, `jar` et `java` dans le PATH, est nécessaire. Toutes les commandes suivantes s'exécutent dans `FONTAINE_Louis_PIVERT_Armand/`.

Sous Windows :

```powershell
powershell -ExecutionPolicy Bypass -File .\compiler.ps1
```

Sous Linux ou macOS :

```sh
```


Les tests couvrent les convois opposés, les quatre états, la FIFO fluviale, les prioritaires, les interruptions et la terminaison de 20 simulations de 100 acteurs concurrents. Les erreurs des threads secondaires font échouer les tests. Les sources des tests restent dans le dépôt ; leurs fichiers compilés sont générés localement dans `build/` et ignorés par Git.

## Organisation

```text
FONTAINE_Louis_PIVERT_Armand/
├── projet/src/voyage/        Sources Java commentées
├── projet/test/voyage/       Tests autonomes
├── projet.jar               Exécutable livré
├── UML.png                  Diagramme des classes
├── compiler.ps1              Compilation
├── PARTIES_NON_REALISEES.txt Limites et périmètre du sujet
├── demonstration.avi        Démonstration muette de moins de deux minutes
└── Projet-OS.pdf             Sujet original
```

Le JAR et la vidéo font partie des livrables demandés. Les répertoires `build/`, `out/`, les `.class` et les réglages propres aux IDE ne sont pas versionnés.

## Principes de fonctionnement

- Une voiture, un camion et un convoi réservent respectivement 1, 2 et 4 segments. L'acquisition est atomique ; les segments communs restent réservés lors d'un déplacement.
- Un convoi bloque les nouvelles entrées opposées et attend la sortie complète des véhicules déjà présents. Les convois opposés sont départagés par ordre d'arrivée.
- Les bateaux partagent une FIFO commune aux deux sens. Une demande d'ouverture bloque toutes les nouvelles entrées routières, puis attend l'évacuation avant la manœuvre.
- Un prioritaire suspend seulement la progression de son sens. Les réservations de reprise restent en place. La sécurité mécanique et l'exclusion d'un convoi opposé engagé restent applicables.
- Un `Timer/TimerTask` vérifie les bateaux en attente après le délai calculé à la fermeture complète.

[Limites et parties non réalisées](FONTAINE_Louis_PIVERT_Armand/PARTIES_NON_REALISEES.txt) · [Sujet](FONTAINE_Louis_PIVERT_Armand/Projet-OS.pdf) · [Vidéo](FONTAINE_Louis_PIVERT_Armand/demonstration.avi)
