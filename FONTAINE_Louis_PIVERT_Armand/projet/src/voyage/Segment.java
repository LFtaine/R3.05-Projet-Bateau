package voyage;

public class Segment {
    private Vehicule occupant;

    public boolean estLibre() {
        return occupant == null;
    }

    public void occuper(Vehicule v) {
        occupant = v;
    }

    public void liberer() {
        occupant = null;
    }

    public Vehicule getOccupant() {
        return occupant;
    }
}
