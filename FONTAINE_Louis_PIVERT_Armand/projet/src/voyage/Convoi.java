package voyage;

/** Convoi de quatre segments interdisant le croisement. */
public class Convoi extends Vehicule {
    /** Construit un acteur avec ses paramètres. */
    public Convoi(String nom, int sens, Pont pont) {
        super(nom, sens, 4, pont);
    }
}
