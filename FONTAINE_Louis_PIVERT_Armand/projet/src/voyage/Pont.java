package voyage;

import java.time.LocalTime;
import java.util.*;

/** Moniteur du pont : toutes les ressources partagées sont protégées par this.
 * Les attentes utilisent une boucle while ; les temporisations ne gardent jamais le verrou.
 */
public final class Pont implements AutoCloseable {
    private final int n;
    private final Segment[][] voies;
    private final Set<Vehicule> presents = new HashSet<>();
    private final LinkedList<Vehicule> convois = new LinkedList<>();
    private final LinkedList<Vehicule> priorites = new LinkedList<>();
    private final Vehicule[] urgences = new Vehicule[2];
    private final LinkedList<Bateau> bateaux = new LinkedList<>();
    private final List<Evenement> historique = new ArrayList<>();
    private final Timer timer = new Timer("Reveil-ouverture", true);
    private final long pas, manoeuvre, passageBateau, delai;
    private final boolean anime;
    private EtatPont etat = EtatPont.FERME;
    private boolean demande, arrete;
    private long prochaineOuverture;
    private Bateau bateauEnCours;
    private Thread controleur;

    /** Configuration du sujet : une minute entre fermeture complète et ouverture suivante. */
    public Pont(int n) { this(n, 700, 2000, 2000, 60_000, true); }

    /** Durées injectables pour démonstrations et tests ; delai est exprimé en ms. */
    public Pont(int n, long pas, long manoeuvre, long bateau, long delai, boolean anime) {
        if (n < 4 || pas < 1 || manoeuvre < 1 || bateau < 1 || delai < 1)
            throw new IllegalArgumentException("N >= 4 et durees positives requis");
        this.n=n; this.pas=pas; this.manoeuvre=manoeuvre;
        this.passageBateau=bateau; this.delai=delai; this.anime=anime;
        voies=new Segment[2][n];
        for (int s=0;s<2;s++) for(int i=0;i<n;i++) voies[s][i]=new Segment();
    }

    /** Copie immuable d'un événement : temps monotone pour vérifier les délais. */
    public static final class Evenement {
        public final String type, nom;
        public final long nanos;
        Evenement(String type, String nom) {
            this.type=type; this.nom=nom; this.nanos=System.nanoTime();
        }
        @Override public String toString() { return type+" "+nom; }
    }

    /** Une copie empêche les lecteurs de modifier le journal du moniteur. */
    public synchronized List<Evenement> evenements() { return new ArrayList<>(historique); }

    /** Réserve atomiquement la longueur complète, avance d'un segment, libère même sur interruption. */
    public void traverser(Vehicule v) throws InterruptedException {
        synchronized(this) {
            verifierActif();
            if(v.getTaille()>n) throw new IllegalArgumentException("Vehicule trop long");
            if(v instanceof Convoi) convois.add(v);
            if(v.estPrioritaire()) priorites.add(v);
            evenement("ARRIVEE",v.toString());
            notifyAll();
        }
        try {
            if(v.estPrioritaire()) { traverserPrioritaire(v); return; }
            synchronized(this) {
                while(!peutEntrer(v)) { verifierActif(); wait(); }
                verifierActif();
                for(int i=0;i<v.getTaille();i++) voies[v.getSens()][i].occuper(v);
                presents.add(v);
                evenement("ENTREE",v.toString());
                notifyAll();
            }
            int queue=0, tete=v.getTaille()-1;
            while(queue<n) {
                Thread.sleep(pas);
                synchronized(this) {
                    boolean signale=false;
                    while(urgences[v.getSens()]!=null ||
                          (tete+1<n && !voies[v.getSens()][tete+1].estLibre())) {
                        verifierActif();
                        if(urgences[v.getSens()]!=null && !signale) {
                            evenement("ARRET_URGENCE",v.toString()); signale=true;
                        }
                        wait();
                    }
                    verifierActif();
                    if(signale) evenement("REPRISE",v.toString());
                    if(tete+1<n) voies[v.getSens()][tete+1].occuper(v);
                    voies[v.getSens()][queue].liberer();
                    queue++;tete++;
                    evenement("AVANCE",v.toString());
                    notifyAll();
                }
            }
        } finally {
            synchronized(this) {
                // L'identité évite de libérer une réservation appartenant à un autre thread.
                for(Segment[] voie:voies) for(Segment s:voie)
                    if(s.getOccupant()==v) s.liberer();
                boolean engage=presents.remove(v);
                convois.remove(v); priorites.remove(v);
                for(int s=0;s<2;s++) if(urgences[s]==v) urgences[s]=null;
                evenement(engage?"SORTIE":"ANNULATION",v.toString());
                notifyAll();
            }
        }
    }

