package voyage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lance les threads avec start() puis attend leur fin avec join() (cours p. 188-191). */
public final class Main {
    /** Classe utilitaire : aucune instance n'est nécessaire. */
    private Main() {
    }

    /** Options : --rapide, --texte et --seed=N. Sans option, le délai du sujet est de 60 s. */
    public static void main(String[] args) throws InterruptedException {
        boolean rapide = false;
        boolean texte = false;
        long graine = 42;
        for (String argument : args) {
            if (argument.equals("--rapide")) {
                rapide = true;
            } else if (argument.equals("--texte")) {
                texte = true;
            } else if (argument.startsWith("--seed=")) {
                graine = Long.parseLong(argument.substring(7));
            } else {
                throw new IllegalArgumentException("Options : --rapide --texte --seed=N");
            }
        }

        Pont pont = new Pont(6, rapide ? 35 : 700, rapide ? 100 : 2_000,
                rapide ? 120 : 2_000, rapide ? 1_500 : 60_000, !texte);
        List<Thread> acteurs = new ArrayList<Thread>();
        try {
            Random hasard = new Random(graine);
            for (int i = 1; i <= 45; i++) {
                int sens = hasard.nextInt(2);
                Vehicule vehicule = creerVehicule(i, sens, hasard.nextInt(10), pont);
                lancer(vehicule, acteurs);
                if (i % 10 == 3 || i % 10 == 5) {
                    String direction = hasard.nextBoolean() ? "nord->sud" : "sud->nord";
                    lancer(new Bateau("B" + i, direction, pont), acteurs);
                }
                Thread.sleep(rapide ? 45 : 1_000 + hasard.nextInt(1_500));
            }
            for (Thread acteur : acteurs) {
                acteur.join();
            }
            if (!pont.attendreRepos(10_000)) {
                throw new IllegalStateException("Fermeture du pont incomplete");
            }
            if (texte) {
                for (Pont.Evenement evenement : pont.evenements()) {
                    System.out.println(evenement);
                }
            }
            System.out.println("Simulation terminee : " + acteurs.size() + " acteurs.");
        } finally {
            pont.close();
            for (Thread acteur : acteurs) {
                if (acteur.isAlive()) {
                    acteur.interrupt();
                }
            }
        }
    }

    /** Répartition : 50 % voitures, 20 % camions, 10 % convois et 20 % prioritaires. */
    private static Vehicule creerVehicule(int numero, int sens, int tirage, Pont pont) {
        if (tirage < 5) {
            return new Voiture("V" + numero, sens, pont);
        }
        if (tirage < 7) {
            return new Camion("C" + numero, sens, pont);
        }
        if (tirage < 8) {
            return new Convoi("X" + numero, sens, pont);
        }
        return new Prioritaire("P" + numero, sens, pont);
    }

    /** Un appel direct à run() serait séquentiel : c'est bien start() qui crée le thread. */
    private static void lancer(Runnable acteur, List<Thread> acteurs) {
        Thread thread = new Thread(acteur, acteur.toString());
        acteurs.add(thread);
        thread.start();
    }
}
