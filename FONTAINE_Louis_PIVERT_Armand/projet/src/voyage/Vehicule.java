package voyage;

/** Acteur routier abstrait : sens, longueur et moniteur partagés immuables. */
public abstract class Vehicule implements Runnable {
    public static final int GAUCHE_DROITE = 0;
    public static final int DROITE_GAUCHE = 1;

    private final String nom;
    private final int sens;
    private final int taille;
    private final Pont pont;

    /** Construit un acteur avec ses paramètres. */
    public Vehicule(String nom, int sens, int taille, Pont pont) {
        if (nom == null || nom.isEmpty() || pont == null || sens < 0 || sens > 1 || taille < 1) {
            throw new IllegalArgumentException("Parametres vehicule invalides");
        }
        this.nom = nom;
        this.sens = sens;
        this.taille = taille;
        this.pont = pont;
    }

    /** Retourne 0 pour gauche vers droite, 1 pour le sens inverse. */
    public int getSens() {
        return sens;
    }

    /** Retourne la longueur logique du véhicule en segments. */
    public int getTaille() {
        return taille;
    }

    /** Les véhicules ordinaires ne sont pas prioritaires ; surcharge dans Prioritaire. */
    public boolean estPrioritaire() {
        return false;
    }

    /** Exécute la traversée dans son thread et préserve le statut d’interruption. */
    @Override
    public void run() {
        try {
            pont.traverser(this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Identifiant court utilisé dans les cellules et le journal. */
    @Override
    public String toString() {
        return nom;
    }
}
