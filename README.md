# TRW Portfolio Signal Watcher & Alarm (Android)

Αυτόνομη εφαρμογή Android σε Kotlin που παρακολουθεί αυτόματα κάθε βράδυ στις **02:00:00 π.μ.** το κανάλι Portfolio Signals στο **The Real World** και χτυπάει δυνατό ξυπνητήρι **ΜΟΝΟ** αν το νέο signal διαφέρει από το προηγούμενο αποθηκευμένο.

---

## 🎯 Τεχνικά Χαρακτηριστικά

1. **Μόνιμο Login Session (The Real World)**:
   - Πλήρες `WebView` με ενεργοποιημένα `javaScriptEnabled`, `domStorageEnabled` και third-party cookies μέσω `CookieManager`.
   - Συνδέεστε μία φορά στον λογαριασμό σας και το session διατηρείται μόνιμα.

2. **Εξαγωγή Signal με JavaScript (Αγνόηση Emoji Reactions)**:
   - Διαβάζει το `document.body.innerText`.
   - Εντοπίζει όλα τα blocks κειμένου που ξεκινούν από `"Portfolio Signal Update"` και τελειώνουν ακριβώς πριν το `"@I Understand The Signals"`.
   - Κάνει normalize όλα τα κενά και τις αλλαγές γραμμής.
   - Επιστρέφει το πιο πρόσφατο (`latestSignal`), αγνοώντας πλήρως τους αριθμούς από emoji reactions που αλλάζουν συνεχώς.

3. **Αυτόματος Νυχτερινός Έλεγχος (02:00 - 02:10 π.μ.)**:
   - Στις **02:00:00 π.μ.** το `AlarmReceiver` εκκινεί την `NightCheckActivity`.
   - Ενεργοποιεί `setShowWhenLocked(true)`, `setTurnScreenOn(true)` και `FLAG_KEEP_SCREEN_ON` με ελάχιστη φωτεινότητα (`screenBrightness = 0.01f`, μαύρη οθόνη) ώστε να μην ενοχλεί στον ύπνο.
   - Επαναλαμβάνει τον έλεγχο κάθε **45 δευτερόλεπτα** (με 15 δευτερόλεπτα αναμονή για φόρτωση DOM).
   - **Αν βρεθεί νέο signal διαφορετικό από το `saved_signal`**:
     - Αποθηκεύει αμέσως το νέο signal.
     - Επαναφέρει τη φωτεινότητα στο **100%**, εμφανίζει το νέο signal με μεγάλα γράμματα και ένα τεράστιο κόκκινο κουμπί **STOP ALARM**.
     - Ανεβάζει το `STREAM_ALARM` στο **100% μέγιστη ένταση** και παίζει συνεχή ήχο ξυπνητηριού (`RingtoneManager.TYPE_ALARM`) + δόνηση μέχρι να πατηθεί το κουμπί.
   - **Αν φτάσει 02:10 π.μ. χωρίς νέο signal**:
     - Κλείνει αθόρυβα (`finishAndRemoveTask()`) ώστε να σβήσει η οθόνη.
     - Προγραμματίζει αυτόματα τον επόμενο έλεγχο για την επόμενη μέρα στις 02:00:00 π.μ.

4. **Ανθεκτικότητα σε Επανεκκίνηση & Doze Mode**:
   - `BootReceiver` για αυτόματο επαναπρογραμματισμό μετά από επανεκκίνηση του κινητού.
   - Χρήση `setAlarmClock` / `setExactAndAllowWhileIdle` για εκτέλεση ακόμα και σε βαθύ Doze mode.
   - Αυτόματη αίτηση εξαιρέσεων μπαταρίας (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) και `SCHEDULE_EXACT_ALARM`.

---

## 🚀 Βήμα-Βήμα: Πώς να το ανεβάσετε στο GitHub & να κατεβάσετε το APK

Δεν χρειάζεται να εγκαταστήσετε Android Studio ή Android SDK στο κινητό/υπολογιστή σας! Το GitHub Actions αναλαμβάνει το compile στο cloud.

### Βήμα 1: Δημιουργία Νέου Repository στο GitHub
1. Μπείτε στο [github.com/new](https://github.com/new).
2. Δώστε όνομα στο repository (π.χ. `trw-signal-alarm`).
3. Επιλέξτε **Public** ή **Private**.
4. **ΜΗΝ** επιλέξετε "Add a README file" ή ".gitignore" (υπάρχουν ήδη έτοιμα στο project).
5. Πατήστε **Create repository**.

### Βήμα 2: Ανέβασμα του κώδικα από το τερματικό
Ανοίξτε το τερματικό και εκτελέστε:

```bash
cd /Users/xristosorfanidis/.gemini/antigravity/scratch/trw-signal-alarm

# Αρχικοποίηση Git
git init
git add .
git commit -m "Initial commit of TRW Signal Alarm project"

# Σύνδεση με το αποθετήριό σας στο GitHub (αντικαταστήστε με το δικό σας URL)
git branch -M main
git remote add origin https://github.com/ΤΟ_USERNAME_ΣΑΣ/trw-signal-alarm.git
git push -u origin main
```

### Βήμα 3: Αυτόματο Compile & Κατέβασμα του `.apk`
1. Μόλις γίνει το `git push`, πηγαίνετε στο repository σας στο GitHub στο browser.
2. Πατήστε στην καρτέλα **Actions** στο επάνω μενού.
3. Θα δείτε το workflow **"Build & Package APK"** να τρέχει (διαρκεί περίπου 2–3 λεπτά).
4. Μόλις ολοκληρωθεί (πράσινο τικ ✅), κάντε κλικ επάνω στο run (π.χ. *"Initial commit of TRW Signal Alarm project"*).
5. Στο κάτω μέρος της σελίδας, στην ενότητα **Artifacts**, θα βρείτε το αρχείο **`TRW-Signal-Alarm-debug.zip`**.
6. Κατεβάστε το στο κινητό σας, κάντε αποσυμπίεση (unzip) και εγκαταστήστε το `app-debug.apk`!

---

## 📱 Πρώτη Ρύθμιση στην Εφαρμογή

1. **Σύνδεση**: Ανοίξτε την εφαρμογή. Στο WebView που εμφανίζεται, συνδεθείτε στον λογαριασμό σας στο The Real World.
2. **Αποθήκευση Τρέχοντος Signal**:
   - Μόλις φορτώσει το κανάλι των Portfolio Signals, πατήστε το κουμπί **"Read & Save Current Signal"**.
   - Το τρέχον signal αποθηκεύεται ως βάση σύγκρισης (`saved_signal`).
3. **Ενεργοποίηση Νυχτερινής Παρακολούθησης**:
   - Ενεργοποιήστε τον διακόπτη **"Daily 02:00 AM Watch"**.
   - Αποδεχθείτε τα παράθυρα για:
     - Άδεια Ακριβούς Ξυπνητηριού (`Exact Alarm`).
     - Εξαίρεση Βελτιστοποίησης Μπαταρίας (`Ignore Battery Optimizations`).
     - Άδεια Ειδοποιήσεων (`Notifications`).
4. **Δοκιμή Ήχου**: Πατήστε **"Test Alarm Sound"** για να βεβαιωθείτε ότι ο ήχος και η δόνηση λειτουργούν στο μέγιστο.

Κάθε βράδυ στις **02:00:00 π.μ.**, το κινητό θα ξυπνάει αυτόματα, θα ελέγχει το chat μέχρι τις **02:10 π.μ.**, και θα χτυπάει δυνατά ΜΟΝΟ αν αναρτηθεί νέο signal!
