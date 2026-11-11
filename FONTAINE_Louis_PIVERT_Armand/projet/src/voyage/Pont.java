package voyage;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Moniteur commun à tous les acteurs (cours p. 196-200).
 * Toutes les données variables sont protégées par le verrou de cet objet.
 * Les méthodes privées non synchronisées sont appelées sous ce même verrou.
 */
public final class Pont implements AutoCloseable {
    private final int nombreSegments;
    private final Segment[][] voies;
    private final Set<Vehicule> presents = new HashSet<Vehicule>();
    private final LinkedList<Vehicule> convois = new LinkedList<Vehicule>();
    private final LinkedList<Vehicule> priorites = new LinkedList<Vehicule>();
    private final Vehicule[] urgences = new Vehicule[2];
    private final LinkedList<Bateau> bateaux = new LinkedList<Bateau>();
    private final List<Evenement> journal = new ArrayList<Evenement>();
    private final Timer timer = new Timer("Timer-du-pont", true);
    private final long pasMs;
    private final long manoeuvreMs;
    private final long passageBateauMs;
    private final long delaiOuvertureMs;
    private final boolean animation;
    private EtatPont etat = EtatPont.FERME;
    private boolean ouvertureDemandee;
    private boolean arrete;
    private long prochaineOuvertureNanos;
    private Bateau bateauEnCours;
    private Thread controleur;

    /** Durées normales : déplacement 700 ms, manœuvres 2 s, délai réglementaire 60 s. */
    public Pont(int nombreSegments) {
        this(nombreSegments, 700, 2_000, 2_000, 60_000, true);
    }

    /** Durées injectables uniquement pour les tests et la démonstration accélérée. */
    public Pont(int nombreSegments, long pasMs, long manoeuvreMs,
                long passageBateauMs, long delaiOuvertureMs, boolean animation) {
        if (nombreSegments < 4 || pasMs <= 0 || manoeuvreMs <= 0
                || passageBateauMs <= 0 || delaiOuvertureMs <= 0) {
            throw new IllegalArgumentException("N >= 4 et durees positives requis");
        }
        this.nombreSegments = nombreSegments;
        this.pasMs = pasMs;
        this.manoeuvreMs = manoeuvreMs;
        this.passageBateauMs = passageBateauMs;
        this.delaiOuvertureMs = delaiOuvertureMs;
        this.animation = animation;
        voies = new Segment[2][nombreSegments];
        for (int sens = 0; sens < 2; sens++) {
            for (int i = 0; i < nombreSegments; i++) {
                voies[sens][i] = new Segment();
            }
        }
    }

    /** Trace immuable, lue par la console et les tests. */
    public static final class Evenement {
        public final String type;
        public final String nom;
        public final long nanos;

        /** L'horloge monotone permet de mesurer un intervalle sans dépendre de l'heure civile. */
        private Evenement(String type, String nom) {
            this.type = type;
            this.nom = nom;
            this.nanos = System.nanoTime();
        }

        /** Texte de la ligne de journal. */
        @Override
        public String toString() {
            return type + " " + nom;
        }
    }

    /** Lecture cohérente : le lecteur ne peut pas modifier le journal partagé. */
    public synchronized List<Evenement> evenements() {
        return new ArrayList<Evenement>(journal);
    }

    /** Déroule une traversée ; tous les sleep sont hors des sections critiques. */
    public void traverser(Vehicule vehicule) throws InterruptedException {
        inscrireVehicule(vehicule);
        try {
            if (vehicule.estPrioritaire()) {
                entrerPrioritaire(vehicule);
                Thread.sleep(Math.max(1, pasMs * nombreSegments / 2));
            } else {
                entrerVehicule(vehicule);
                for (int queue = 0; queue < nombreSegments; queue++) {
                    Thread.sleep(pasMs);
                    avancerVehicule(vehicule, queue);
                }
            }
        } finally {
            retirerVehicule(vehicule);
        }
    }

