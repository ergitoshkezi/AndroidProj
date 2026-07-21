i# Pipeline Universale di Acquisizione Menu — LLM-Minimal

## Contesto & Problema

Il sistema attuale funziona così:

```
QR/URL → WebView → testo grezzo → LLM (estrazione+parsing) → MenuCategory
```

**Problema**: l'LLM fa tutto — estrazione, parsing, strutturazione — ricevendo spesso HTML o testo da 20-100 KB. Costoso, lento, inaffidabile su siti eterogenei.

Il `DomIterativeMenuExtractor` già esiste ma usa l'LLM per estrarre categorie e piatti dal DOM JSON — ancora LLM-heavy.

---

## Obiettivo

```
QR/URL/PDF/Immagine
    ↓ Acquisizione
    ↓ HTML/testo
    ↓ Estrazione strutturata deterministica
    ↓ JSON grezzo (candidati piatti)
    ↓ LLM leggero (validazione/normalizzazione)
    ↓ List<MenuCategory> finale
```

L'LLM riceve **solo un JSON 2-5 KB** di candidati voci, non HTML o testo libero.

---

## Analisi dello Stato Attuale

### Cosa esiste già (da riutilizzare)

| Componente | Stato | Ruolo nuovo |
|---|---|---|
| `WebViewMenuExtractor` | ✅ Ottimo | Acquisizione HTML/JS — invariato |
| `PdfMenuExtractor` | ✅ Ok | Acquisizione PDF — invariato |
| `ImageMenuExtractor` | ✅ Ok | OCR — invariato |
| `DomBlockScorer` | ✅ Riutilizzare | Scoring blocchi — potenziare |
| `DomNoiseFilter` | ✅ Riutilizzare | Filtraggio rumore |
| `MenuBlockSelector` | ✅ Riutilizzare | Selezione blocchi |
| `DomToJsonConverter` | ⚠️ Modificare | Conversione → ora produce JSON candidati strutturati |
| `DomIterativeMenuExtractor` | 🔄 Sostituire | Nuovo orchestratore deterministico |
| `MenuParserPipeline` | ⚠️ Estendere | Integrare nuovo percorso DOM |
| `QrMenuImportScreen` | ⚠️ Aggiornare | Nuovo flow orchestrazione |
| `LLMApiClient` | ⚠️ Nuovo metodo | `validateMenuJson()` — solo normalizzazione |

### Cosa manca (da creare)

| Componente | Responsabilità |
|---|---|
| `StructuredDataExtractor` | Estrae JSON-LD / Schema.org / Microdata / OpenGraph dal HTML raw |
| `SemanticBlockParser` | Converte blocchi DOM in `CandidateMenuItem` senza LLM |
| `CandidateMenuItem` | Modello dati intermedio (pre-LLM) |
| `MenuCandidateJson` | Serializzazione JSON grezzo → input LLM |
| `DuplicateRemover` | Deduplicazione piatti (mobile/desktop duplicati) |
| `QualityScorer` | Confidence score per ogni candidato |
| `UniversalMenuPipeline` | Orchestratore principale — sostituisce DomIterativeMenuExtractor |

---

## Architettura Proposta

### Modello Intermedio

```kotlin
data class CandidateMenuItem(
    val name: String,
    val description: String = "",
    val price: Double? = null,
    val priceRaw: String = "",
    val category: String = "",
    val allergens: List<String> = emptyList(),
    val confidence: Float = 0f,        // 0.0 - 1.0
    val source: ExtractionSource       // JSON_LD, SCHEMA_ORG, DOM_SEMANTIC, DOM_HEURISTIC, OCR
)

enum class ExtractionSource { JSON_LD, SCHEMA_ORG, MICRODATA, DOM_SEMANTIC, DOM_HEURISTIC, PDF, OCR }
```

### Pipeline Step-by-Step

