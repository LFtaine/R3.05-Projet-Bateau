# Simulation concurrente d’un pont mobile

Binôme : Louis FONTAINE et Armand PIVERT

Version reprise à partir du cours R3.05 fourni. Voir **CONFORMITE_COURS.md** pour les correspondances page par page, les choix de modélisation et les limites.

## Exécution

Java 8 ou supérieur suffit pour exécuter le JAR. Dans un terminal ouvert dans ce dossier :

```text
java -jar projet.jar
java -jar projet.jar --rapide
java -jar projet.jar --rapide --texte --seed=42
```

Le mode normal utilise 6 segments par sens et attend **60 secondes après la fermeture complète** avant une nouvelle ouverture. Chaque déplacement dure 700 ms ; ouverture et fermeture durent chacune 2 s. Le mode accéléré utilise 35 ms par déplacement, 100 ms par manœuvre, 120 ms par bateau et 1,5 s entre deux cycles. Il sert aux démonstrations et ne représente pas le délai réglementaire du sujet.

L’animation utilise les séquences ANSI : préférer Windows Terminal, PowerShell récent ou un terminal Linux/macOS. `--texte` affiche le journal complet à la fin, sans animation. La simulation lance 45 véhicules et 10 bateaux puis attend leurs threads et la fermeture complète. Le nombre d’acteurs est donc 55. La graine fixe les tirages ; l’ordre exact d’ordonnancement des threads reste variable.

Si `java` ne fonctionne pas, vérifier `java -version` et le PATH. Sur la machine de préparation, le raccourci Oracle du PATH échoue silencieusement ; l’exécutable direct `C:\Program Files\Java\jre1.8.0_503\bin\java.exe` fonctionne. Exemple PowerShell :

```powershell
& 'C:\Program Files\Java\jre1.8.0_503\bin\java.exe' -jar projet.jar --rapide
```

## Recompiler et tester

Un **JDK 8 ou supérieur** est nécessaire, avec `java`, `javac` et `jar` dans le PATH.

Windows : `powershell -ExecutionPolicy Bypass -File .\compiler.ps1`, puis `powershell -ExecutionPolicy Bypass -File .\tester.ps1`.

Linux/macOS : `sh compiler.sh`, puis `sh tester.sh`.

Test long avec une minute réelle : `powershell -ExecutionPolicy Bypass -File .\tester.ps1 -Minute` ou `sh tester.sh --minute`.

Les sources applicatives sont dans `projet/src/voyage`, les tests autonomes dans `projet/test/voyage`. Les scripts excluent les tests du JAR et produisent les classes dans `build/`.

## Organisation et synchronisation

Chaque véhicule et bateau implémente `Runnable` et possède son propre thread. `Pont` est le moniteur central : ses données sont protégées par le verrou `this`. Les réservations, changements d’état, inscriptions FIFO et décisions d’entrée sont atomiques. Les opérations sont séparées en méthodes synchronisées nommées `inscrireVehicule`, `entrerVehicule`, `avancerVehicule`, `retirerVehicule`, `entrerBateau` et en étapes de commande du pont. Les prédicats sont réévalués dans des boucles `while` après chaque réveil `wait()` ; `notifyAll()` réveille les catégories d’acteurs potentiellement débloquées. Les `sleep()` sont effectués hors du verrou.

Deux tableaux de segments représentent les deux voies. Les indices sont relatifs au sens de déplacement ; l’affichage inverse ceux de droite vers gauche. Une voiture réserve 1 segment, un camion 2 et un convoi 4 à l’entrée, en une seule opération. Les véhicules avancent d’un segment : la nouvelle tête est réservée avant la libération de la queue, sous le même verrou. Les segments communs entre ancienne et nouvelle position restent réservés. À la sortie, le nombre de segments diminue progressivement jusqu’au départ de la queue.

