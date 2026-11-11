package voyage;

/** Véhicule prioritaire utilisant la voie dégagée par la bande d’urgence. */
public class Prioritaire extends Vehicule {
    /** Construit un acteur avec ses paramètres. */
    public Prioritaire(String nom, int sens, Pont pont) {
        super(nom, sens, 1, pont);
    }

    /** Active le protocole de priorité à la place des réservations routières ordinaires. */
    @Override
    public boolean estPrioritaire() {
        return true;
    }
}
