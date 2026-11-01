# TRW Portfolio Signal Watcher & Alarm (Android)

Αυτόνομη εφαρμογή Android σε Kotlin που παρακολουθεί αυτόματα κάθε βράδυ σε **δύο ρυθμιζόμενα exact alarms**:
1. **Check 1 Time** (Προεπιλογή: 02:13 π.μ. - Κύριος έλεγχος)
2. **Check 2 Time (Backup)** (Προεπιλογή: 03:30 π.μ. - Εφεδρικός έλεγχος)

το κανάλι Portfolio Signals στο **The Real World** και χτυπάει δυνατό ξυπνητήρι στη μέγιστη ένταση **ΜΟΝΟ** αν το νέο σημερινό signal διαφέρει από το προηγούμενο αποθηκευμένο (`saved_signal`).

---

## 🎯 Τεχνικά Χαρακτηριστικά & Αναβαθμίσεις

1. **Ανίχνευση Νέου Post μέσω Πτώσης Reactions (Μαθηματικό Μοντέλο)**:
   - Δεν εξαρτάται από λέξεις όπως "Today" ή αυθαίρετα σταθερά όρια.
   - Το `SignalExtractor.kt` εξάγει τα 2 τελευταία μηνύματα (`latest` & `previous`), το καθαρό κείμενο του σήματος και τον **πρώτο αριθμό reactions** (`firstReaction`) στο κάτω μέρος κάθε μηνύματος.
   - **Κριτήριο νέου post (`isNewPost`)**:
     * `(latestReaction < saved_reaction)` Ή `(prevReaction > 0 && latestReaction < prevReaction * 0.65 && prevSignal == saved_signal)`.
     * Όταν βγαίνει νέο post, τα reactions πέφτουν απότομα (π.χ. από 671 σε 30), ενώ το παλιό μήνυμα μετακινείται στη θέση `prevSignal`.
   - **Αν `isNewPost == false`**: Δεν χτυπάει ξυπνητήρι. Αν τα reactions απλώς αυξήθηκαν στο ίδιο post, ενημερώνει σιωπηλά το `saved_reaction = latestReaction`, καθαρίζει την cache και ξαναδοκιμάζει κάθε 45s (έως 8 προσπάθειες).
   - **Αν `isNewPost == true`**:
     * Αν `latestSignal == saved_signal`: Το σήμα παρέμεινε ίδιο (π.χ. 100% CASH). Κλείνει αθόρυβα την οθόνη χωρίς ξυπνητήρι!
     * Αν `latestSignal != saved_signal`: **ΧΤΥΠΑ ΤΟ ΞΥΠΝΗΤΗΡΙ** στη μέγιστη ένταση (100% volume + 100% φωτεινότητα + δόνηση + τεράστιο κουμπί STOP ALARM).

2. **Αποθήκευση Αναφοράς & Εποπτεία στην Αρχική Οθόνη**:
   - Πατώντας "Read & Save Current Signal", αποθηκεύονται τα `saved_signal` και `saved_reaction`.
   - Εμφανίζεται καθαρά: `Last Reaction Count: 671 (Prev msg: 936)` για άμεση επιβεβαίωση.
   - Δύο ανεξάρτητοι επιλογείς ώρας (TimePickers σε 24ωρη μορφή) για Check 1 και Check 2 με ξεχωριστούς διακόπτες ενεργοποίησης.