    /** Les convois opposés sont départagés par leur ordre d'arrivée, évitant l'interblocage. */
    private boolean peutEntrer(Vehicule v) {
        int sens=v.getSens();
        if(etat!=EtatPont.FERME || demande || urgences[sens]!=null) return false;
        for(Vehicule p:priorites) if(p.getSens()==sens) return false;
        if(v instanceof Convoi) {
            if(!voieVide(1-sens) || urgences[1-sens]!=null) return false;
            for(Vehicule c:convois) {
                if(c==v) break;
                if(c.getSens()!=sens) return false;
            }
        } else {
            for(Vehicule c:convois) if(c.getSens()!=sens) return false;
        }
        for(int i=0;i<v.getTaille();i++) if(!voies[sens][i].estLibre()) return false;
        return true;
    }

    /** Un prioritaire par sens ; les réservations de reprise des autres restent inchangées. */
    private void traverserPrioritaire(Vehicule v) throws InterruptedException {
        synchronized(this) {
            while(!peutPasserPrioritaire(v)) { verifierActif(); wait(); }
            verifierActif();
            urgences[v.getSens()]=v; priorites.remove(v); presents.add(v);
            evenement("PRIORITAIRE",v.toString()); notifyAll();
        }
        Thread.sleep(Math.max(1,pas*n/2));
    }

    private boolean peutPasserPrioritaire(Vehicule v) {
        if(etat!=EtatPont.FERME || demande || urgences[v.getSens()]!=null) return false;
        // La priorité ne permet pas de croiser un convoi déjà engagé, trop large.
        for(Vehicule c:presents) if(c instanceof Convoi && c.getSens()!=v.getSens()) return false;
        for(Vehicule p:priorites) {
            if(p==v) break;
            if(p.getSens()==v.getSens()) return false;
        }
        return true;
    }

    private boolean voieVide(int sens) {
        for(Segment s:voies[sens]) if(!s.estLibre()) return false;
        return true;
    }

    /** File commune aux deux sens ; sa tête seule peut accéder à la rivière. */
    public void passerBateau(Bateau b) throws InterruptedException {
        synchronized(this) {
            verifierActif(); bateaux.add(b); evenement("BATEAU_ATTENTE",b.toString());
            tenterOuverture(); notifyAll();
        }
        try {
            synchronized(this) {
                while(etat!=EtatPont.OUVERT || bateaux.peek()!=b || bateauEnCours!=null) {
                    verifierActif(); wait();
                }
                verifierActif(); bateauEnCours=b; evenement("BATEAU_PASSAGE",b.toString());
            }
            Thread.sleep(passageBateau);
        } finally {
            synchronized(this) {
                boolean passe=bateauEnCours==b;
                if(passe) bateauEnCours=null;
                bateaux.remove(b);
                evenement(passe?"BATEAU_SORTIE":"BATEAU_ANNULATION",b.toString()); notifyAll();
            }
        }
    }

    /** Appel sous verrou, y compris depuis le Timer : une seule ouverture peut être engagée. */
    private synchronized void tenterOuverture() {
        if(!arrete && !bateaux.isEmpty() && etat==EtatPont.FERME && !demande &&
           System.nanoTime()>=prochaineOuverture) {
            demande=true; evenement("DEMANDE_OUVERTURE","");
            controleur=new Thread(this::cycle,"Controleur-pont"); controleur.start(); notifyAll();
        }
    }

