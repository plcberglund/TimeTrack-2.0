# TimeTrack 2.0

En Android-app för att rapportera arbetstid vecka för vecka och skicka in den
till chefen som ett snyggt Excel-dokument.

## Funktioner

- **Veckovy med dag-boxar** – varje dag (Mån–Sön) är en egen box där du lägger
  till arbetspass.
- **Arbetspass** med fälten: Företag, Arbetsplats/plats, Anteckning, Antal
  timmar och OB-timmar. OB räknas separat och läggs inte ovanpå de vanliga
  timmarna.
- **Snabbknappar** – när du skrivit ett företag eller en arbetsplats sparas det
  automatiskt som en snabbknapp (per fält) till nästa gång.
- **Ledig / Sjuk / Semester** – markera hela dagar med ett tryck.
- **Månadsvy** – översikt per månad med totaler och OB för året.
- **Skicka rapport** – ett tryck genererar ett formaterat Excel-dokument
  (`.xlsx`) med en box per dag och en summarad, och öppnar Gmail med filen
  bifogad. Du väljer själv mottagare i Gmail.
- Allt sparas **lokalt på enheten** – inget konto, ingen inloggning.

## Teknik

- Kotlin + Jetpack Compose (Material 3), mörkt orange-tema.
- Room för lokal lagring, DataStore för inställningar (ditt namn).
- Egen lättviktig XLSX-generator (inga tunga tredjepartsberoenden) – se
  `util/XlsxWriter.kt` och `util/ReportExporter.kt`.
- minSdk 26, compileSdk 35.

## Bygga och köra

Appen byggs i **Android Studio** (eller med `./gradlew`):

1. Öppna projektet i Android Studio (Giraffe eller senare).
2. Låt Gradle synka (laddar ner AGP, Compose m.m.).
3. Kör på en enhet/emulator med Android 8.0 (API 26) eller senare.

Kommandorad:

```bash
./gradlew assembleDebug      # bygger en debug-APK
./gradlew installDebug       # installerar på ansluten enhet
```

APK:n hamnar i `app/build/outputs/apk/debug/`.

## Projektstruktur

```
app/src/main/java/com/timetrack/
├── MainActivity.kt
├── TimeTrackApp.kt
├── data/            # Room (Shift, DayMark, Suggestion), repository, inställningar
├── ui/              # Compose-skärmar, ViewModel, tema
└── util/            # Vecko-logik, XLSX-generator, Excel-export + Gmail-delning
```
