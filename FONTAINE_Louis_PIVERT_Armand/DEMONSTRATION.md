# Trame orale (moins de 2 minutes)

0–15 s : présenter le pont à six segments, ses deux voies routières et les deux directions de bateaux. Chaque acteur est un thread ; Pont coordonne les accès par un moniteur Java.

15–35 s : montrer les voitures, camions et convois : réservations de 1, 2 et 4 segments. L’entrée opposée est bloquée pour un convoi et les véhicules déjà engagés évacuent.

35–55 s : montrer une demande de bateau : nouvelles entrées interdites, évacuation complète puis ouverture. Les bateaux passent un à un dans la file commune.

55–75 s : expliquer les prioritaires : la progression s’arrête seulement dans leur sens et les segments de reprise restent réservés. La sécurité des manœuvres reste prioritaire.

75–95 s : expliquer le Timer après la fermeture complète. Le mode normal impose 60 secondes ; la vidéo utilise 1,5 seconde pour démontrer plusieurs cycles rapidement.

95–110 s : présenter les tests et le nettoyage sur interruption. Exécuter tester.ps1 ou tester.sh pour les reproduire.

La vidéo fournie montre une exécution accélérée réelle, ralentie à la lecture pour être lisible. Elle peut servir de support visuel ; adapter les explications aux événements affichés.
