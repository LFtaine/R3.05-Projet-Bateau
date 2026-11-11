package voyage;

/** Camion réservant deux segments consécutifs. */
public class Camion extends Vehicule {
    /** Construit un acteur avec ses paramètres. */
    public Camion(String nom, int sens, Pont pont) {
        super(nom, sens, 2, pont);
    }
}
