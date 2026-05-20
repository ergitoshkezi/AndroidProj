package com.example.ingredient

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val EXTRACTOR_TAG = "WebViewExtractor"
private const val PAGE_SETTLE_MS = 1500L
private const val BACKUP_EXTRACT_MS = 25_000L   // slightly longer to allow scroll+expand
private const val TIMEOUT_MS = 50_000L

data class WebExtractResult(
    val visibleText: String,
    val capturedApiJson: List<String>
) {
    /** Best content to send to LLM: prefer menu-looking JSON, fall back to visible text. */
    fun bestContent(): String {
        // If visibleText is Next.js embedded data, use it directly (highest quality)
        if (visibleText.startsWith("__NEXT_DATA__:") || visibleText.startsWith("__NUXT_DATA__:")) {
            val json = visibleText.substringAfter(":")
            return "DATI JSON PAGINA (Next.js/Nuxt SSR):\n${json.take(30000)}"
        }

        val menuJson = capturedApiJson.firstOrNull { json ->
            listOf("price", "prezzo", "menu", "dish", "piatto", "food", "item", "categoria", "description", "ingredient")
                .any { json.contains(it, ignoreCase = true) }
        }
        return if (menuJson != null && menuJson.length > 200) {
            "DATI API JSON DEL SITO:\n${menuJson.take(20000)}\n\n---\nTESTO VISIBILE PAGINA:\n${visibleText.take(10000)}"
        } else {
            visibleText.take(30000)
        }
    }
}

/** Renders a URL fully (JavaScript executed) using an off-screen WebView, then extracts content. */
object WebViewMenuExtractor {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(
        context: Context,
        url: String,
        onProgress: (String) -> Unit
    ): WebExtractResult = withContext(Dispatchers.Main) {

        val deferred = CompletableDeferred<WebExtractResult>()
        val capturedApiJson = mutableListOf<String>()

        val webView = WebView(context) // must use Activity context, NOT applicationContext

        // evaluateJavascript only works when WebView is attached to a window.
        // Add it as a 1×1 invisible view so the engine runs, then remove when done.
        val rootView = (context as? android.app.Activity)
            ?.window?.decorView as? android.view.ViewGroup
        val attachParams = android.view.ViewGroup.LayoutParams(1, 1)
        webView.visibility = android.view.View.INVISIBLE
        rootView?.addView(webView, attachParams)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // JS interface — receives JSON blobs captured by injected fetch/XHR patches
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onApiJson(data: String) {
                    if (data.length > 100) {
                        synchronized(capturedApiJson) { capturedApiJson.add(data) }
                        Log.d(EXTRACTOR_TAG, "Captured API JSON (${data.length} chars)")
                    }
                }
            },
            "MenuCapture"
        )

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, pageUrl: String, favicon: android.graphics.Bitmap?) {
                view.evaluateJavascript(JS_NETWORK_INTERCEPTOR, null)
            }

            override fun onPageFinished(view: WebView, pageUrl: String) {
                onProgress("Rendering JavaScript…")
                view.evaluateJavascript(JS_NETWORK_INTERCEPTOR, null)

                // Step 1: expand accordions + scroll, then extract
                view.postDelayed({
                    if (!deferred.isCompleted) {
                        onProgress("Espansione sezioni e scroll…")
                        view.evaluateJavascript(JS_EXPAND_AND_SCROLL) {
                            // Second scroll pass after accordion expansion (new content may have loaded)
                            view.postDelayed({
                                if (!deferred.isCompleted) {
                                    view.evaluateJavascript(JS_EXPAND_AND_SCROLL, null)
                                }
                            }, 1500L)
                            // Wait for lazy-loaded content to settle, then extract
                            view.postDelayed({
                                if (!deferred.isCompleted) {
                                    onProgress("Lettura struttura pagina…")
                                    extractAndComplete(view, deferred, capturedApiJson)
                                }
                            }, 4000L)
                        }
                    }
                }, PAGE_SETTLE_MS)
            }

            // Accept SSL errors — many restaurant sites have cert issues
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                Log.w(EXTRACTOR_TAG, "SSL error (proceeding): ${error.primaryError}")
                handler.proceed()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Only fail on main frame errors; subresource errors are ignorable
                if (request.isForMainFrame) {
                    Log.w(EXTRACTOR_TAG, "Main frame error: ${error.description} — trying extraction anyway")
                    view.postDelayed({
                        if (!deferred.isCompleted) extractAndComplete(view, deferred, capturedApiJson)
                    }, PAGE_SETTLE_MS)
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400 && !deferred.isCompleted) {
                    deferred.completeExceptionally(Exception("HTTP ${errorResponse.statusCode}"))
                }
            }
        }

        onProgress("Caricamento pagina con JavaScript…")
        webView.loadUrl(url)

        // Backup timer: force extraction after BACKUP_EXTRACT_MS even if onPageFinished never fires
        webView.postDelayed({
            if (!deferred.isCompleted) {
                Log.w(EXTRACTOR_TAG, "Backup timer fired — forcing extraction")
                onProgress("Estrazione forzata…")
                extractAndComplete(webView, deferred, capturedApiJson)
            }
        }, BACKUP_EXTRACT_MS)

        try {
            withTimeout(TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Log.e(EXTRACTOR_TAG, "Extraction failed", e)
            // On timeout, try one last synchronous-style extraction before giving up
            if (!deferred.isCompleted) {
                throw e
            }
            deferred.await()
        } finally {
            rootView?.removeView(webView)
            webView.destroy()
        }
    }
}

