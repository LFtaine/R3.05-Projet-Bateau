package voyage;

public abstract class Vehicule implements Runnable {
    public static final int GAUCHE_DROITE = 0;
    public static final int DROITE_GAUCHE = 1;

    private final String nom;
    private final int sens;
    private final int taille;
    private final Pont pont;

    public Vehicule(String nom, int sens, int taille, Pont pont) {
        this.nom = nom;
        this.sens = sens;
        this.taille = taille;
        this.pont = pont;
    }

    public int getSens() {
        return sens;
    }

    public int getTaille() {
        return taille;
    }

    public boolean estPrioritaire() {
        return false;
    }

    @Override
    public void run() {
        try {
            pont.traverser(this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return nom;
    }
}