    /** L'arrivée d'un convoi bloque les entrées opposées avant toute mise en attente. */
    private synchronized void inscrireVehicule(Vehicule vehicule) throws InterruptedException {
        verifierActif();
        if (vehicule.getTaille() > nombreSegments) {
            throw new IllegalArgumentException("Vehicule plus long que le pont");
        }
        if (vehicule instanceof Convoi) {
            convois.addLast(vehicule);
        }
        if (vehicule.estPrioritaire()) {
            priorites.addLast(vehicule);
        }
        enregistrer("ARRIVEE", vehicule.toString());
        notifyAll();
    }

    /** Attend sans attente active puis réserve tous les segments nécessaires atomiquement. */
    private synchronized void entrerVehicule(Vehicule vehicule) throws InterruptedException {
        // Cours p. 199-200 : après le réveil, retester la condition en récupérant le verrou.
        while (!peutEntrer(vehicule)) {
            verifierActif();
            wait();
        }
        verifierActif();
        for (int i = 0; i < vehicule.getTaille(); i++) {
            voies[vehicule.getSens()][i].occuper(vehicule);
        }
        presents.add(vehicule);
        enregistrer("ENTREE", vehicule.toString());
        notifyAll();
    }

    /** Prédicat d'admission, évalué sous le verrou du pont. */
    private boolean peutEntrer(Vehicule vehicule) {
        int sens = vehicule.getSens();
        if (etat != EtatPont.FERME || ouvertureDemandee || urgences[sens] != null) {
            return false;
        }
        for (Vehicule prioritaire : priorites) {
            if (prioritaire.getSens() == sens) {
                return false;
            }
        }
        if (vehicule instanceof Convoi) {
            if (!voieVide(1 - sens) || urgences[1 - sens] != null) {
                return false;
            }
            // La libération du dernier segment et l'annonce de sortie sont deux opérations.
            // Attendre aussi le retrait de l'acteur évite d'admettre le convoi entre ces opérations.
            for (Vehicule present : presents) {
                if (present.getSens() != sens) {
                    return false;
                }
            }
            // Le convoi opposé le plus ancien passe d'abord : pas d'attente circulaire.
            for (Vehicule convoi : convois) {
                if (convoi == vehicule) {
                    break;
                }
                if (convoi.getSens() != sens) {
                    return false;
                }
            }
        } else {
            for (Vehicule convoi : convois) {
                if (convoi.getSens() != sens) {
                    return false;
                }
            }
        }
        for (int i = 0; i < vehicule.getTaille(); i++) {
            if (!voies[sens][i].estLibre()) {
                return false;
            }
        }
        return true;
    }

    /** Réserve la nouvelle tête et libère la queue ; garde les segments communs aux deux positions. */
    private synchronized void avancerVehicule(Vehicule vehicule, int queue)
            throws InterruptedException {
        int sens = vehicule.getSens();
        int suivant = queue + vehicule.getTaille();
        boolean arretSignale = false;
        while (urgences[sens] != null
                || (suivant < nombreSegments && !voies[sens][suivant].estLibre())) {
            verifierActif();
            if (urgences[sens] != null && !arretSignale) {
                enregistrer("ARRET_URGENCE", vehicule.toString());
                arretSignale = true;
            }
            // Les réservations de reprise restent en place pendant l'arrêt.
            wait();
        }
        verifierActif();
        if (arretSignale) {
            enregistrer("REPRISE", vehicule.toString());
        }
        if (suivant < nombreSegments) {
            voies[sens][suivant].occuper(vehicule);
        }
        voies[sens][queue].liberer();
        enregistrer("AVANCE", vehicule.toString());
        notifyAll();
    }

