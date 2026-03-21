package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.roundToInt

private const val MATH_JAX_BASE_URL = "file:///android_asset/mathjax/"
private const val MATH_JAX_RENDER_RETRY_DELAY_MILLIS = 50L
private const val MAX_MATH_JAX_RENDER_RETRIES = 60

private const val MATH_JAX_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
html, body {
  margin: 0;
  padding: 0;
  background: transparent;
  overflow: hidden;
}
body {
  display: inline-block;
}
body.display {
  display: block;
  width: 100%;
}
#root {
  display: inline-block;
  margin: 0;
  padding: 2px 0;
}
#root.display {
  display: block;
  width: 100%;
  text-align: center;
  padding: 8px 0;
}
#root pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: monospace;
}
mjx-container[jax="SVG"] {
  display: inline-block;
  margin: 0 !important;
  max-width: 100%;
}
#root.display mjx-container[jax="SVG"] {
  display: block;
  text-align: center;
}
svg {
  overflow: visible;
  max-width: 100%;
}
</style>
<script>
window.mathJaxReady = false;
window.MathJax = {
  startup: {
    typeset: false,
    ready: () => {
      MathJax.startup.defaultReady();
      window.mathJaxReady = true;
    }
  },
  svg: {
    fontCache: 'none'
  }
};
</script>
<script defer src="tex-svg.js"></script>
<script>
window.renderMath = function(expression, displayMode, textColor, fontSizePx) {
  if (!window.mathJaxReady || !window.MathJax || typeof MathJax.tex2svg !== 'function') {
    return 'loading';
  }

  const root = document.getElementById('root');
  document.body.className = displayMode ? 'display' : 'inline';
  document.body.style.color = textColor;
  document.body.style.fontSize = fontSizePx + 'px';
  root.className = displayMode ? 'display' : 'inline';

  try {
    const node = MathJax.tex2svg(expression, { display: displayMode });
    root.replaceChildren(node);
  } catch (error) {
    const fallback = document.createElement(displayMode ? 'pre' : 'span');
    fallback.textContent = displayMode ? '\\[' + expression + '\\]' : '\\(' + expression + '\\)';
    root.replaceChildren(fallback);
  }

  const measuredNode = root.firstElementChild || root;
  const rect = measuredNode.getBoundingClientRect();
  const width = Math.max(Math.ceil(rect.width), Math.ceil(root.scrollWidth), 1);
  const height = Math.max(Math.ceil(rect.height), Math.ceil(root.scrollHeight), 1);
  return width + '|' + height;
};
</script>
</head>
<body>
<div id="root" class="inline"></div>
</body>
</html>
"""

private data class MathRenderRequest(
    val tex: String,
    val displayMode: Boolean,
    val fontSizePx: Int,
    val textColorCss: String,
    val minimumHeightPx: Int
)

@Composable
internal fun InlineMathView(tex: String) {
    val density = LocalDensity.current
    val textColor = LocalContentColor.current
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize
        .takeIf { it.type != TextUnitType.Unspecified }
        ?: 16.sp
    val fontSizeCssPx = remember(fontSize, density.fontScale) {
        (fontSize.value * density.fontScale).roundToInt()
    }
    val minimumHeightCssPx = remember(fontSizeCssPx) {
        (fontSizeCssPx * 1.4f).roundToInt()
    }
    val textColorCss = remember(textColor) {
        formatCssColor(textColor)
    }

    MathJaxFormulaView(
        tex = tex,
        displayMode = false,
        fontSizePx = fontSizeCssPx,
        textColorCss = textColorCss,
        minimumHeightPx = minimumHeightCssPx,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
internal fun DisplayMathView(
    tex: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textColor = LocalContentColor.current
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize
        .takeIf { it.type != TextUnitType.Unspecified }
        ?: 16.sp
    val fontSizeCssPx = remember(fontSize, density.fontScale) {
        (fontSize.value * density.fontScale).roundToInt()
    }
    val minimumHeightCssPx = remember(tex, fontSizeCssPx) {
        estimateDisplayMathMinimumHeightPx(tex, fontSizeCssPx)
    }
    val textColorCss = remember(textColor) {
        formatCssColor(textColor)
    }
    var measuredHeightCssPx by remember(tex, fontSizeCssPx, textColorCss) {
        mutableIntStateOf(minimumHeightCssPx)
    }

    MathJaxFormulaView(
        tex = tex,
        displayMode = true,
        fontSizePx = fontSizeCssPx,
        textColorCss = textColorCss,
        minimumHeightPx = minimumHeightCssPx,
        modifier = modifier.height(measuredHeightCssPx.dp),
        onMeasured = { heightPx ->
            measuredHeightCssPx = heightPx.coerceAtLeast(minimumHeightCssPx)
        }
    )
}

@Composable
private fun MathJaxFormulaView(
    tex: String,
    displayMode: Boolean,
    fontSizePx: Int,
    textColorCss: String,
    minimumHeightPx: Int,
    modifier: Modifier = Modifier,
    onMeasured: (Int) -> Unit = {}
) {
    val request = remember(tex, displayMode, fontSizePx, textColorCss, minimumHeightPx) {
        MathRenderRequest(
            tex = tex,
            displayMode = displayMode,
            fontSizePx = fontSizePx,
            textColorCss = textColorCss,
            minimumHeightPx = minimumHeightPx
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context -> MathJaxWebView(context) },
        update = { webView ->
            webView.contentDescription = tex
            webView.setRenderRequest(request, onMeasured)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private class MathJaxWebView(context: Context) : WebView(context) {
    private var pageLoaded = false
    private var renderedRequest: MathRenderRequest? = null
    private var pendingRequest: MathRenderRequest? = null
    private var onMeasured: ((Int) -> Unit)? = null
    private var renderRetryCount = 0

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isHorizontalScrollBarEnabled = false
        isLongClickable = false
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setOnLongClickListener { true }

        settings.allowContentAccess = false
        settings.allowFileAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.javaScriptEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                renderRetryCount = 0
                renderPendingRequest()
            }
        }

        loadDataWithBaseURL(
            MATH_JAX_BASE_URL,
            MATH_JAX_HTML,
            "text/html",
            "utf-8",
            null
        )
    }

    fun setRenderRequest(
        request: MathRenderRequest,
        onMeasured: (Int) -> Unit
    ) {
        this.onMeasured = onMeasured
        if (request == renderedRequest && pendingRequest == null) return

        pendingRequest = request
        renderRetryCount = 0
        renderPendingRequest()
    }

    private fun renderPendingRequest() {
        val request = pendingRequest ?: return
        if (!pageLoaded) return

        evaluateJavascript(buildRenderScript(request)) { rawResult ->
            if (rawResult == "\"loading\"") {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            val measuredBounds = parseMeasuredBounds(rawResult)
            if (measuredBounds == null) {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            if (
                request.displayMode &&
                measuredBounds.second <= request.minimumHeightPx &&
                renderRetryCount < MAX_MATH_JAX_RENDER_RETRIES
            ) {
                scheduleRenderRetry()
                return@evaluateJavascript
            }

            renderedRequest = request
            pendingRequest = null
            renderRetryCount = 0

            onMeasured?.invoke(max(measuredBounds.second, request.minimumHeightPx))
        }
    }

    private fun scheduleRenderRetry() {
        if (renderRetryCount >= MAX_MATH_JAX_RENDER_RETRIES) return

        renderRetryCount += 1
        postDelayed(
            { renderPendingRequest() },
            MATH_JAX_RENDER_RETRY_DELAY_MILLIS
        )
    }
}

private fun buildRenderScript(request: MathRenderRequest): String = """
    (function() {
      if (typeof window.renderMath !== 'function') {
        return null;
      }
      return window.renderMath(
        "${escapeJavaScriptString(request.tex)}",
        ${request.displayMode},
        "${request.textColorCss}",
        ${request.fontSizePx}
      );
    })();
