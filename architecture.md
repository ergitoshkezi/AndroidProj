# Flusso di elaborazione post-scansione del QR Code

Quando l'applicazione scansiona un QR Code (tramite `QrMenuImportScreen.kt`), si avvia una pipeline di estrazione e analisi articolata per trasformare il contenuto in un menu strutturato e arricchito su Firebase.

Ecco i passaggi architettonici che il programma esegue:

## 1. Acquisizione e Normalizzazione dell'URL
Dopo la scansione, il sistema ottiene un contenuto testuale (tipicamente un URL).
L'applicazione normalizza l'URL (es. aggiungendo `https://` se mancante) prima di decidere quale motore di estrazione utilizzare.

## 2. Selezione della Strategia di Estrazione (Routing)
Il sistema valuta il tipo di URL per determinare la corretta strategia di scraping:

- **Immagini (`.jpg`, `.png`, ecc.)**: 
  Viene avviata l'estrazione ottica del testo (OCR) tramite `ImageMenuExtractor.extractText`, che elabora l'immagine per restituire il testo grezzo.

- **Documenti PDF**: 
  Viene identificato tramite `PdfMenuExtractor.isPdfUrl`. Il programma scarica il PDF ed estrae il testo internamente tramite `PdfMenuExtractor.extractText`.

- **Pagine Web Standard (HTML/JS)**: 
  Viene utilizzato un estrattore basato su WebView (`WebViewMenuExtractor.extract`) per gestire contenuti dinamici generati via JavaScript.

## 3. Elaborazione e Riconoscimento (Parsing & LLM)
Nel caso di pagine web, l'app tenta due strade in sequenza:

- **Estrazione Strutturata Veloce (`UniversalMenuPipeline.extractCompact`)**:
  Il sistema cerca dati strutturati o pre-elabora l'HTML in un JSON compatto per aggirare la dipendenza dall'AI generativa. Se questa fase va a buon fine, salta al passaggio di arricchimento.

- **Fallback all'AI (LLM)**:
  Se l'estrazione rapida fallisce o il testo proviene da OCR/PDF, il testo grezzo viene pre-processato per pulire i metadati irrilevanti (`MenuContentPreprocessor.preprocess`) e inviato all'API dell'LLM (es. Groq) tramite `LLMApiClient().processMenuText`. Il modello AI ha il compito di estrarre e classificare i piatti in categorie (es. Antipasti, Primi, ecc.).

## 4. Arricchimento dei Dati
Una volta ottenuta la struttura base del menu, i dati vengono passati a `LLMApiClient().enrichDishes`. In questa fase, l'intelligenza artificiale deduce o completa le informazioni mancanti per ogni piatto, tra cui:
- Ingredienti (spesso non esplicitati)
- Allergeni
- Calorie stimate
- Origine geografica (Nazione/Regione)

## 5. Revisione e Salvataggio (Firebase)
- Il menu elaborato viene mostrato a schermo per un'anteprima (Preview) con tutti i dettagli estratti (prezzi, categorie, ingredienti).
- L'utente (ristoratore) sceglie se **sostituire** il menu corrente o **aggiungere** i nuovi piatti a quelli esistenti (Append Mode).
- Alla conferma, la classe `FirebaseMenuUploader` si occupa di caricare l'albero strutturato dei piatti e delle categorie sul database Firebase in tempo reale, associandoli all'ID del ristorante.