    /** Admet au plus un prioritaire par sens, sans modifier les réservations de reprise. */
    private synchronized void entrerPrioritaire(Vehicule vehicule) throws InterruptedException {
        while (!peutPasserPrioritaire(vehicule)) {
            verifierActif();
            wait();
        }
        verifierActif();
        urgences[vehicule.getSens()] = vehicule;
        priorites.remove(vehicule);
        presents.add(vehicule);
        enregistrer("PRIORITAIRE", vehicule.toString());
        notifyAll();
    }

    /** Priorité métier : elle ne dépend pas de Thread.setPriority() et respecte la sécurité. */
    private boolean peutPasserPrioritaire(Vehicule vehicule) {
        int sens = vehicule.getSens();
        if (etat != EtatPont.FERME || ouvertureDemandee || urgences[sens] != null) {
            return false;
        }
        for (Vehicule present : presents) {
            if (present instanceof Convoi && present.getSens() != sens) {
                return false;
            }
        }
        for (Vehicule prioritaire : priorites) {
            if (prioritaire == vehicule) {
                break;
            }
            if (prioritaire.getSens() == sens) {
                return false;
            }
        }
        return true;
    }

    /** Nettoyage commun à la fin normale et à l'interruption coopérative. */
    private synchronized void retirerVehicule(Vehicule vehicule) {
        for (Segment[] voie : voies) {
            for (Segment segment : voie) {
                if (segment.getOccupant() == vehicule) {
                    segment.liberer();
                }
            }
        }
        boolean etaitPresent = presents.remove(vehicule);
        convois.remove(vehicule);
        priorites.remove(vehicule);
        if (urgences[vehicule.getSens()] == vehicule) {
            urgences[vehicule.getSens()] = null;
        }
        enregistrer(etaitPresent ? "SORTIE" : "ANNULATION", vehicule.toString());
        notifyAll();
    }

    /** Chaque bateau s'inscrit, attend son tour, traverse et quitte la FIFO commune. */
    public void passerBateau(Bateau bateau) throws InterruptedException {
        inscrireBateau(bateau);
        try {
            entrerBateau(bateau);
            Thread.sleep(passageBateauMs);
        } finally {
            retirerBateau(bateau);
        }
    }

    /** Enregistre l'ordre d'arrivée indépendamment du sens nord/sud. */
    private synchronized void inscrireBateau(Bateau bateau) throws InterruptedException {
        verifierActif();
        bateaux.addLast(bateau);
        enregistrer("BATEAU_ATTENTE", bateau.toString());
        tenterOuverture();
        notifyAll();
    }

    /** La tête seule peut prendre la rivière, quand l'ouverture est complète. */
    private synchronized void entrerBateau(Bateau bateau) throws InterruptedException {
        while (etat != EtatPont.OUVERT || bateaux.peekFirst() != bateau || bateauEnCours != null) {
            verifierActif();
            wait();
        }
        verifierActif();
        bateauEnCours = bateau;
        enregistrer("BATEAU_PASSAGE", bateau.toString());
    }

    /** Retire aussi un bateau interrompu pour ne pas immobiliser toute la file. */
    private synchronized void retirerBateau(Bateau bateau) {
        boolean traversait = bateauEnCours == bateau;
        if (traversait) {
            bateauEnCours = null;
        }
        bateaux.remove(bateau);
        enregistrer(traversait ? "BATEAU_SORTIE" : "BATEAU_ANNULATION", bateau.toString());
        notifyAll();
    }

    /** Bloque les entrées avant de lancer un unique thread de commande du pont. */
    private synchronized void tenterOuverture() {
        if (arrete || bateaux.isEmpty() || ouvertureDemandee || etat != EtatPont.FERME
                || System.nanoTime() < prochaineOuvertureNanos) {
            return;
        }
        ouvertureDemandee = true;
        enregistrer("DEMANDE_OUVERTURE", "");
        controleur = new Thread(new Runnable() {
            @Override
            public void run() {
                effectuerCycle();
            }
        }, "Controleur-du-pont");
        controleur.start();
        notifyAll();
    }