L’arrivée d’un convoi bloque immédiatement les nouvelles entrées ordinaires opposées. Les véhicules engagés continuent. Pour deux convois opposés en attente, le plus ancien est admis en premier : le suivant ne peut pas l’empêcher de démarrer. Les véhicules du même sens restent autorisés dans la limite des autres contraintes. Cette règle d’arbitrage précise un cas non départagé dans le sujet et évite un interblocage.

Un prioritaire arrête seulement la progression dans son sens pendant sa traversée ; les réservations logiques de reprise restent en place. Un seul prioritaire par sens est admis à la fois, en ordre d’arrivée dans ce sens. Les prioritaires peuvent dépasser l’attente d’un convoi, mais jamais croiser un convoi opposé déjà engagé. La sécurité du pont reste supérieure à la priorité : aucun prioritaire n’entre lorsqu’une ouverture est prise en charge. Ceux qui attendent pendant l’ouverture ne figent pas les véhicules qui doivent évacuer le pont.

Les bateaux partagent une FIFO indépendante du sens. La tête seule traverse et reste dans la file jusqu’à sa sortie. La demande d’ouverture ferme toutes les entrées routières, puis le contrôleur attend l’évacuation. Il suit FERME → OUVERTURE → OUVERT → FERMETURE → FERME. Tant que des bateaux attendent, le pont reste ouvert ; un trafic fluvial continuellement non vide peut donc retarder les voitures, conformément à la règle du sujet.

La fermeture complète fixe l’échéance avec `System.nanoTime()` pour que les changements d’horloge civile n’affectent pas le délai. Un `Timer/TimerTask` vérifie la file à l’échéance. Le réveil se réarme si nécessaire. Une arrivée après l’échéance peut déclencher directement un cycle.

Les blocs `finally` libèrent les réservations et retirent les inscriptions même sur interruption. Les interruptions sont rétablies par les acteurs. `close()` annule le Timer et réveille les attentes ; le lancement normal attend la fin des acteurs avant cet arrêt. L’interruption simule le retrait instantané d’un acteur, et non une procédure physique d’évacuation d’un véhicule en panne.

## Vérification

Les tests couvrent les convois opposés, le blocage des entrées, la FIFO commune, l’évacuation avant ouverture, un prioritaire arrivé après une demande, le délai entre deux cycles, l’arrêt/reprise, l’indépendance des sens, les prioritaires successifs et les interruptions en attente et en traversée. Une charge de 20 graines × 100 acteurs vérifie la terminaison avec une limite temporelle.

À chaque événement, des invariants vérifient l’absence de circulation routière pendant les manœuvres, la FIFO fluviale, l’exclusion des convois opposés, l’absence de réservations orphelines ainsi que la longueur et la continuité des réservations. `Segment` refuse l’écrasement d’une réservation. Les exceptions des threads de test sont propagées au résultat du test. Ce sont des vérifications exécutables, pas une preuve formelle pour tous les ordonnancements possibles.

`VALIDATION.txt` contient les résultats réellement obtenus. `UML.png` et `UML.svg` présentent les classes ; `UML.puml` est la version modifiable. `demonstration.avi` est une vidéo muette de moins de deux minutes, reconstruite à partir des écrans ANSI réellement émis par le JAR ; elle affiche une exécution en mode accéléré. La vitesse de lecture est ralentie pour faciliter l’observation. `DEMONSTRATION.md` fournit un fil de présentation orale.

## Contenu du livrable

- `CONFORMITE_COURS.md` : justification avec pages du cours et tableau de couverture du sujet.
- `projet/src/voyage/*.java` : sources commentées.
- `projet/test/voyage/TestsPont.java` : tests sans dépendance externe.
- `projet.jar` : exécutable compatible Java 8.
- `UML.png`, `UML.svg`, `UML.puml` : schéma des classes.
- `demonstration.avi` : vidéo de démonstration.
- `PARTIES_NON_REALISEES.txt` : limites et contrainte explicitement exclue du sujet.
- `Projet-OS.pdf` : sujet original, inchangé.
