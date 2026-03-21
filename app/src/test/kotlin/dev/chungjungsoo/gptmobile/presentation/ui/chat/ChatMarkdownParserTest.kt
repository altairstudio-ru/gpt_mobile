package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownParserTest {
    @Test
    fun parseChatMarkdown_inlineMathAndDisplayMath_supportedDelimiters() {
        val parsed = parseChatMarkdown(
            """
            Before ${'$'}a+b${'$'} after.
            
            ${'$'}${'$'}
            c = d
            ${'$'}${'$'}
            
            End with \(x^2\).
            """.trimIndent()
        )

        assertEquals(3, parsed.blocks.size)
        assertEquals(2, parsed.inlineMath.size)

        val firstBlock = parsed.blocks[0] as ChatMarkdownBlock.Markdown
        val displayMath = parsed.blocks[1] as ChatMarkdownBlock.DisplayMath
        val lastBlock = parsed.blocks[2] as ChatMarkdownBlock.Markdown

        assertTrue(firstBlock.content.contains(parsed.inlineMath[0].placeholder))
        assertEquals("c = d", displayMath.tex)
        assertTrue(lastBlock.content.contains(parsed.inlineMath[1].placeholder))
    }

    @Test
    fun parseChatMarkdown_escapedDollar_keepsPlainText() {
        val parsed = parseChatMarkdown("""Price is \$5 and not math.""")

        assertEquals(1, parsed.blocks.size)
        assertTrue((parsed.blocks[0] as ChatMarkdownBlock.Markdown).content.contains("""\$5"""))
        assertTrue(parsed.inlineMath.isEmpty())
    }

    @Test
    fun parseChatMarkdown_codeFenceAndInlineCode_ignoreMathParsing() {
        val parsed = parseChatMarkdown(
            """
            Inline code `${'$'}${'$'}notMath${'$'}${'$'}`
            
            ```kotlin
            val formula = "${'$'}${'$'}alsoNotMath${'$'}${'$'}"
            ```
            
            Actual math: ${'$'}realMath${'$'}
            """.trimIndent()
        )

        assertEquals(1, parsed.inlineMath.size)
        val markdown = (parsed.blocks.single() as ChatMarkdownBlock.Markdown).content

        assertTrue(markdown.contains("`${'$'}${'$'}notMath${'$'}${'$'}`"))
        assertTrue(markdown.contains("${'$'}${'$'}alsoNotMath${'$'}${'$'}"))
        assertTrue(markdown.contains(parsed.inlineMath.single().placeholder))
    }

    @Test
    fun parseChatMarkdown_indentedFence_keepsDisplayMathLiteral() {
        val parsed = parseChatMarkdown(
            """
            - item
              
              ```kotlin
              val env = "${'$'}${'$'}HOME${'$'}${'$'}"
              ```
            """.trimIndent()
        )

        assertTrue(parsed.inlineMath.isEmpty())
        assertEquals(1, parsed.blocks.size)
        val markdown = parsed.blocks.single() as ChatMarkdownBlock.Markdown
        assertTrue(markdown.content.contains("${'$'}${'$'}HOME${'$'}${'$'}"))
    }

    @Test
    fun parseChatMarkdown_bracketDisplayMath_createsStandaloneBlock() {
        val parsed = parseChatMarkdown(
            """
            Start
            \[
            x = y + z
            \]
            End
            """.trimIndent()
        )

        assertEquals(3, parsed.blocks.size)
        assertEquals("x = y + z", (parsed.blocks[1] as ChatMarkdownBlock.DisplayMath).tex)
    }

    @Test
    fun containsInlineMathPlaceholder_withoutToken_returnsFalse() {
        assertFalse(containsInlineMathPlaceholder("plain markdown"))
    }
}
