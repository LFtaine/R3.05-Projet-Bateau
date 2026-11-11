package voyage;

/** Réservation élémentaire ; accès exclusivement sous le verrou du Pont. */
public class Segment {
    private Vehicule occupant;

    /** Teste la disponibilité ; à appeler sous le verrou du pont. */
    public boolean estLibre() {
        return occupant == null;
    }

    /** Réserve une cellule libre et refuse tout écrasement de son occupant. */
    public void occuper(Vehicule v) {
        if (v == null || occupant != null) {
            throw new IllegalStateException("Reservation impossible : segment occupe ou vehicule null");
        }
        occupant = v;
    }

    /** Libère la cellule lorsque la queue du véhicule l’a quittée. */
    public void liberer() {
        occupant = null;
    }

    /** Retourne le détenteur de la réservation, ou null si elle est libre. */
    public Vehicule getOccupant() {
        return occupant;
    }
}