    /** Sépare l'attente conditionnelle sous verrou et la temporisation hors verrou. */
    private void effectuerCycle() {
        try {
            attendreEvacuation();
            Thread.sleep(manoeuvreMs);
            ouvrirEtAttendreBateaux();
            Thread.sleep(manoeuvreMs);
            terminerFermeture();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Attend le départ de tous les véhicules, prioritaires compris. */
    private synchronized void attendreEvacuation() throws InterruptedException {
        while (!presents.isEmpty()) {
            verifierActif();
            wait();
        }
        verifierActif();
        changerEtat(EtatPont.OUVERTURE);
    }

    /** Autorise les bateaux puis attend la fin du dernier passage avant de refermer. */
    private synchronized void ouvrirEtAttendreBateaux() throws InterruptedException {
        verifierActif();
        changerEtat(EtatPont.OUVERT);
        while (!bateaux.isEmpty()) {
            verifierActif();
            wait();
        }
        verifierActif();
        changerEtat(EtatPont.FERMETURE);
    }

    /** L'échéance est calculée à la fermeture complète, jamais au début de la fermeture. */
    private synchronized void terminerFermeture() throws InterruptedException {
        verifierActif();
        ouvertureDemandee = false;
        changerEtat(EtatPont.FERME);
        prochaineOuvertureNanos = System.nanoTime() + delaiOuvertureMs * 1_000_000L;
        if (animation) {
            afficher();
        }
        programmerReveil(delaiOuvertureMs);
        notifyAll();
    }

    /** Arme le Timer/TimerTask demandé par le sujet ; appelée sous verrou. */
    private void programmerReveil(long attenteMs) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                verifierEcheance();
            }
        }, attenteMs);
    }

    /** Un réveil précoce est reprogrammé, afin de ne jamais ouvrir avant l'échéance. */
    private synchronized void verifierEcheance() {
        if (arrete) {
            return;
        }
        long restantNanos = prochaineOuvertureNanos - System.nanoTime();
        if (restantNanos > 0) {
            programmerReveil(Math.max(1, (restantNanos + 999_999) / 1_000_000));
        } else {
            tenterOuverture();
        }
    }

    /** Teste si aucun segment n'est occupé dans ce sens ; appelée sous verrou. */
    private boolean voieVide(int sens) {
        for (Segment segment : voies[sens]) {
            if (!segment.estLibre()) {
                return false;
            }
        }
        return true;
    }

    /** Publie le nouvel état sous verrou puis réveille toutes les catégories d'acteurs. */
    private void changerEtat(EtatPont nouvelEtat) {
        etat = nouvelEtat;
        enregistrer("ETAT", nouvelEtat.name());
        notifyAll();
    }

    /** Propage l'arrêt en InterruptedException, prise en charge par les blocs finally des acteurs. */
    private void verifierActif() throws InterruptedException {
        if (arrete || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Simulation arretee");
        }
    }

    /** Vérifie les règles de sûreté à chaque événement, même sans l'option -ea. */
    private void verifierInvariants() {
        if (etat != EtatPont.FERME && !presents.isEmpty()) {
            throw new IllegalStateException("Vehicule pendant une ouverture ou une manoeuvre");
        }
        if (bateauEnCours != null
                && (etat != EtatPont.OUVERT || bateaux.peekFirst() != bateauEnCours)) {
            throw new IllegalStateException("Bateau hors FIFO ou pont non ouvert");
        }
        for (Vehicule vehicule : presents) {
            if (vehicule instanceof Convoi
                    && (!voieVide(1 - vehicule.getSens()) || urgences[1 - vehicule.getSens()] != null)) {
                throw new IllegalStateException("Croisement d'un convoi");
            }
            if (vehicule instanceof Convoi) {
                for (Vehicule autre : presents) {
                    if (autre.getSens() != vehicule.getSens()) {
                        throw new IllegalStateException("Convoi avant sortie complete du sens oppose");
                    }
                }
            }
        }
        for (Segment[] voie : voies) {
            for (Segment segment : voie) {
                if (!segment.estLibre() && !presents.contains(segment.getOccupant())) {
                    throw new IllegalStateException("Reservation sans vehicule present");
                }
            }
        }
        for (Vehicule vehicule : presents) {
            if (!vehicule.estPrioritaire()) {
                verifierReservation(vehicule);
            }
        }
    }

    /** Contrôle la longueur et la continuité des réservations d'un véhicule. */
    private void verifierReservation(Vehicule vehicule) {
        int compte = 0;
        int premier = -1;
        int dernier = -1;
        for (int i = 0; i < nombreSegments; i++) {
            if (voies[vehicule.getSens()][i].getOccupant() == vehicule) {
                if (premier == -1) {
                    premier = i;
                }
                dernier = i;
                compte++;
            }
        }
        if (compte > vehicule.getTaille() || (compte > 0 && dernier - premier + 1 != compte)) {
            throw new IllegalStateException("Reservation discontinue ou trop longue");
        }
    }

    /** Journalise une vue cohérente ; appelée avec le verrou du pont. */
    private void enregistrer(String type, String nom) {
        verifierInvariants();
        journal.add(new Evenement(type, nom));
        if (animation) {
            afficher();
        }
    }

    /** Dessine les voies en coordonnées physiques et les dix derniers événements. */
    private void afficher() {
        StringBuilder texte = new StringBuilder("\033[H\033[2J\n  PONT MOBILE - ");
        texte.append(LocalTime.now().withNano(0)).append("\n\n  Etat : ").append(etat);
        if (ouvertureDemandee) {
            texte.append(" | entrees bloquees");
        }
        long secondes = Math.max(0,
                (prochaineOuvertureNanos - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
        texte.append("\n  Ouverture autorisee dans : ").append(secondes).append(" s\n\n");
        for (int sens = 0; sens < 2; sens++) {
            texte.append(sens == 0 ? "  G -> D  " : "  G <- D  ");
            for (int i = 0; i < nombreSegments; i++) {
                int indice = sens == 0 ? i : nombreSegments - 1 - i;
                Segment segment = voies[sens][indice];
                texte.append(String.format("[%-5s]", segment.estLibre() ? "" : segment.getOccupant()));
            }
            texte.append(" urgence : ").append(urgences[sens] == null ? "libre" : urgences[sens]);
            texte.append('\n');
        }
        texte.append("\n  Riviere ~~~ ").append(bateauEnCours == null ? "libre" : bateauEnCours);
        texte.append(" ~~~\n  FIFO bateaux : ").append(bateaux);
        texte.append("\n  V voiture | C camion | X convoi | P prioritaire\n\n");
        for (int i = Math.max(0, journal.size() - 10); i < journal.size(); i++) {
            texte.append("  ").append(journal.get(i)).append('\n');
        }
        System.out.print(texte);
        System.out.flush();
    }

    /** Attend le repos et la fermeture complète ; le timeout sert à détecter un blocage en test. */
    public synchronized boolean attendreRepos(long timeoutMs) throws InterruptedException {
        long finNanos = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!presents.isEmpty() || !bateaux.isEmpty() || ouvertureDemandee || etat != EtatPont.FERME) {
            long restantNanos = finNanos - System.nanoTime();
            if (restantNanos <= 0) {
                return false;
            }
            wait(Math.max(1, (restantNanos + 999_999) / 1_000_000));
        }
        return true;
    }

    /** Arrête le Timer et réveille les attentes, sans utiliser Thread.stop/suspend/resume. */
    @Override
    public synchronized void close() {
        arrete = true;
        timer.cancel();
        if (controleur != null) {
            controleur.interrupt();
        }
        notifyAll();
    }
}
