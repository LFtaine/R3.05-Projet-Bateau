package voyage;

/** Acteur fluvial indépendant ; la direction ne modifie pas son rang FIFO. */
public class Bateau implements Runnable {
    private final String nom;
    private final String direction;
    private final Pont pont;

    /** Construit un acteur avec ses paramètres. */
    public Bateau(String nom, String direction, Pont pont) {
        if (nom == null || nom.isEmpty() || direction == null || pont == null) {
            throw new IllegalArgumentException("Parametres bateau invalides");
        }
        this.nom = nom;
        this.direction = direction;
        this.pont = pont;
    }

    /** Enregistre puis effectue le passage FIFO ; préserve le statut d’interruption. */
    @Override
    public void run() {
        try {
            pont.passerBateau(this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Identifie le bateau et son sens dans la file et le journal. */
    @Override
    public String toString() {
        return nom + " (" + direction + ")";
    }
}
