package com.trw.signalalarm

import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

data class ExtractedSignal(
    val isToday: Boolean,
    val signal: String
)

object SignalExtractor {
    private const val TAG = "SignalExtractor"

    /**
     * JavaScript snippet that:
     * 1. Auto-scrolls all scrollable containers to the bottom and clicks any jump-to-bottom buttons.
     * 2. Reads document.body.innerText.
     * 3. Finds all blocks starting with 'Portfolio Signal Update' and ending at '@I Understand The Signals'.
     * 4. Normalizes whitespaces/newlines.
     * 5. Checks if the date separator 'Today' exists in document.body.innerText and whether
     *    the latest 'Portfolio Signal Update' appears AFTER 'Today' (meaning it belongs to today).
     * 6. Returns a JSON string: {"isToday": true/false, "signal": "..."}.
     */
    const val JS_EXTRACTION_SCRIPT = """
        (function() {
            try {
                // 1. Auto-scroll to bottom of page and any scrollable containers
                window.scrollTo(0, 99999999);
                if (document.body) document.body.scrollTop = document.body.scrollHeight;
                if (document.documentElement) document.documentElement.scrollTop = document.documentElement.scrollHeight;

                var all = document.querySelectorAll('div, main, section, ul');
                for (var i = 0; i < all.length; i++) {
                    var el = all[i];
                    if (el.scrollHeight > el.clientHeight && el.clientHeight > 80) {
                        el.scrollTop = el.scrollHeight;
                    }
                }

                // Click any "jump to bottom", "scroll down", or down-arrow buttons if present
                var buttons = document.querySelectorAll('button, [role="button"], a');
                for (var b = 0; b < buttons.length; b++) {
                    var btn = buttons[b];
                    var aria = (btn.getAttribute('aria-label') || '').toLowerCase();
                    var title = (btn.getAttribute('title') || '').toLowerCase();
                    var text = (btn.innerText || '').toLowerCase().trim();
                    if (aria.includes('bottom') || aria.includes('down') || aria.includes('jump') ||
                        title.includes('bottom') || title.includes('down') ||
                        text.includes('jump to bottom') || text.includes('jump to present') || text.includes('new messages')) {
                        try { btn.click(); } catch(e){}
                    }
                }

                // 2. Read full rendered text
                var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
                if (!bodyText) {
                    return JSON.stringify({ isToday: false, signal: '' });
                }

                var startTag = 'Portfolio Signal Update';
                var endTag = '@I Understand The Signals';
                var lastSignal = '';
                var lastSignalStart = -1;
                var lastSignalEnd = -1;
                var searchPos = 0;

                while (true) {
                    var sIdx = bodyText.indexOf(startTag, searchPos);
                    if (sIdx === -1) break;
                    var eIdx = bodyText.indexOf(endTag, sIdx + startTag.length);
                    if (eIdx !== -1) {
                        var raw = bodyText.substring(sIdx, eIdx);
                        var norm = raw.replace(/\s+/g, ' ').trim();
                        if (norm.length > 0) {
                            lastSignal = norm;
                            lastSignalStart = sIdx;
                            lastSignalEnd = eIdx;
                        }
                        searchPos = eIdx + endTag.length;
                    } else {
                        searchPos = sIdx + startTag.length;
                    }
                }

                if (!lastSignal || lastSignalStart === -1) {
                    return JSON.stringify({ isToday: false, signal: '' });
                }

                // 3. Check for day divider "Today" in body text
                // Search for occurrences of \bToday\b outside the signal content itself
                var todayRegex = /\bToday\b/gi;
                var match;
                var dividerTodayIdx = -1;
                while ((match = todayRegex.exec(bodyText)) !== null) {
                    var pos = match.index;
                    // Ignore matches that fall inside the signal text itself
                    if (pos >= lastSignalStart && pos <= lastSignalEnd) {
                        continue;
                    }
                    dividerTodayIdx = pos;
                }

                var isToday = false;
                // If "Today" divider exists and the last signal starts AFTER "Today", it's today's signal!
                if (dividerTodayIdx !== -1 && lastSignalStart > dividerTodayIdx) {
                    isToday = true;
                }

                return JSON.stringify({
                    isToday: isToday,
                    signal: lastSignal
                });
            } catch (err) {
                return JSON.stringify({
                    isToday: false,
                    signal: '',
                    error: err.toString()
                });
            }
        })();
    """

    /**
     * Triggers a dedicated scroll-to-bottom on the WebView.
     */
    fun scrollToBottom(webView: WebView, onFinished: (() -> Unit)? = null) {
        val scrollScript = """
            (function() {
                try {
                    window.scrollTo(0, 99999999);
                    if (document.body) document.body.scrollTop = document.body.scrollHeight;
                    if (document.documentElement) document.documentElement.scrollTop = document.documentElement.scrollHeight;
                    var all = document.querySelectorAll('div, main, section, ul');
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        if (el.scrollHeight > el.clientHeight && el.clientHeight > 80) {
                            el.scrollTop = el.scrollHeight;
                        }
                    }
                    var buttons = document.querySelectorAll('button, [role="button"], a');
                    for (var b = 0; b < buttons.length; b++) {
                        var btn = buttons[b];
                        var aria = (btn.getAttribute('aria-label') || '').toLowerCase();
                        var title = (btn.getAttribute('title') || '').toLowerCase();
                        var text = (btn.innerText || '').toLowerCase().trim();
                        if (aria.includes('bottom') || aria.includes('down') || aria.includes('jump') ||
                            title.includes('bottom') || title.includes('down') ||
                            text.includes('jump to bottom') || text.includes('jump to present') || text.includes('new messages')) {
                            try { btn.click(); } catch(e){}
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript(scrollScript) {
            onFinished?.invoke()
        }
    }

    /**
     * Executes the extraction script on the given WebView and returns the ExtractedSignal.
     */
    fun extractLatestSignal(webView: WebView, onResult: (ExtractedSignal) -> Unit) {
        webView.evaluateJavascript(JS_EXTRACTION_SCRIPT) { rawResult ->
            val result = parseExtractionResult(rawResult)
            Log.d(TAG, "Extracted isToday=${result.isToday}, signalLength=${result.signal.length}")
            onResult(result)
        }
    }

    /**
     * Safely unescapes and parses the JSON returned by evaluateJavascript.
     */
    fun parseExtractionResult(rawResult: String?): ExtractedSignal {
        if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult == "\"\"") {
            return ExtractedSignal(isToday = false, signal = "")
        }
        return try {
            val tokener = JSONTokener(rawResult)
            var obj: JSONObject? = null
            val next = tokener.nextValue()
            if (next is JSONObject) {
                obj = next
            } else if (next is String) {
                obj = JSONObject(next)
            }
            val isToday = obj?.optBoolean("isToday", false) ?: false
            val signal = obj?.optString("signal", "")?.trim() ?: ""
            ExtractedSignal(isToday = isToday, signal = signal)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing extraction result: ${e.message}", e)
            val isTodayFallback = rawResult.contains("\"isToday\":true") || rawResult.contains("\"isToday\": true")
            ExtractedSignal(isToday = isTodayFallback, signal = "")
        }
    }
}
