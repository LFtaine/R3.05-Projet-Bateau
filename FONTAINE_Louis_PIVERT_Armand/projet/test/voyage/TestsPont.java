package voyage;

import java.util.*;
import java.util.concurrent.*;

/** Tests autonomes : événements observables, délais bornés et propagation des erreurs des threads. */
public final class TestsPont {
    private static final List<Throwable> erreurs=Collections.synchronizedList(new ArrayList<Throwable>());
    private static int assertions;
    private TestsPont() { }
    private static void verifier(boolean ok,String message) {
        assertions++; if(!ok) throw new AssertionError(message);
    }
    private static Thread lancer(Runnable r) {
        Thread t=new Thread(r);
        t.setDaemon(true); // Un échec de délai doit terminer le test, même si une régression bloque un acteur.
        t.setUncaughtExceptionHandler((thread,e)->erreurs.add(e)); t.start(); return t;
    }
    private static void attendre(Pont p,String type,String nom) throws Exception {
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        do {
            for(Pont.Evenement e:p.evenements()) if(e.type.equals(type)&&e.nom.equals(nom)) return;
            Thread.sleep(1);
        } while(System.nanoTime()<fin);
        throw new AssertionError("Evenement absent : "+type+" "+nom);
    }
    private static void finir(Pont p,Thread... ts) throws Exception {
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        for(Thread t:ts) {
            t.join(Math.max(1,TimeUnit.NANOSECONDS.toMillis(fin-System.nanoTime())));
            verifier(!t.isAlive(),"Thread bloque");
        }
        verifier(p.attendreRepos(3000),"Pont non vide ou fermeture incomplete");
        verifier(erreurs.isEmpty(),"Exception d'acteur : "+erreurs);
    }
    private static int index(Pont p,String type,String nom) {
        List<Pont.Evenement> es=p.evenements();
        for(int i=0;i<es.size();i++) if(es.get(i).type.equals(type)&&es.get(i).nom.equals(nom)) return i;
        return -1;
    }

    private static void convois() throws Exception {
        try(Pont p=new Pont(6,20,10,10,80,false)) {
            Thread v=lancer(new Voiture("V",1,p)); attendre(p,"ENTREE","V");
            Thread x=lancer(new Convoi("X",0,p)); attendre(p,"ARRIVEE","X");
            Thread y=lancer(new Convoi("Y",1,p)); attendre(p,"ARRIVEE","Y");
            Thread w=lancer(new Voiture("W",1,p));
            finir(p,v,x,y,w);
            verifier(index(p,"SORTIE","V")<index(p,"ENTREE","X"),"Convoi avant evacuation opposee");
            verifier(index(p,"SORTIE","X")<index(p,"ENTREE","Y"),"Ordre convois oppose");
            verifier(index(p,"SORTIE","X")<index(p,"ENTREE","W"),"Entree opposee non bloquee");
        }
    }

    private static void fifoEtDelai() throws Exception {
        try(Pont p=new Pont(6,10,25,20,150,false)) {
            Thread v=lancer(new Camion("C",0,p)); attendre(p,"ENTREE","C");
            Thread b1=lancer(new Bateau("B1","NS",p)); attendre(p,"BATEAU_ATTENTE","B1 (NS)");
            attendre(p,"DEMANDE_OUVERTURE","");
            Thread prioritaire=lancer(new Prioritaire("P",0,p));
            Thread b2=lancer(new Bateau("B2","SN",p)); attendre(p,"BATEAU_ATTENTE","B2 (SN)");
            finir(p,v,b1,b2,prioritaire);
            verifier(index(p,"SORTIE","C")<index(p,"ETAT","OUVERTURE"),"Ouverture sur camion");
            verifier(index(p,"BATEAU_SORTIE","B1 (NS)")<index(p,"BATEAU_PASSAGE","B2 (SN)"),"FIFO violee");
            verifier(index(p,"ETAT","FERME")<index(p,"PRIORITAIRE","P"),"Prioritaire pendant demande/ouverture");
            Thread b3=lancer(new Bateau("B3","NS",p)); finir(p,b3);
            long fermeture=0,ouverture=0;
            for(Pont.Evenement e:p.evenements()) {
                if(e.type.equals("ETAT")&&e.nom.equals("FERME")&&fermeture==0) fermeture=e.nanos;
                else if(e.type.equals("DEMANDE_OUVERTURE")&&fermeture!=0) { ouverture=e.nanos;break; }
            }
            verifier(ouverture-fermeture>=TimeUnit.MILLISECONDS.toNanos(150),"Delai de reouverture trop court");
        }
    }