private fun extractAndComplete(
    view: WebView,
    deferred: CompletableDeferred<WebExtractResult>,
    capturedApiJson: MutableList<String>
) {
    view.evaluateJavascript(JS_EXTRACT_TEXT) { raw ->
        val text = raw
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?.replace("\\\"", "\"")
            ?.replace("\\/", "/")
            ?: ""
        Log.d(EXTRACTOR_TAG, "Extracted ${text.length} chars, ${capturedApiJson.size} API responses")
        Log.d(EXTRACTOR_TAG, "Text preview: ${text.take(800).replace("\n", "↵")}")
        if (!deferred.isCompleted) {
            deferred.complete(WebExtractResult(text, capturedApiJson.toList()))
        }
    }
}

/** Scrolls the page and all overflow containers to trigger lazy loading,
 *  then expands all accordion/tab/collapse elements. */
private val JS_EXPAND_AND_SCROLL = """
(function() {
    try {
        // 1. Scroll main page to bottom in steps (triggers lazy loading)
        var totalHeight = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
        var step = Math.max(400, Math.floor(totalHeight / 8));
        var pos = 0;
        function scrollMainStep() {
            pos += step;
            window.scrollTo(0, pos);
            if (pos < totalHeight) setTimeout(scrollMainStep, 100);
        }
        scrollMainStep();

        // 2. Also scroll any overflow:auto/scroll containers (menus inside scroll panes)
        try {
            document.querySelectorAll('*').forEach(function(el) {
                try {
                    var cs = window.getComputedStyle(el);
                    var ov = cs.overflow + cs.overflowY;
                    if ((ov.indexOf('auto') !== -1 || ov.indexOf('scroll') !== -1) && el.scrollHeight > el.clientHeight + 50) {
                        el.scrollTop = el.scrollHeight;
                    }
                } catch(e) {}
            });
        } catch(e) {}

        // 3. Click all expand/accordion/tab/show-more elements
        var expandSelectors = [
            '[aria-expanded="false"]',
            '[data-toggle="collapse"]','[data-toggle="tab"]','[data-bs-toggle="collapse"]',
            'button[class*="expand"]','button[class*="accordion"]',
            'button[class*="show"]','button[class*="more"]','button[class*="toggle"]',
            '[class*="accordion__trigger"]','[class*="accordion-trigger"]',
            '[class*="collapse-trigger"]','[class*="show-more"]','[class*="showmore"]',
            '[class*="leggi"]','[class*="vedi"]','[class*="expand"]',
            '[class*="tab-button"]','[class*="tabbutton"]','[role="tab"]',
            'details:not([open]) summary'
        ];
        expandSelectors.forEach(function(sel) {
            try {
                document.querySelectorAll(sel).forEach(function(el) {
                    try { el.click(); } catch(e) {}
                });
            } catch(e) {}
        });

        // 4. Also open all <details> elements directly
        try {
            document.querySelectorAll('details').forEach(function(d) { d.open = true; });
        } catch(e) {}

    } catch(e) {}
    return 'done';
})()
""".trimIndent()