```
FASE 1 — ACQUISIZIONE (invariata)
  QR → URL
  URL → WebExtractResult (HTML + domSnapshot + capturedApiJson)
  PDF → testo
  Immagine → OCR testo

FASE 2 — ESTRAZIONE STRUTTURATA (NUOVA, priorità decrescente)
  2a. StructuredDataExtractor:
      Cerca JSON-LD <script type="application/ld+json">
      Cerca itemtype="https://schema.org/Menu"
      Cerca itemtype="https://schema.org/MenuItem"
      Se trovati → List<CandidateMenuItem> con source=JSON_LD/SCHEMA_ORG
      → Salta fase 2b/2c se confidence > soglia

  2b. capturedApiJson (già disponibile in WebExtractResult):
      Analizza JSON intercettati da XHR/fetch
      Cerca pattern: {menu, items, dishes, categories, sections}
      Se trovati → List<CandidateMenuItem> con source=DOM_SEMANTIC

  2c. SemanticBlockParser (su domSnapshot.blocks):
      Prende i blocchi già scored/filtrati da DomBlockScorer
      Algoritmo sliding-window su blocchi ordinati:
        - Blocco con foodHits>0 e priceHits>0 → CandidateMenuItem
        - Blocco heading-like vicino a blocchi con prezzi → categoria
        - NON usa pattern rigidi "riga con €" ma analisi contestuale
      Output: List<CandidateMenuItem> con source=DOM_HEURISTIC

FASE 3 — DEDUPLICAZIONE
  DuplicateRemover: rimuove candidati duplicati (nome simile + prezzo uguale)
  Preferisce source più autorevole (JSON_LD > SCHEMA_ORG > DOM_SEMANTIC > DOM_HEURISTIC)

FASE 4 — QUALITY SCORING
  QualityScorer: per ogni CandidateMenuItem calcola confidence:
    +0.4 se ha nome (>2 char)
    +0.3 se ha prezzo valido
    +0.2 se ha descrizione
    +0.1 se ha categoria
  Scarta candidati con confidence < 0.4

FASE 5 — SERIALIZZAZIONE JSON GREZZO
  MenuCandidateJson.serialize(candidates):
  {
    "restaurant": "...",
    "sections": [
      {
        "name": "Pizze",
        "items": [
          {"name": "Margherita", "description": "...", "price": 8.50, "confidence": 0.9}
        ]
      }
    ],
    "totalCandidates": 42,
    "extractionSource": "JSON_LD"
  }
  Target: ≤ 5 KB

FASE 6 — LLM VALIDAZIONE (opzionale, solo se necessario)
  LLMApiClient.validateMenuJson(candidateJson):
  Prompt: "Correggi errori ortografici, unifica categorie duplicate, 
           rimuovi voci non alimentari. NON inventare piatti. 
           Restituisci JSON identico con correzioni minime."
  Input: JSON 2-5 KB
  Output: JSON corretto

FASE 7 — MAPPING FINALE
  AstToDtoMapper (esistente) o nuovo JsonToMenuMapper:
  JSON → List<MenuCategory>
```

---

## File da Creare / Modificare

### [NEW] Modelli intermedi
#### [NEW] `parser/extraction/CandidateMenuItem.kt`
Modello dati pre-LLM con confidence score e source.

#### [NEW] `parser/extraction/ExtractionSource.kt`
Enum delle sorgenti di estrazione.

---

### [NEW] Estrattori deterministici

#### [NEW] `parser/extraction/StructuredDataExtractor.kt`
- Parsifica `<script type="application/ld+json">` dal HTML raw
- Cerca `@type: Menu`, `@type: MenuItem`, `@type: MenuSection`
- Gestisce Schema.org Microdata (`itemtype`, `itemprop`)
- Restituisce `List<CandidateMenuItem>` con alta confidence

#### [NEW] `parser/extraction/ApiJsonMenuExtractor.kt`
- Analizza `capturedApiJson` da `WebExtractResult`
- Cerca pattern di menu in JSON intercettati da XHR/fetch
- Supporta strutture annidate comuni (Deliverect, TheFork, ecc.)