    private static void urgence() throws Exception {
        try(Pont p=new Pont(6,45,10,10,80,false)) {
            Thread c=lancer(new Camion("C",0,p)); attendre(p,"ENTREE","C");
            Thread opposite=lancer(new Voiture("O",1,p)); attendre(p,"ENTREE","O");
            Thread u=lancer(new Prioritaire("P",0,p)); attendre(p,"PRIORITAIRE","P");
            Thread u2=lancer(new Prioritaire("Q",0,p));
            finir(p,c,opposite,u,u2);
            verifier(index(p,"ARRET_URGENCE","C")>=0,"Pas d'arret urgence");
            verifier(index(p,"REPRISE","C")>index(p,"SORTIE","P"),"Reprise avant sortie du prioritaire");
            verifier(index(p,"PRIORITAIRE","Q")>index(p,"SORTIE","P"),"Deux prioritaires dans le meme sens");
            int a=index(p,"PRIORITAIRE","P"),z=index(p,"SORTIE","P"); boolean avance=false;
            List<Pont.Evenement> es=p.evenements();
            for(int i=a+1;i<z;i++) if(es.get(i).type.equals("AVANCE")&&es.get(i).nom.equals("O")) avance=true;
            verifier(avance,"Le sens oppose ne progresse pas pendant la priorite");
        }
    }

    private static void interruptions() throws Exception {
        try(Pont p=new Pont(6,30,20,60,80,false)) {
            Thread x=lancer(new Convoi("X",0,p)); attendre(p,"ENTREE","X"); x.interrupt(); finir(p,x);
            Thread c=lancer(new Camion("C",1,p)); attendre(p,"ENTREE","C");
            Thread y=lancer(new Convoi("Y",0,p)); attendre(p,"ARRIVEE","Y");y.interrupt();
            Thread b=lancer(new Bateau("B","NS",p)); attendre(p,"BATEAU_ATTENTE","B (NS)");b.interrupt();
            finir(p,c,y,b);
            Thread q=lancer(new Prioritaire("Q",0,p)); attendre(p,"PRIORITAIRE","Q");q.interrupt();finir(p,q);
            Thread b2=lancer(new Bateau("B2","SN",p)); attendre(p,"BATEAU_PASSAGE","B2 (SN)");b2.interrupt();finir(p,b2);
            Thread v=lancer(new Voiture("V",1,p));finir(p,v);
        }
    }

    private static void charge() throws Exception {
        for(int seed=0;seed<20;seed++) {
            try(Pont p=new Pont(6,1,2,2,8,false)) {
                Random r=new Random(seed); List<Thread> ts=new ArrayList<>();
                for(int i=0;i<100;i++) {
                    int s=r.nextInt(2),type=r.nextInt(5);String nom="A"+i;
                    Runnable acteur=type==0?new Voiture(nom,s,p):type==1?new Camion(nom,s,p):
                        type==2?new Convoi(nom,s,p):type==3?new Prioritaire(nom,s,p):new Bateau(nom,s==0?"NS":"SN",p);
                    ts.add(lancer(acteur));
                }
                finir(p,ts.toArray(new Thread[0]));
                long routiers=p.evenements().stream().filter(e->e.type.equals("ENTREE")||e.type.equals("PRIORITAIRE")).count();
                long sorties=p.evenements().stream().filter(e->e.type.equals("SORTIE")).count();
                verifier(routiers==sorties,"Acteur routier perdu");
            }
        }
    }

    /** Chaque groupe possède une borne temporelle et vérifie les invariants internes du pont. */
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--minute")) { minuteReelle(); return; }
        convois(); System.out.println("OK convois opposes et blocage entrees");
        fifoEtDelai(); System.out.println("OK FIFO, evacuation, priorite et Timer");
        urgence(); System.out.println("OK arret/reprise, priorites et sens oppose");
        interruptions(); System.out.println("OK nettoyage sur interruption");
        charge(); System.out.println("OK 20 graines x 100 acteurs concurrents");
        System.out.println("SUCCES : "+assertions+" verifications");
    }

    /** Vérification optionnelle du constructeur de production, avec une vraie minute d'attente. */
    private static void minuteReelle() throws Exception {
        try(Pont p=new Pont(6)) {
            Thread b1=lancer(new Bateau("Minute1","NS",p)); finir(p,b1);
            Thread b2=lancer(new Bateau("Minute2","SN",p));
            b2.join(70_000);
            verifier(!b2.isAlive(),"Bateau non servi apres une minute");
            verifier(p.attendreRepos(4000),"Fermeture incomplete");
            long fermeture=0,ouverture=0;
            for(Pont.Evenement e:p.evenements()) {
                if(e.type.equals("ETAT")&&e.nom.equals("FERME")&&fermeture==0) fermeture=e.nanos;
                else if(e.type.equals("DEMANDE_OUVERTURE")&&fermeture!=0) { ouverture=e.nanos; break; }
            }
            long intervalle=ouverture-fermeture;
            verifier(intervalle>=TimeUnit.SECONDS.toNanos(60),"La minute reelle n'est pas respectee");
            verifier(erreurs.isEmpty(),"Erreur d'acteur : "+erreurs);
            System.out.println("OK delai reel du constructeur normal : "+TimeUnit.NANOSECONDS.toMillis(intervalle)+" ms");
        }
    }
}