/** Patches window.fetch and XMLHttpRequest to forward JSON responses to MenuCapture.onApiJson */
private val JS_NETWORK_INTERCEPTOR = """
(function() {
    if (window._menuCapturePatched) return;
    window._menuCapturePatched = true;

    // Patch fetch
    const origFetch = window.fetch;
    window.fetch = async function(...args) {
        const res = await origFetch.apply(this, args);
        try {
            const ct = res.headers.get('content-type') || '';
            if (ct.includes('json')) {
                res.clone().text().then(function(t) {
                    if (window.MenuCapture) window.MenuCapture.onApiJson(t);
                });
            }
        } catch(e) {}
        return res;
    };

    // Patch XMLHttpRequest
    const origOpen = XMLHttpRequest.prototype.open;
    const origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._url = url;
        return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
        this.addEventListener('load', function() {
            try {
                const ct = this.getResponseHeader('content-type') || '';
                if (ct.includes('json') && this.responseText && this.responseText.length > 100) {
                    if (window.MenuCapture) window.MenuCapture.onApiJson(this.responseText);
                }
            } catch(e) {}
        });
        return origSend.apply(this, arguments);
    };
})();
""".trimIndent()

/** Full DOM tree walker: visits EVERY visible element in document order.
 *  Extracts direct text from each node (no duplication), marks headings as CATEGORIA.
 *  Returns structured text preserving reading order. */