    /** Attend l'évacuation, manœuvre, sert la FIFO, puis arme le réveil après fermeture complète. */
    private void cycle() {
        try {
            synchronized(this) {
                while(!presents.isEmpty()) { verifierActif(); wait(); }
                verifierActif(); changerEtat(EtatPont.OUVERTURE);
            }
            Thread.sleep(manoeuvre);
            synchronized(this) {
                verifierActif(); changerEtat(EtatPont.OUVERT);
                while(!bateaux.isEmpty()) { verifierActif(); wait(); }
                verifierActif(); changerEtat(EtatPont.FERMETURE);
            }
            Thread.sleep(manoeuvre);
            synchronized(this) {
                verifierActif();
                prochaineOuverture=System.nanoTime()+delai*1_000_000L;
                demande=false; changerEtat(EtatPont.FERME);
                timer.schedule(new TimerTask() {
                    @Override public void run() { reveiller(); }
                },delai);
                notifyAll();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Réarme si le Timer se réveille légèrement avant l'échéance monotone. */
    private synchronized void reveiller() {
        if(arrete) return;
        long restant=prochaineOuverture-System.nanoTime();
        if(restant>0) timer.schedule(new TimerTask() {
            @Override public void run() { reveiller(); }
        }, Math.max(1,(restant+999_999)/1_000_000));
        else tenterOuverture();
    }

    private void changerEtat(EtatPont e) { etat=e; evenement("ETAT",e.name()); notifyAll(); }
    private void verifierActif() throws InterruptedException {
        if(arrete || Thread.currentThread().isInterrupted()) throw new InterruptedException("Simulation arretee");
    }

    /** Invariants exécutés à chaque événement, même sans l'option Java -ea. */
    private void verifierInvariants() {
        if(etat!=EtatPont.FERME && !presents.isEmpty()) throw new IllegalStateException("Route ouverte pendant manoeuvre");
        if(bateauEnCours!=null && (etat!=EtatPont.OUVERT || bateaux.peek()!=bateauEnCours))
            throw new IllegalStateException("Conflit bateau/FIFO");
        for(Vehicule v:presents) if(v instanceof Convoi) {
            if(!voieVide(1-v.getSens()) || urgences[1-v.getSens()]!=null)
                throw new IllegalStateException("Convoi croise");
        }
        for(Segment[] voie:voies) for(Segment s:voie)
            if(!s.estLibre() && !presents.contains(s.getOccupant())) throw new IllegalStateException("Reservation orpheline");
        for(Vehicule v:presents) if(!v.estPrioritaire()) {
            int compte=0, premier=-1, dernier=-1;
            for(int i=0;i<n;i++) if(voies[v.getSens()][i].getOccupant()==v) {
                if(premier<0) premier=i;
                dernier=i; compte++;
            }
            if(compte>v.getTaille() || (compte>0 && dernier-premier+1!=compte))
                throw new IllegalStateException("Longueur ou continuite des reservations invalide");
        }
    }

    private void evenement(String type,String nom) {
        verifierInvariants(); historique.add(new Evenement(type,nom));
        if(anime) afficher();
    }

    /** Animation console ANSI ; les deux voies sont dessinées en coordonnées physiques. */
    private void afficher() {
        StringBuilder s=new StringBuilder("\033[H\033[2J\n  PONT MOBILE - "+LocalTime.now().withNano(0)+"\n\n");
        s.append("  Etat : ").append(etat).append(demande?" | entrees bloquees":"").append('\n');
        s.append("  Ouverture autorisee dans : ").append(Math.max(0,(prochaineOuverture-System.nanoTime()+999_999_999L)/1_000_000_000L)).append(" s\n\n");
        for(int sens=0;sens<2;sens++) {
            s.append(sens==0?"  G -> D  ":"  G <- D  ");
            for(int i=0;i<n;i++) {
                Segment segment=voies[sens][sens==0?i:n-1-i];
                s.append(String.format("[%-5s]",segment.estLibre()?"":segment.getOccupant()));
            }
            s.append(" urgence : ").append(urgences[sens]==null?"libre":urgences[sens]).append('\n');
        }
        s.append("\n  Riviere ~~~ ").append(bateauEnCours==null?"libre":bateauEnCours).append(" ~~~\n");
        s.append("  FIFO bateaux : ").append(bateaux).append('\n');
        s.append("  V voiture | C camion | X convoi | P prioritaire\n\n");
        for(int i=Math.max(0,historique.size()-10);i<historique.size();i++) s.append("  ").append(historique.get(i)).append('\n');
        System.out.print(s); System.out.flush();
    }

    /** Attend aussi la fin de la fermeture mécanique, avec une limite pour détecter les blocages. */
    public synchronized boolean attendreRepos(long timeoutMs) throws InterruptedException {
        long fin=System.nanoTime()+timeoutMs*1_000_000L;
        while(!presents.isEmpty() || !bateaux.isEmpty() || demande || etat!=EtatPont.FERME) {
            long reste=fin-System.nanoTime(); if(reste<=0) return false;
            wait(Math.max(1,(reste+999_999)/1_000_000));
        }
        return true;
    }

    /** Arrête le Timer et réveille les clients ; leurs finally libèrent leurs ressources. */
    @Override public synchronized void close() {
        arrete=true; timer.cancel(); if(controleur!=null) controleur.interrupt(); notifyAll();
    }
}
