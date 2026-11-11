package voyage;

/** Voiture occupant un segment. */
public class Voiture extends Vehicule {
    /** Construit un acteur avec ses paramètres. */
    public Voiture(String nom, int sens, Pont pont) {
        super(nom, sens, 1, pont);
    }
}