private val JS_EXTRACT_TEXT = """
(function() {
    try {
        // Priority 1: Next.js __NEXT_DATA__ — full menu JSON embedded in page
        if (window.__NEXT_DATA__) {
            var nd = JSON.stringify(window.__NEXT_DATA__);
            if (nd.length > 500) return '__NEXT_DATA__:' + nd;
        }
        var ndEl = document.getElementById('__NEXT_DATA__');
        if (ndEl && ndEl.textContent && ndEl.textContent.length > 500) {
            return '__NEXT_DATA__:' + ndEl.textContent;
        }

        // Priority 2: Nuxt/SSR data
        if (window.__NUXT__) {
            var nuxt = JSON.stringify(window.__NUXT__);
            if (nuxt.length > 500) return '__NUXT_DATA__:' + nuxt;
        }

        // ── Generic section extractor ─────────────────────────────────────────
        // Extracts ALL semantic sections from ANY site structure.
        // Returns sections as markers — Kotlin side pre-filters non-menu sections.
        //
        // Marker types:
        //   === CATEGORIA: X ===  →  high-confidence menu category (accordion button)
        //   === SEZIONE: X ===    →  generic section (heading/details) — may or may not be menu
        //
        // Priority 3: Accordion/collapsed-panel
        //   button[class*="accordion|categ"], <summary>, Bootstrap collapse triggers
        var sectionResult = (function() {

            // ── Helper: get content of an anchor element ──────────────────────
            function getContent(anchor) {
                var texts = [];
                // Strategy A: next sibling chain (standard accordion)
                var sib = anchor.nextElementSibling;
                var limit = 6;
                while (sib && limit-- > 0) {
                    var tag = sib.tagName || '';
                    if (/^H[1-6]$/.test(tag) || tag === 'SUMMARY') break;
                    if (tag === 'BUTTON' && /accordion|categ/i.test(sib.className || '')) break;
                    var txt = (sib.innerText || sib.textContent || '').replace(/\s+/g, ' ').trim();
                    if (txt.length > 5) texts.push(txt);
                    sib = sib.nextElementSibling;
                }
                // Strategy B: parent's next sibling (wrapped accordion: <div><button/><div class="panel">)
                if (texts.length === 0 && anchor.parentElement) {
                    var parentSib = anchor.parentElement.nextElementSibling;
                    if (parentSib) {
                        var txt = (parentSib.innerText || parentSib.textContent || '').replace(/\s+/g, ' ').trim();
                        if (txt.length > 5) texts.push(txt);
                    }
                }
                // Strategy C: aria-controls target (Bootstrap collapse)
                if (texts.length === 0) {
                    var target = anchor.getAttribute('data-target') || anchor.getAttribute('aria-controls') || anchor.getAttribute('data-bs-target');
                    if (target) {
                        var panel = document.querySelector(target);
                        if (panel) {
                            var txt = (panel.innerText || panel.textContent || '').replace(/\s+/g, ' ').trim();
                            if (txt.length > 5) texts.push(txt);
                        }
                    }
                }
                return texts.join('\n').trim();
            }

            // ── Priority 3A: Accordion buttons ────────────────────────────────
            var accordionSels = [
                'button[class*="accordion"]', 'button[class*="categ"]',
                '[data-toggle="collapse"]', '[data-bs-toggle="collapse"]',
                '[class*="menu-section"][class*="title"]',
                '[class*="menu__category__header"]', '[class*="category-title"]',
                '[class*="dish-category"]'
            ];
            for (var ai = 0; ai < accordionSels.length; ai++) {
                var btns = Array.prototype.slice.call(document.querySelectorAll(accordionSels[ai]));
                if (btns.length < 2) continue;
                btns.forEach(function(b) { try { b.click(); } catch(e) {} }); // expand
                var secs = [];
                btns.forEach(function(btn) {
                    var title = (btn.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!title || title.length > 100) return;
                    var content = getContent(btn);
                    if (content.length > 10) secs.push('=== CATEGORIA: ' + title + ' ===\n' + content);
                });
                if (secs.length >= 2) return secs.join('\n\n');
            }

            // ── Priority 3B: <details>/<summary> (HTML5 native accordion) ─────
            var details = Array.prototype.slice.call(document.querySelectorAll('details'));
            if (details.length >= 2) {
                details.forEach(function(d) { d.open = true; }); // expand all
                var secs = [];
                details.forEach(function(d) {
                    var summary = d.querySelector('summary');
                    if (!summary) return;
                    var title = (summary.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!title || title.length > 100) return;
                    var content = Array.prototype.slice.call(d.childNodes)
                        .filter(function(n) { return n !== summary; })
                        .map(function(n) { return (n.innerText || n.textContent || '').trim(); })
                        .join('\n').trim();
                    if (content.length > 10) secs.push('=== CATEGORIA: ' + title + ' ===\n' + content);
                });
                if (secs.length >= 2) return secs.join('\n\n');
            }

            // ── Priority 3C: Generic heading-based sections ───────────────────
            // h2/h3 as section anchors — catch plain restaurant sites
            // Uses === SEZIONE: === so Kotlin can pre-filter non-menu ones
            var headings = Array.prototype.slice.call(document.querySelectorAll('h2,h3'));
            if (headings.length >= 2) {
                var secs = [];
                headings.forEach(function(h) {
                    var title = (h.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!title || title.length > 100) return;
                    var content = getContent(h);
                    // Also try: all siblings until next heading (for cases where content isn't a direct sibling)
                    if (content.length < 10) {
                        var next = h.nextElementSibling;
                        var buf = []; var lim = 20;
                        while (next && lim-- > 0) {
                            if (/^H[1-3]$/.test(next.tagName || '')) break;
                            var t = (next.innerText || next.textContent || '').replace(/\s+/g, ' ').trim();
                            if (t.length > 3) buf.push(t);
                            next = next.nextElementSibling;
                        }
                        content = buf.join('\n').trim();
                    }
                    if (content.length > 10) secs.push('=== SEZIONE: ' + title + ' ===\n' + content);
                });
                if (secs.length >= 2) return secs.join('\n\n');
            }

            return null;
        })();
        if (sectionResult) return sectionResult;

        // ── Clutter removal ──────────────────────────────────────────────────
        // Remove elements that NEVER contain menu data
        var clutterSelectors = [
            'script','style','noscript','iframe','svg',
            'nav','footer','header','aside',
            '[aria-hidden="true"]','[role="banner"]','[role="navigation"]',
            '[class*="cookie"]','[class*="gdpr"]','[class*="popup"]',
            '[class*="modal-backdrop"]','[class*="overlay"]',
            '[class*="language"]','[class*="translate"]',
            '[class*="share"]','[class*="social"]',
            '[class*="gflag"]','[class*="google_translate"]',
            '[id*="cookie"]','[id*="gdpr"]','[id*="popup"]',
            '[id*="translate"]','[id*="google_translate"]',
            '[class*="orari"]','[class*="opening-hour"]','[class*="schedule"]',
            '.fold_reply',
            'select'   // language pickers — 50+ country names → noise
        ];
        clutterSelectors.forEach(function(sel) {
            try { document.querySelectorAll(sel).forEach(function(el) {
                try { if (el.parentNode) el.parentNode.removeChild(el); } catch(e) {}
            }); } catch(e) {}
        });

        // ── Tag classification ────────────────────────────────────────────────
        // NOTE: BUTTON is intentionally NOT in skipTags — accordion buttons carry category names
        var skipTags = {'SCRIPT':1,'STYLE':1,'NOSCRIPT':1,'SVG':1,'PATH':1,
            'IMG':1,'INPUT':1,'SELECT':1,'OPTION':1,'OPTGROUP':1,
            'TEXTAREA':1,'IFRAME':1,'CANVAS':1,'VIDEO':1,'AUDIO':1};

        var headingTags = {'H1':1,'H2':1,'H3':1,'H4':1};
        var subheadTags = {'H5':1,'H6':1,'STRONG':1,'B':1,'CAPTION':1,'TH':1,'LABEL':1};

        // Returns true if an element should be treated as a menu CATEGORY header
        function isCategoryEl(el, tag, text) {
            // Standard heading tags
            if (headingTags[tag] && text.length <= 100) return true;
            // Buttons/divs with accordion/category-style class names
            if (!el.className) return false;
            var cls = el.className.toLowerCase();
            return (tag === 'BUTTON' || tag === 'A' || tag === 'DIV' || tag === 'SPAN') &&
                   text.length <= 80 &&
                   (cls.indexOf('accordion') !== -1 || cls.indexOf('categ') !== -1 ||
                    cls.indexOf('section-title') !== -1 || cls.indexOf('menu-title') !== -1 ||
                    cls.indexOf('tab-title') !== -1 || cls.indexOf('panel-title') !== -1 ||
                    cls.indexOf('menu-section') !== -1 || cls.indexOf('menu-cat') !== -1 ||
                    cls.indexOf('dish-cat') !== -1 || cls.indexOf('food-cat') !== -1);
        }

        // ── TreeWalker pass ───────────────────────────────────────────────────
        var root = document.body || document.documentElement;
        var lines = [];
        var seen = {};

        var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, null, false);
        var el = root;

        while (el) {
            var tag = el.tagName || '';

            if (!skipTags[tag]) {
                // Skip elements with display:none or visibility:hidden
                // (max-height:0 / overflow:hidden is intentionally NOT skipped — accordion content is still valid)
                var visible = true;
                try {
                    var cs = window.getComputedStyle(el);
                    if (cs.display === 'none' || cs.visibility === 'hidden') visible = false;
                } catch(e) {}

                if (visible) {
                    // Collect DIRECT text-node children only (avoids duplicating descendant text)
                    var directText = '';
                    for (var ci = 0; ci < el.childNodes.length; ci++) {
                        var cn = el.childNodes[ci];
                        if (cn.nodeType === 3) {  // TEXT_NODE
                            var t = cn.textContent.replace(/[\n\r\t]+/g, ' ').replace(/ {2,}/g, ' ').trim();
                            if (t) directText += t + ' ';
                        }
                    }
                    directText = directText.trim();

                    // Skip opening-hours lines (e.g. "lunedì 12-14:30, 19-23:30")
                    var isSchedule = (
                        /^(lun|mar|mer|gio|ven|sab|dom|mon|tue|wed|thu|fri|sat|sun)/i.test(directText) ||
                        /\d{1,2}[:\-]\d{2}\s*[,\-–]\s*\d{1,2}[:\-]\d{2}/.test(directText) ||
                        /^orari\b/i.test(directText) ||
                        /^(clicca per|opening hours|hours?:)/i.test(directText)
                    );

                    if (!isSchedule && directText && directText.length >= 2 && !seen[directText]) {
                        seen[directText] = 1;

                        if (isCategoryEl(el, tag, directText)) {
                            lines.push('\n=== CATEGORIA: ' + directText + ' ===');
                        } else if (subheadTags[tag] && directText.length <= 120) {
                            lines.push('** ' + directText + ' **');
                        } else {
                            lines.push(directText);
                        }
                    }
                }
            }

            el = walker.nextNode();
        }

        var output = lines.join('\n').replace(/\n{3,}/g, '\n\n').trim();
        if (output.length > 100) return output.substring(0, 60000);

        // Fallback: plain innerText
        return root.innerText ? root.innerText.trim().substring(0, 60000) : '';

    } catch(e) {
        try { return (document.body || document.documentElement).innerText.trim().substring(0, 60000); } catch(e2) { return ''; }
    }
})()
""".trimIndent()
