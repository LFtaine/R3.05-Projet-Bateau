package voyage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tests autonomes, sans bibliothèque externe ni mécanisme de synchronisation différent du cours. */
public final class TestsPont {
    private static final List<Throwable> erreurs = new ArrayList<Throwable>();
    private static int verifications;

    /** Classe utilitaire. */
    private TestsPont() {
    }

    /** Fait échouer le processus de test si la propriété attendue est fausse. */
    private static void verifier(boolean condition, String message) {
        verifications++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Capture aussi les erreurs émises dans les threads secondaires. */
    private static Thread lancer(Runnable acteur) {
        Thread thread = new Thread(acteur, acteur.toString());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable erreur) {
                synchronized (erreurs) {
                    erreurs.add(erreur);
                }
            }
        });
        thread.start();
        return thread;
    }

    /** Recherche un événement dans une copie cohérente du journal. */
    private static int index(Pont pont, String type, String nom) {
        List<Pont.Evenement> evenements = pont.evenements();
        for (int i = 0; i < evenements.size(); i++) {
            Pont.Evenement evenement = evenements.get(i);
            if (evenement.type.equals(type) && evenement.nom.equals(nom)) {
                return i;
            }
        }
        return -1;
    }

    /** Observe un état du scénario, avec pause et limite ; ce n'est pas l'attente métier du pont. */
    private static void attendre(Pont pont, String type, String nom) throws InterruptedException {
        long fin = System.nanoTime() + 4_000_000_000L;
        while (index(pont, type, nom) == -1 && System.nanoTime() < fin) {
            Thread.sleep(1);
        }
        verifier(index(pont, type, nom) != -1, "Evenement absent : " + type + " " + nom);
    }

    /** Une erreur ou un interblocage doit faire échouer la suite, pas laisser le test attendre sans fin. */
    private static void terminer(Pont pont, Thread... threads) throws InterruptedException {
        long fin = System.nanoTime() + 8_000_000_000L;
        for (Thread thread : threads) {
            thread.join(Math.max(1, (fin - System.nanoTime()) / 1_000_000));
            verifier(!thread.isAlive(), "Thread bloque : " + thread.getName());
        }
        verifier(pont.attendreRepos(3_000), "Pont non revenu au repos");
        synchronized (erreurs) {
            verifier(erreurs.isEmpty(), "Erreur d'acteur : " + erreurs);
        }
    }

    /** Vérifie l'ordre de deux événements, en exigeant que tous deux existent. */
    private static void avant(Pont pont, String typeA, String nomA, String typeB, String nomB) {
        int a = index(pont, typeA, nomA);
        int b = index(pont, typeB, nomB);
        verifier(a >= 0 && b > a, typeA + " " + nomA + " doit preceder " + typeB + " " + nomB);
    }

    /** Les véhicules opposés évacuent ; les convois en attente sont départagés sans blocage circulaire. */
    private static void testerConvois() throws InterruptedException {
        try (Pont pont = new Pont(6, 20, 10, 10, 80, false)) {
            Thread voiture = lancer(new Voiture("V", 1, pont));
            attendre(pont, "ENTREE", "V");
            Thread premier = lancer(new Convoi("X", 0, pont));
            attendre(pont, "ARRIVEE", "X");
            Thread second = lancer(new Convoi("Y", 1, pont));
            attendre(pont, "ARRIVEE", "Y");
            Thread oppose = lancer(new Voiture("W", 1, pont));
            terminer(pont, voiture, premier, second, oppose);
            avant(pont, "SORTIE", "V", "ENTREE", "X");
            avant(pont, "SORTIE", "X", "ENTREE", "Y");
            avant(pont, "SORTIE", "X", "ENTREE", "W");
        }
    }

    /** La demande bloque aussi les prioritaires ; les bateaux passent dans l'ordre commun aux deux sens. */
    private static void testerOuvertureEtFifo() throws InterruptedException {
        try (Pont pont = new Pont(6, 15, 20, 25, 150, false)) {
            Thread camion = lancer(new Camion("C", 0, pont));
            attendre(pont, "ENTREE", "C");
            Thread bateau1 = lancer(new Bateau("B1", "NS", pont));
            attendre(pont, "DEMANDE_OUVERTURE", "");
            Thread prioritaire = lancer(new Prioritaire("P", 0, pont));
            Thread bateau2 = lancer(new Bateau("B2", "SN", pont));
            attendre(pont, "BATEAU_ATTENTE", "B2 (SN)");
            terminer(pont, camion, bateau1, bateau2, prioritaire);
            avant(pont, "SORTIE", "C", "ETAT", "OUVERTURE");
            avant(pont, "BATEAU_SORTIE", "B1 (NS)", "BATEAU_PASSAGE", "B2 (SN)");
            avant(pont, "ETAT", "FERME", "PRIORITAIRE", "P");
            Thread bateau3 = lancer(new Bateau("B3", "NS", pont));
            terminer(pont, bateau3);
            verifier(intervalleOuvertures(pont) >= 150_000_000L, "Reouverture trop precoce");
            verifierTransitions(pont);
        }
    }

    /** Contrôle explicitement chaque transition de la machine à quatre états. */
    private static void verifierTransitions(Pont pont) {
        String[] cycle = {"OUVERTURE", "OUVERT", "FERMETURE", "FERME"};
        int rang = 0;
        for (Pont.Evenement evenement : pont.evenements()) {
            if (evenement.type.equals("ETAT")) {
                verifier(evenement.nom.equals(cycle[rang % 4]), "Transition d'etat invalide");
                rang++;
            }
        }
        verifier(rang >= 8 && rang % 4 == 0, "Deux cycles complets attendus");
    }

    /** Mesure de la première fermeture complète à la demande d'ouverture suivante. */
    private static long intervalleOuvertures(Pont pont) {
        long fermeture = -1;
        for (Pont.Evenement evenement : pont.evenements()) {
            if (fermeture == -1 && evenement.type.equals("ETAT") && evenement.nom.equals("FERME")) {
                fermeture = evenement.nanos;
            } else if (fermeture != -1 && evenement.type.equals("DEMANDE_OUVERTURE")) {
                return evenement.nanos - fermeture;
            }
        }
        throw new AssertionError("Seconde ouverture absente");
    }

    /** Le sens du prioritaire s'arrête et reprend ; le sens opposé reste mobile. */
    private static void testerPriorites() throws InterruptedException {
        try (Pont pont = new Pont(6, 45, 10, 10, 80, false)) {
            Thread camion = lancer(new Camion("C", 0, pont));
            attendre(pont, "ENTREE", "C");
            Thread oppose = lancer(new Voiture("O", 1, pont));
            attendre(pont, "ENTREE", "O");
            Thread premier = lancer(new Prioritaire("P", 0, pont));
            attendre(pont, "PRIORITAIRE", "P");
            Thread second = lancer(new Prioritaire("Q", 0, pont));
            terminer(pont, camion, oppose, premier, second);
            verifier(index(pont, "ARRET_URGENCE", "C") >= 0, "Arret du camion absent");
            avant(pont, "SORTIE", "P", "REPRISE", "C");
            avant(pont, "SORTIE", "P", "PRIORITAIRE", "Q");
            List<Pont.Evenement> evenements = pont.evenements();
            boolean opposeAvance = false;
            for (int i = index(pont, "PRIORITAIRE", "P") + 1; i < index(pont, "SORTIE", "P"); i++) {
                Pont.Evenement evenement = evenements.get(i);
                verifier(!evenement.type.equals("AVANCE") || !evenement.nom.equals("C"),
                        "Camion avance pendant la priorite");
                if (evenement.type.equals("AVANCE") && evenement.nom.equals("O")) {
                    opposeAvance = true;
                }
            }
            verifier(opposeAvance, "Sens oppose immobilise par la priorite");
        }
    }

    /** Un bateau arrivé pendant un passage rejoint la même FIFO, sans fermer prématurément le pont. */
    private static void testerArriveePontOuvert() throws InterruptedException {
        try (Pont pont = new Pont(6, 10, 10, 100, 80, false)) {
            Thread premier = lancer(new Bateau("B1", "NS", pont));
            attendre(pont, "BATEAU_PASSAGE", "B1 (NS)");
            Thread second = lancer(new Bateau("B2", "SN", pont));
            attendre(pont, "BATEAU_ATTENTE", "B2 (SN)");
            Thread troisieme = lancer(new Bateau("B3", "NS", pont));
            terminer(pont, premier, second, troisieme);
            avant(pont, "BATEAU_SORTIE", "B1 (NS)", "BATEAU_PASSAGE", "B2 (SN)");
            avant(pont, "BATEAU_SORTIE", "B2 (SN)", "BATEAU_PASSAGE", "B3 (NS)");
            int ouvertures = 0;
            for (Pont.Evenement evenement : pont.evenements()) {
                if (evenement.type.equals("ETAT") && evenement.nom.equals("OUVERTURE")) {
                    ouvertures++;
                }
            }
            verifier(ouvertures == 1, "Fermeture avant vidange de la FIFO");
        }
    }

    /** Un prioritaire ne croise pas un convoi opposé déjà engagé. */
    private static void testerSecuriteConvoiPrioritaire() throws InterruptedException {
        try (Pont pont = new Pont(6, 20, 10, 10, 80, false)) {
            Thread convoi = lancer(new Convoi("X", 0, pont));
            attendre(pont, "ENTREE", "X");
            Thread prioritaire = lancer(new Prioritaire("P", 1, pont));
            terminer(pont, convoi, prioritaire);
            avant(pont, "SORTIE", "X", "PRIORITAIRE", "P");
        }
    }

    /** Les nettoyages enfin exécutés doivent permettre à de nouveaux acteurs de traverser. */
    private static void testerInterruptions() throws InterruptedException {
        try (Pont pont = new Pont(6, 30, 20, 60, 80, false)) {
            Thread convoi = lancer(new Convoi("X", 0, pont));
            attendre(pont, "ENTREE", "X");
            convoi.interrupt();
            terminer(pont, convoi);
            Thread camion = lancer(new Camion("C", 1, pont));
            attendre(pont, "ENTREE", "C");
            Thread attente = lancer(new Convoi("Y", 0, pont));
            attendre(pont, "ARRIVEE", "Y");
            attente.interrupt();
            Thread bateau = lancer(new Bateau("B", "NS", pont));
            attendre(pont, "BATEAU_ATTENTE", "B (NS)");
            bateau.interrupt();
            terminer(pont, camion, attente, bateau);
            Thread prioritaire = lancer(new Prioritaire("P", 0, pont));
            attendre(pont, "PRIORITAIRE", "P");
            prioritaire.interrupt();
            terminer(pont, prioritaire);
            Thread passage = lancer(new Bateau("B2", "SN", pont));
            attendre(pont, "BATEAU_PASSAGE", "B2 (SN)");
            passage.interrupt();
            terminer(pont, passage);
            terminer(pont, lancer(new Voiture("V", 1, pont)));
        }
    }

    /** Vérifie la terminaison de 2 000 acteurs et la conservation des entrées/sorties. */
    private static void testerCharge() throws InterruptedException {
        for (int graine = 0; graine < 20; graine++) {
            try (Pont pont = new Pont(6, 1, 2, 2, 8, false)) {
                Random hasard = new Random(graine);
                List<Thread> threads = new ArrayList<Thread>();
                for (int i = 0; i < 100; i++) {
                    int sens = hasard.nextInt(2);
                    int type = hasard.nextInt(5);
                    String nom = "A" + i;
                    Runnable acteur;
                    if (type == 0) {
                        acteur = new Voiture(nom, sens, pont);
                    } else if (type == 1) {
                        acteur = new Camion(nom, sens, pont);
                    } else if (type == 2) {
                        acteur = new Convoi(nom, sens, pont);
                    } else if (type == 3) {
                        acteur = new Prioritaire(nom, sens, pont);
                    } else {
                        acteur = new Bateau(nom, sens == 0 ? "NS" : "SN", pont);
                    }
                    threads.add(lancer(acteur));
                }
                terminer(pont, threads.toArray(new Thread[threads.size()]));
                int entrees = 0;
                int sorties = 0;
                for (Pont.Evenement evenement : pont.evenements()) {
                    if (evenement.type.equals("ENTREE") || evenement.type.equals("PRIORITAIRE")) {
                        entrees++;
                    } else if (evenement.type.equals("SORTIE")) {
                        sorties++;
                    }
                }
                verifier(entrees == sorties, "Vehicule perdu");
            }
        }
    }

    /** Test optionnel avec le constructeur normal et une vraie minute de refroidissement. */
    private static void testerMinuteReelle() throws InterruptedException {
        try (Pont pont = new Pont(6)) {
            terminer(pont, lancer(new Bateau("Minute1", "NS", pont)));
            Thread second = lancer(new Bateau("Minute2", "SN", pont));
            second.join(70_000);
            verifier(!second.isAlive(), "Bateau non servi apres la minute");
            verifier(pont.attendreRepos(4_000), "Fermeture incomplete");
            long intervalle = intervalleOuvertures(pont);
            verifier(intervalle >= 60_000_000_000L, "Minute reelle non respectee");
            synchronized (erreurs) {
                verifier(erreurs.isEmpty(), "Erreur d'acteur : " + erreurs);
            }
            System.out.println("OK delai reel : " + intervalle / 1_000_000 + " ms");
        }
    }

    /** Lance la suite courante, ou seulement le test long avec --minute. */
    public static void main(String[] args) throws InterruptedException {
        if (args.length == 1 && args[0].equals("--minute")) {
            testerMinuteReelle();
            return;
        }
        testerConvois();
        System.out.println("OK convois, evacuation opposee, absence d'interblocage");
        testerOuvertureEtFifo();
        System.out.println("OK FIFO, quatre etats, demande d'ouverture, Timer");
        testerPriorites();
        System.out.println("OK priorites, arret/reprise, independance des sens");
        testerArriveePontOuvert();
        System.out.println("OK arrivees pendant ouverture et FIFO sans fermeture prematuree");
        testerSecuriteConvoiPrioritaire();
        System.out.println("OK priorite subordonnee a la securite des convois");
        testerInterruptions();
        System.out.println("OK interruptions en attente et en traversee");
        testerCharge();
        System.out.println("OK 20 simulations de 100 acteurs concurrents");
        System.out.println("SUCCES : " + verifications + " verifications");
    }
}
