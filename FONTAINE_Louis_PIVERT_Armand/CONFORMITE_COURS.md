# Justification des choix à partir du cours

Référence : **cours-OS.pdf**, R3.05 Programmation système, M. Khayata, 2026-2027, 201 pages. Les numéros ci-dessous sont ceux des pages du PDF. Le sujet **Projet-OS.pdf** fixe les règles de circulation ; le cours fournit les outils pour les programmer. Les références du cours ne sont pas présentées comme des règles supplémentaires du sujet.

## Correspondance avec les notions enseignées

| Cours | Notion | Application dans les sources |
|---|---|---|
| p. 138 | Accès concurrents et résultats dépendant de l'ordonnancement | Les décisions d'entrée et les réservations se font sous le même verrou, jamais en deux opérations séparées accessibles aux autres threads. |
| p. 143 | Section critique et exclusion mutuelle | `Pont` est la ressource commune. Les méthodes qui modifient les voies, les files et les états sont synchronisées sur ce même objet. |
| p. 144 et 153 | Progression, attente et interblocage | Un ordre d'arrivée départage les convois opposés. Un véhicule long ne retient jamais un premier segment en attendant les autres. Voir les limites d'équité ci-dessous. |
| p. 164-165 | Mémoire commune et variables propres aux threads | Le pont et les segments sont partagés ; `queue` dans `traverser()` est une variable locale au thread du véhicule. |
| p. 188-189 | `Runnable`, `Thread` et `start()` | `Vehicule` et `Bateau` implémentent `Runnable`. `Main.lancer()` crée un `Thread` distinct et appelle `start()`. |
| p. 190 | Ordonnancement imprévisible | Les tests exigent des règles d'ordre métier, pas un ordre complet identique à chaque exécution. Une graine identique ne fixe pas l'ordonnancement. |
| p. 191 | `sleep()` et `join()` | Le déplacement et les manœuvres utilisent `sleep()` ; `Main` attend les acteurs par `join()`. |
| p. 196 et 198 | Méthodes `synchronized` et lectures cohérentes | `entrerVehicule()`, `avancerVehicule()`, `retirerVehicule()` et les opérations fluviales utilisent le moniteur de `Pont`. `evenements()` lit le journal avec ce même moniteur. |
| p. 197 | Délimitation d'une section critique | Les méthodes de traversée orchestrent les opérations ; leurs pauses ne sont pas dans une méthode synchronisée. Un acteur endormi ne monopolise donc pas le verrou du pont. |
| p. 199 | `wait()` libère le moniteur et exige de le détenir | Chaque attente métier se trouve dans une méthode synchronisée de `Pont`, sur ce même objet. |
| p. 199-200 | Condition retestée dans `while` | L'entrée routière, la progression, les prioritaires, la FIFO et le contrôleur retestent leur prédicat après chaque réveil. |
| p. 199-200 | `notifyAll()` pour plusieurs conditions différentes | La libération d'un segment, une sortie ou une transition peuvent débloquer des catégories différentes. Tous sont réveillés puis retestent leurs conditions. |
| p. 200 | Traitement d'`InterruptedException` | Les acteurs rétablissent le statut avec `Thread.currentThread().interrupt()`. Les blocs `finally` de `Pont` nettoient les réservations et les files. |

Exemple central, dans `entrerVehicule()` :

```java
private synchronized void entrerVehicule(Vehicule vehicule)
        throws InterruptedException {
    while (!peutEntrer(vehicule)) {
        verifierActif();
        wait();
    }
    // Réservation de toute la longueur, avec le verrou toujours détenu.
}
```

Le test du prédicat et la réservation font partie de la même section critique. `wait()` libère le moniteur pendant l'attente ; le thread doit l'obtenir de nouveau avant de continuer. `notifyAll()` ne fait pas passer immédiatement les threads réveillés devant le détenteur du verrou.

## Règles du sujet et méthodes responsables

| Sujet | Réalisation |
|---|---|
| §1 : un thread par véhicule et bateau | `Main.lancer()`, `Vehicule.run()`, `Bateau.run()` |
| §2 : quatre états et évacuation complète | `attendreEvacuation()`, `ouvrirEtAttendreBateaux()`, `terminerFermeture()` |
| §3 : deux voies et réservations 1/2/4 segments | `Segment`, `entrerVehicule()`, `avancerVehicule()` |
| §3.3 : entrée opposée bloquée dès l'arrivée du convoi | Inscription dans `convois` avant `wait()`, vérifications dans `peutEntrer()` |
| §3.3 : aucun véhicule opposé présent pendant un convoi | Vérification de la voie et de l'ensemble `presents`, y compris les prioritaires |
| §4 : FIFO commune aux deux sens | `bateaux.addLast()`, `peekFirst()`, `entrerBateau()` |
| §4 : aucun croisement fluvial | Un unique `bateauEnCours`, réservé sous le moniteur |
| §5 : une minute après fermeture complète | Échéance fixée dans `terminerFermeture()`, `Timer` et `TimerTask` dans `programmerReveil()` |
| §6 : arrêt du même sens et réservations de reprise | `urgences[sens]`, `avancerVehicule()`, `entrerPrioritaire()` |

