package voyage;

public class Prioritaire extends Vehicule {
    public Prioritaire(String nom, int sens, Pont pont) {
        super(nom, sens, 1, pont);
    }

    @Override
    public boolean estPrioritaire() {
        return true;
    }
}
