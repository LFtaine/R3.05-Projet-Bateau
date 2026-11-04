package voyage;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class Pont {
    private static final long PAS = 700;
    private static final long MANOEUVRE = 2000;
    private static final long PASSAGE_BATEAU = 2000;
    private static final long DELAI_OUVERTURE = 60_000;

    private final int n;
    private final Segment[][] voies;
    private final int[] urgences = new int[2];
    private final int[] convois = new int[2];
    private final LinkedList<Bateau> file = new LinkedList<>();
    private final LinkedList<String> journal = new LinkedList<>();
    private final Timer timer = new Timer(true);

    private EtatPont etat = EtatPont.FERME;
    private boolean ouvertureDemandee = false;
    private int surPont = 0;
    private long prochaineOuverture = 0;
    private Bateau bateauEnCours;
    private Vehicule prioritaireEnCours;

    public Pont(int nombreSegments) {
        n = nombreSegments;
        voies = new Segment[2][n];
        for (int sens = 0; sens < 2; sens++) {
            for (int i = 0; i < n; i++) {
                voies[sens][i] = new Segment();
            }
        }
    }

    public void traverser(Vehicule v) throws InterruptedException {
        if (v.estPrioritaire()) {
            passerPrioritaire(v);
            return;
        }
        entrer(v);
        int queue = 0;
        int tete = v.getTaille() - 1;
        while (queue < n) {
            Thread.sleep(PAS);
            avancer(v, tete + 1, queue);
            tete++;
            queue++;
        }
        sortir(v);
    }

    private synchronized void entrer(Vehicule v) throws InterruptedException {
        int sens = v.getSens();
        log(v + " arrive à l'entrée");
        if (v instanceof Convoi) {
            convois[sens]++;
            log("Entrée opposée bloquée pour " + v);
        }
        while (!peutEntrer(v)) {
            wait();
        }
        for (int i = 0; i < v.getTaille(); i++) {
            voies[sens][i].occuper(v);
        }
        surPont++;
        log(v + " entre sur le pont");
    }

    private boolean peutEntrer(Vehicule v) {
        int sens = v.getSens();
        int autre = 1 - sens;
        if (etat != EtatPont.FERME || ouvertureDemandee || urgences[sens] > 0) {
            return false;
        }
        if (v instanceof Convoi) {
            if (!voieVide(autre)) {
                return false;
            }
        } else if (convois[autre] > 0) {
            return false;
        }
        for (int i = 0; i < v.getTaille(); i++) {
            if (!voies[sens][i].estLibre()) {
                return false;
            }
        }
        return true;
    }

    private boolean voieVide(int sens) {
        for (Segment s : voies[sens]) {
            if (!s.estLibre()) {
                return false;
            }
        }
        return true;
    }

    private synchronized void avancer(Vehicule v, int suivant, int queue) throws InterruptedException {
        int sens = v.getSens();
        boolean arrete = false;
        while (urgences[sens] > 0 || (suivant < n && !voies[sens][suivant].estLibre())) {
            if (urgences[sens] > 0 && !arrete) {
                arrete = true;
                log(v + " s'arrête sur la bande d'urgence");
            }
            wait();
        }
        if (arrete) {
            log(v + " reprend sa route");
        }
        if (suivant < n) {
            voies[sens][suivant].occuper(v);
        }
        voies[sens][queue].liberer();
        notifyAll();
        afficher();
    }

    private synchronized void sortir(Vehicule v) {
        surPont--;
        if (v instanceof Convoi) {
            convois[v.getSens()]--;
        }
        log(v + " sort du pont");
        notifyAll();
    }

    private void passerPrioritaire(Vehicule v) throws InterruptedException {
        int sens = v.getSens();
        synchronized (this) {
            urgences[sens]++;
            log(v + " arrive : priorité, les véhicules s'arrêtent");
            while (etat != EtatPont.FERME) {
                wait();
            }
            surPont++;
            prioritaireEnCours = v;
            log(v + " traverse le pont");
        }
        Thread.sleep(PAS * n / 2);
        synchronized (this) {
            urgences[sens]--;
            surPont--;
            prioritaireEnCours = null;
            log(v + " a quitté le pont");
            notifyAll();
        }
    }

    public void passerBateau(Bateau b) throws InterruptedException {
        synchronized (this) {
            file.add(b);
            log(b + " attend son tour");
        }
        tenterOuverture();
        synchronized (this) {
            while (etat != EtatPont.OUVERT || file.peek() != b) {
                wait();
            }
            bateauEnCours = b;
            log(b + " passe sous le pont");
        }
        Thread.sleep(PASSAGE_BATEAU);
        synchronized (this) {
            file.remove(b);
            bateauEnCours = null;
            log(b + " est passé");
            notifyAll();
        }
    }

    private synchronized void tenterOuverture() {
        if (etat == EtatPont.FERME && !ouvertureDemandee && !file.isEmpty()
                && System.currentTimeMillis() >= prochaineOuverture) {
            ouvertureDemandee = true;
            log("Ouverture demandée : entrées bloquées");
            new Thread(this::ouvrir, "Pont").start();
        }
    }

    private void ouvrir() {
        try {
            synchronized (this) {
                while (surPont > 0) {
                    wait();
                }
                changerEtat(EtatPont.OUVERTURE);
            }
            Thread.sleep(MANOEUVRE);
            synchronized (this) {
                changerEtat(EtatPont.OUVERT);
                while (!file.isEmpty()) {
                    wait();
                }
                changerEtat(EtatPont.FERMETURE);
            }
            Thread.sleep(MANOEUVRE);
            synchronized (this) {
                changerEtat(EtatPont.FERME);
                ouvertureDemandee = false;
                prochaineOuverture = System.currentTimeMillis() + DELAI_OUVERTURE;
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        tenterOuverture();
                    }
                }, DELAI_OUVERTURE);
                notifyAll();
                afficher();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void changerEtat(EtatPont nouvelEtat) {
        etat = nouvelEtat;
        log("Pont : " + nouvelEtat);
        notifyAll();
    }

    private void log(String message) {
        journal.add(LocalTime.now().withNano(0) + "  " + message);
        if (journal.size() > 10) {
            journal.removeFirst();
        }
        afficher();
    }

    private void afficher() {
        StringBuilder sb = new StringBuilder("\033[H\033[2J");
        sb.append("\n  ===== PONT MOBILE =====\n\n");
        sb.append("  État du pont : ").append(couleur(etat)).append(etat).append("\033[0m\n");
        if (prochaineOuverture > System.currentTimeMillis()) {
            LocalTime heure = LocalTime.ofInstant(Instant.ofEpochMilli(prochaineOuverture), ZoneId.systemDefault());
            sb.append("  Prochaine ouverture possible à ").append(heure.withNano(0)).append("\n");
        }
        sb.append("\n  Route\n");
        sb.append("  ---------------------------------------------\n");
        sb.append("  Gauche -> Droite  ");
        for (int i = 0; i < n; i++) {
            sb.append(cellule(voies[0][i]));
        }
        sb.append("  ->\n");
        sb.append("  Droite -> Gauche  ");
        for (int i = n - 1; i >= 0; i--) {
            sb.append(cellule(voies[1][i]));
        }
        sb.append("  <-\n");
        sb.append("  ---------------------------------------------\n");
        sb.append("  Bande d'urgence : ").append(prioritaireEnCours == null ? "libre" : prioritaireEnCours).append("\n");
        sb.append("\n  Rivière\n");
        sb.append("  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
        sb.append("  Sous le pont : ").append(bateauEnCours == null ? "personne" : bateauEnCours).append("\n");
        sb.append("  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
        sb.append("  Bateaux en attente : ");
        int rang = 0;
        for (Bateau b : file) {
            if (b != bateauEnCours) {
                sb.append(++rang).append(". ").append(b).append("   ");
            }
        }
        if (rang == 0) {
            sb.append("aucun");
        }
        sb.append("\n\n  V = voiture   C = camion   X = convoi   P = prioritaire\n");
        sb.append("\n  Derniers événements\n");
        for (String ligne : journal) {
            sb.append("  ").append(ligne).append("\n");
        }
        System.out.print(sb);
        System.out.flush();
    }

    private String couleur(EtatPont e) {
        switch (e) {
            case FERME:
                return "\033[32m";
            case OUVERT:
                return "\033[31m";
            default:
                return "\033[33m";
        }
    }

    private String cellule(Segment s) {
        if (s.estLibre()) {
            return "[    ]";
        }
        return String.format("[%-4s]", s.getOccupant());
    }
}
