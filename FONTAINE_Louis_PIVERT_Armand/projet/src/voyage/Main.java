package voyage;

import java.util.Random;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Pont pont = new Pont(6);
        Random hasard = new Random();

        for (int i = 1; i <= 45; i++) {
            int sens = hasard.nextInt(2);
            int tirage = hasard.nextInt(10);
            Vehicule v;
            if (tirage < 5) {
                v = new Voiture("V" + i, sens, pont);
            } else if (tirage < 7) {
                v = new Camion("C" + i, sens, pont);
            } else if (tirage < 8) {
                v = new Convoi("X" + i, sens, pont);
            } else {
                v = new Prioritaire("P" + i, sens, pont);
            }
            new Thread(v, v.toString()).start();

            if (i % 10 == 3 || i % 10 == 5) {
                String direction = hasard.nextBoolean() ? "nord->sud" : "sud->nord";
                Bateau b = new Bateau("B" + i, direction, pont);
                new Thread(b, "Bateau-" + i).start();
            }
            Thread.sleep(1000 + hasard.nextInt(1500));
        }
    }
}
