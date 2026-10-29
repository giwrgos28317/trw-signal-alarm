package com.trw.signalalarm

import android.util.Log
import android.webkit.WebView
import org.json.JSONTokener

object SignalExtractor {
    private const val TAG = "SignalExtractor"

    /**
     * JavaScript snippet that extracts the latest portfolio signal according to specs:
     * - Reads document.body.innerText
     * - Finds all text blocks starting with 'Portfolio Signal Update' and ending immediately before '@I Understand The Signals'
     * - Normalizes whitespace and newlines (replace(/\s+/g, ' ').trim())
     * - Discards emoji reaction counts below '@I Understand The Signals'
     * - Returns the LAST (most recent) clean signal found on the page
     */
    const val JS_EXTRACTION_SCRIPT = """
        (function() {
            try {
                var text = (document.body && document.body.innerText) ? document.body.innerText : '';
                if (!text) {
                    return '';
                }
                var startTag = 'Portfolio Signal Update';
                var endTag = '@I Understand The Signals';
                var signals = [];
                var searchPos = 0;

                while (true) {
                    var startIdx = text.indexOf(startTag, searchPos);
                    if (startIdx === -1) {
                        break;
                    }
                    var endIdx = text.indexOf(endTag, startIdx + startTag.length);
                    if (endIdx !== -1) {
                        var rawSignal = text.substring(startIdx, endIdx);
                        var normalized = rawSignal.replace(/\s+/g, ' ').trim();
                        if (normalized.length > 0) {
                            signals.push(normalized);
                        }
                        searchPos = endIdx + endTag.length;
                    } else {
                        searchPos = startIdx + startTag.length;
                    }
                }

                if (signals.length > 0) {
                    return signals[signals.length - 1];
                }
                return '';
            } catch (err) {
                return '';
            }
        })();
    """

    /**
     * Executes the extraction script on the given WebView and returns the parsed signal string.
     */
    fun extractLatestSignal(webView: WebView, onResult: (String) -> Unit) {
        webView.evaluateJavascript(JS_EXTRACTION_SCRIPT) { rawResult ->
            val cleaned = parseJsResult(rawResult)
            Log.d(TAG, "Extracted signal length=${cleaned.length}: $cleaned")
            onResult(cleaned)
        }
    }

    /**
     * Safely unescapes the JSON string returned by evaluateJavascript.
     */
    fun parseJsResult(rawResult: String?): String {
        if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult == "\"\"") {
            return ""
        }
        return try {
            val tokener = JSONTokener(rawResult)
            val parsed = tokener.nextValue()
            if (parsed is String) {
                parsed.trim()
            } else {
                parsed?.toString()?.trim() ?: ""
            }
        } catch (e: Exception) {
            // Fallback manual unescape
            var s = rawResult.trim()
            if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                s = s.substring(1, s.length - 1)
            }
            s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", " ")
                .replace("\\\\", "\\")
                .trim()
        }
    }
}