""".trimIndent()

private fun parseMeasuredBounds(rawResult: String?): Pair<Int, Int>? {
    val result = rawResult
        ?.removePrefix("\"")
        ?.removeSuffix("\"")
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: return null
    val parts = result.split('|')
    if (parts.size != 2) return null

    val width = parts[0].toIntOrNull() ?: return null
    val height = parts[1].toIntOrNull() ?: return null
    return width to height
}

private fun formatCssColor(color: Color): String {
    val red = (color.red * 255).roundToInt()
    val green = (color.green * 255).roundToInt()
    val blue = (color.blue * 255).roundToInt()
    return "rgba($red, $green, $blue, ${color.alpha})"
}

private fun estimateDisplayMathMinimumHeightPx(
    tex: String,
    fontSizePx: Int
): Int {
    val lineCount = tex.split("\\\\").size.coerceAtLeast(1)
    val containsTallOperators = listOf(
        "\\frac",
        "\\dfrac",
        "\\tfrac",
        "\\sum",
        "\\prod",
        "\\int",
        "\\oint",
        "\\lim",
        "\\begin",
        "\\left",
        "\\right",
        "\\over"
    ).any(tex::contains)
    val baseEmHeight = when {
        lineCount >= 3 -> 7.5f
        lineCount == 2 -> 5.8f
        containsTallOperators -> 4.8f
        else -> 3.2f
    }
    return (fontSizePx * baseEmHeight).roundToInt()
}

private fun escapeJavaScriptString(text: String): String = buildString(text.length) {
    text.forEach { character ->
        append(
            when (character) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\'' -> "\\'"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                '\u2028' -> "\\u2028"
                '\u2029' -> "\\u2029"
                else -> character
            }
        )
    }
}