#### [NEW] `parser/extraction/SemanticBlockParser.kt`
- Riceve `List<DomBlock>` già scored
- Algoritmo sliding-window contestuale:
  - Raggruppa blocchi con alta densità prezzo+cibo
  - Identifica categorie da heading-blocks vicini
  - NON usa pattern rigidi tipo "riga€"
- Restituisce `List<CandidateMenuItem>`

#### [NEW] `parser/extraction/DuplicateRemover.kt`
- Deduplicazione per nome simile (Levenshtein o normalizzazione)
- Preferisce fonte più autorevole

#### [NEW] `parser/extraction/QualityScorer.kt`
- Calcola confidence score per ogni candidato
- Filtra sotto soglia configurabile

#### [NEW] `parser/extraction/MenuCandidateJson.kt`
- Serializza candidati in JSON strutturato
- Raggruppa per categoria
- Produce output ≤ 5 KB

---

### [NEW] Orchestratore principale

#### [NEW] `parser/extraction/UniversalMenuPipeline.kt`
Sostituisce `DomIterativeMenuExtractor` come orchestratore.

```kotlin
object UniversalMenuPipeline {
    suspend fun extract(
        webResult: WebExtractResult?,
        rawHtml: String = "",
        onProgress: (String) -> Unit
    ): List<MenuCategory>
}
```

---

### [MODIFY] Componenti esistenti

#### [MODIFY] `LLMApiClient.kt`
Aggiungere metodo:
```kotlin
suspend fun validateMenuJson(candidateJson: String): String
```
Con prompt di sola normalizzazione/correzione semantica.

#### [MODIFY] `QrMenuImportScreen.kt`
Sostituire il blocco `processUrl()`:
- Rimuovere chiamata diretta a `LLMApiClient().processMenuText(content)`
- Aggiungere chiamata a `UniversalMenuPipeline.extract(extracted, ...)`
- Mantenere fallback a pipeline testuale per PDF/OCR

#### [MODIFY] `DomIterativeMenuExtractor.kt`
- Deprecare (wrapper che chiama `UniversalMenuPipeline`)
- O mantenere per retrocompatibilità

---

## Ordine di Implementazione

1. `CandidateMenuItem.kt` + `ExtractionSource.kt` (modelli)
2. `StructuredDataExtractor.kt` (JSON-LD — massimo ROI)
3. `ApiJsonMenuExtractor.kt` (XHR intercettati — già disponibili)
4. `SemanticBlockParser.kt` (DOM heuristic — sostituzione pattern rigidi)
5. `DuplicateRemover.kt` + `QualityScorer.kt` + `MenuCandidateJson.kt`
6. `UniversalMenuPipeline.kt` (orchestratore)
7. Modifiche a `LLMApiClient.kt`
8. Modifiche a `QrMenuImportScreen.kt`

---

## Piano di Verifica

### Automatica
- Unit test su `StructuredDataExtractor` con HTML reali (JSON-LD campione)
- Unit test su `SemanticBlockParser` con blocchi DOM campione
- Unit test `QualityScorer` — confidence thresholds

### Manuale
- Test su 5 URL reali di ristoranti (statici, Next.js, accordion, PDF, immagine)
- Verificare token LLM usati: target < 1000 token per estrazione
- Verificare completezza: nessun piatto perso rispetto al menu reale

---

## Open Questions

> [!IMPORTANT]
> **Soglia fallback LLM**: Se la pipeline deterministica produce 0 candidati con confidence > 0.4, si usa l'LLM come fallback full (comportamento attuale) o si restituisce lista vuota?
> Raccomandato: fallback LLM solo se candidati < 3.

> [!IMPORTANT]
> **Fase di enrichment**: L'enrichment (calorie, paese, regione) attuale usa l'LLM. Rimane invariato o va nella fase 6 (LLM validation)?
> Raccomandato: tenerlo separato, opzionale post-conferma utente.

> [!NOTE]
> **OkHttp per HTML raw**: Attualmente si usa WebView per tutto. Per siti statici, OkHttp + Jsoup sarebbe più veloce (nessun rendering JS). Aggiungere OkHttp come percorso primario con fallback WebView?