Les indices internes sont relatifs au sens de marche : 0 est toujours l'entrée du véhicule. L'affichage inverse les indices du sens droite vers gauche pour représenter correctement la position physique.

## Points à expliquer lors de la présentation

**Priorité métier.** Le passage d'une ambulance est décidé par les données du moniteur, pas par `Thread.setPriority()`. Une priorité d'ordonnancement ne peut pas remplacer une règle d'exclusion d'accès au pont.

**Arrêt et reprise.** La circulation routière est arrêtée de manière coopérative avec `wait()` et reprise avec `notifyAll()`, selon les exemples des pages 199-200. Les pages 191 et 193 mentionnent aussi des primitives anciennes. Le projet emploie l'interruption coopérative et les moniteurs ; il n'appelle pas `stop()`, `suspend()` ou `resume()`. Ces anciennes méthodes sont dépréciées et lèvent systématiquement `UnsupportedOperationException` depuis Java 20 : [documentation OpenJDK](https://inside.java/2022/11/09/quality-heads-up/), [API Thread Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html). Cette adaptation permet de respecter le mécanisme de coordination enseigné et de fonctionner sur les JDK actuels.

**Acquisition de plusieurs segments.** À l'entrée d'un camion, les deux cellules sont testées puis réservées sous le même verrou. Pour avancer d'une cellule, l'ancienne et la nouvelle position se recouvrent : les cellules communes restent réservées, la nouvelle tête est prise et la queue libérée atomiquement. Le même raisonnement vaut pour quatre cellules. La queue sort progressivement du pont.

**Convois opposés.** Le premier convoi inscrit garde la priorité sur les convois opposés plus récents. Une demande ultérieure ne peut donc pas bloquer le convoi qui doit justement passer pour la débloquer. Les entrées ordinaires opposées restent bloquées ; les véhicules déjà engagés continuent.

**Sécurité avant priorité.** Un prioritaire attend si une ouverture est prise en charge ou si un convoi opposé est déjà engagé. En attente d'une ouverture, il ne fige pas les voitures qui doivent évacuer. Dès qu'il est admis, les autres véhicules de son sens cessent de progresser ; leurs réservations de reprise restent en place. Un prioritaire opposé à un convoi encore en attente peut passer avant lui, mais jamais le croiser une fois engagé.

**Progression et équité.** La simulation utilise un ensemble fini d'acteurs. Les tests bornent le temps de terminaison et vérifient les cas de blocage connus. La FIFO fluviale, l'arbitrage des convois et la FIFO des prioritaires par sens sont explicites. Le moniteur Java n'offre pas de garantie formelle d'attente bornée pour chaque voiture sous un flux infini d'acteurs plus prioritaires. De plus, le sujet impose de garder le pont ouvert tant que la FIFO fluviale reste non vide : un flux continu de bateaux peut retarder la route. Il serait inexact d'affirmer une absence universelle de famine. Les tests ne sont pas une preuve pour tous les ordonnancements.

**Timer et temps.** `Timer/TimerTask` est utilisé parce que le sujet le demande. `System.nanoTime()` mesure l'intervalle sans être affecté par une remise à l'heure du système ; il ne représente pas une date. Le mode normal conserve 60 secondes. Le mode `--rapide` utilise explicitement 1,5 seconde.

## Conventions de réalisation

Sources UTF-8, indentation à quatre espaces, accolades autour des blocs conditionnels, imports explicites, classes en `PascalCase`, méthodes et attributs en `camelCase`, attributs privés et `final` lorsqu'ils sont constants après construction. Les classes et les méthodes sont commentées. Aucun exécuteur, verrou explicite, sémaphore Java, `volatile`, thread virtuel ou bibliothèque externe n'est nécessaire : la synchronisation applicative repose sur les moniteurs étudiés.

`AutoCloseable`, le journal d'événements et les tests sont des aides à la fermeture et à la vérification. Ils ne remplacent aucun mécanisme de synchronisation du cours.
