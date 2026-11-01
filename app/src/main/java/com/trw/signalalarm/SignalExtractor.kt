package com.trw.signalalarm

import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

data class SignalExtractionResult(
    val latestSignal: String,
    val latestReaction: Int,
    val prevSignal: String,
    val prevReaction: Int,
    val totalMessagesFound: Int
)

object SignalExtractor {
    private const val TAG = "SignalExtractor"

    /**
     * JavaScript snippet that:
     * 1. Auto-scrolls all scrollable chat containers to the bottom and clicks any jump-to-bottom buttons.
     * 2. Finds all messages starting with 'Portfolio Signal Update'.
     * 3. For the last 2 messages (previous and latest):
     *    - Extracts the clean signal text (from 'Portfolio Signal Update' to before '@I Understand The Signals').
     *    - Extracts the FIRST reaction count (firstReaction, integer >= 0) in the footer area.
     * 4. Returns JSON:
     *    {"latestSignal": "...", "latestReaction": 671, "prevSignal": "...", "prevReaction": 936, "totalMessagesFound": N}
     */
    const val JS_EXTRACTION_SCRIPT = """
        (function() {
            try {
                // 1. Auto-scroll chat container to the bottom
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

                // Click jump-to-bottom / down-arrow buttons if present
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

                // 2. Read innerText and locate all "Portfolio Signal Update" messages
                var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
                if (!bodyText) {
                    return JSON.stringify({
                        latestSignal: '',
                        latestReaction: 0,
                        prevSignal: '',
                        prevReaction: 0,
                        totalMessagesFound: 0
                    });
                }

                var startTag = 'Portfolio Signal Update';
                var endTag = '@I Understand The Signals';
                var msgPositions = [];
                var searchPos = 0;

                while (true) {
                    var sIdx = bodyText.indexOf(startTag, searchPos);
                    if (sIdx === -1) break;
                    msgPositions.push(sIdx);
                    searchPos = sIdx + startTag.length;
                }

                var total = msgPositions.length;
                if (total === 0) {
                    return JSON.stringify({
                        latestSignal: '',
                        latestReaction: 0,
                        prevSignal: '',
                        prevReaction: 0,
                        totalMessagesFound: 0
                    });
                }

                // Helper to parse clean signal text and the first reaction count
                function parseMessage(startIdx, nextStartIdx) {
                    var chunk = (nextStartIdx !== -1)
                        ? bodyText.substring(startIdx, nextStartIdx)
                        : bodyText.substring(startIdx, startIdx + 5000);

                    var eIdx = chunk.indexOf(endTag);
                    var cleanSignal = '';
                    var footer = '';

                    if (eIdx !== -1) {
                        var rawSignal = chunk.substring(0, eIdx);
                        cleanSignal = rawSignal.replace(/\s+/g, ' ').trim();
                        footer = chunk.substring(eIdx + endTag.length);
                    } else {
                        cleanSignal = chunk.replace(/\s+/g, ' ').trim();
                    }

                    // Extract first reaction number from footer
                    var firstReaction = 0;
                    if (footer) {
                        var searchArea = footer;
                        var markerIdx = -1;
                        var markers = ['2024-2026', '2024 - 2026', 'RSPS Explainer', 'Updates'];
                        for (var m = 0; m < markers.length; m++) {
                            var mPos = footer.indexOf(markers[m]);
                            if (mPos !== -1) {
                                var endM = mPos + markers[m].length;
                                if (endM > markerIdx) {
                                    markerIdx = endM;
                                }
                            }
                        }
                        if (markerIdx !== -1) {
                            searchArea = footer.substring(markerIdx);
                        }

                        // Match positive integers (excluding potential years like 2020..2035 and colons in timestamps)
                        var numRegex = /(?:^|[^\d:\/\-\.])(\d{1,6})(?=[^\d:\/\-\.]|$)/g;
                        var match;
                        while ((match = numRegex.exec(searchArea)) !== null) {
                            var val = parseInt(match[1], 10);
                            if (!isNaN(val) && val >= 0 && (val < 2020 || val > 2035)) {
                                firstReaction = val;
                                break;
                            }
                        }
                    }

                    return {
                        signal: cleanSignal,
                        reaction: firstReaction
                    };
                }

                var latestInfo = { signal: '', reaction: 0 };
                var prevInfo = { signal: '', reaction: 0 };

                if (total >= 1) {
                    var latestStart = msgPositions[total - 1];
                    latestInfo = parseMessage(latestStart, -1);
                }

                if (total >= 2) {
                    var prevStart = msgPositions[total - 2];
                    var latestStart2 = msgPositions[total - 1];
                    prevInfo = parseMessage(prevStart, latestStart2);
                }

                return JSON.stringify({
                    latestSignal: latestInfo.signal,
                    latestReaction: latestInfo.reaction,
                    prevSignal: prevInfo.signal,
                    prevReaction: prevInfo.reaction,
                    totalMessagesFound: total
                });
            } catch (err) {
                return JSON.stringify({
                    latestSignal: '',
                    latestReaction: 0,
                    prevSignal: '',
                    prevReaction: 0,
                    totalMessagesFound: 0,
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
     * Executes the extraction script on the given WebView and returns the SignalExtractionResult.
     */
    fun extractLatestSignal(webView: WebView, onResult: (SignalExtractionResult) -> Unit) {
        webView.evaluateJavascript(JS_EXTRACTION_SCRIPT) { rawResult ->
            val result = parseExtractionResult(rawResult)
            Log.d(TAG, "Extracted total=${result.totalMessagesFound}, latestReaction=${result.latestReaction}, prevReaction=${result.prevReaction}")
            onResult(result)
        }
    }

    /**
     * Safely unescapes and parses the JSON returned by evaluateJavascript.
     */
    fun parseExtractionResult(rawResult: String?): SignalExtractionResult {
        if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult == "\"\"") {
            return SignalExtractionResult("", 0, "", 0, 0)
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
            SignalExtractionResult(
                latestSignal = obj?.optString("latestSignal", "")?.trim() ?: "",
                latestReaction = obj?.optInt("latestReaction", 0) ?: 0,
                prevSignal = obj?.optString("prevSignal", "")?.trim() ?: "",
                prevReaction = obj?.optInt("prevReaction", 0) ?: 0,
                totalMessagesFound = obj?.optInt("totalMessagesFound", 0) ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing extraction result: ${e.message}", e)
            SignalExtractionResult("", 0, "", 0, 0)
        }
    }
}
