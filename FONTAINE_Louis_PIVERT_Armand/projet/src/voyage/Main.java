package voyage;
import java.util.*;

/** Lance une simulation finie et attend explicitement tous ses acteurs. */
public final class Main {
    private Main() { }
    /** --rapide : démonstration accélérée ; --texte : journal sans ANSI ; --seed=N : reproductibilité. */
    public static void main(String[] args) throws InterruptedException {
        boolean rapide=false, texte=false; long seed=42;
        for(String arg:args) {
            if(arg.equals("--rapide")) rapide=true;
            else if(arg.equals("--texte")) texte=true;
            else if(arg.startsWith("--seed=")) seed=Long.parseLong(arg.substring(7));
            else throw new IllegalArgumentException("Options : --rapide --texte --seed=N");
        }
        try(Pont pont=new Pont(6,rapide?35:700,rapide?100:2000,rapide?120:2000,rapide?1500:60_000,!texte)) {
            Random hasard=new Random(seed); List<Thread> acteurs=new ArrayList<>();
            for(int i=1;i<=45;i++) {
                int sens=hasard.nextInt(2), tirage=hasard.nextInt(10);
                Vehicule v=tirage<5?new Voiture("V"+i,sens,pont):tirage<7?new Camion("C"+i,sens,pont):
                    tirage<8?new Convoi("X"+i,sens,pont):new Prioritaire("P"+i,sens,pont);
                lancer(v,acteurs);
                if(i%10==3 || i%10==5) lancer(new Bateau("B"+i,hasard.nextBoolean()?"nord->sud":"sud->nord",pont),acteurs);
                Thread.sleep(rapide?45:1000+hasard.nextInt(1500));
            }
            for(Thread t:acteurs) t.join();
            if(!pont.attendreRepos(10_000)) throw new IllegalStateException("Fermeture incomplete");
            if(texte) for(Pont.Evenement e:pont.evenements()) System.out.println(e);
            System.out.println("Simulation terminee : "+acteurs.size()+" acteurs.");
        }
    }
    private static void lancer(Runnable acteur,List<Thread> acteurs) {
        Thread t=new Thread(acteur,acteur.toString()); acteurs.add(t); t.start();
    }
}
