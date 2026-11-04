package voyage;

public class Bateau implements Runnable {
    private final String nom;
    private final String direction;
    private final Pont pont;

    public Bateau(String nom, String direction, Pont pont) {
        this.nom = nom;
        this.direction = direction;
        this.pont = pont;
    }

    @Override
    public void run() {
        try {
            pont.passerBateau(this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return nom + " (" + direction + ")";
    }
}
