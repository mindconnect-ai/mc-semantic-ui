package ai.mindconnect.ui.javafx.richtext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What an HTMLEditor's document becomes when the form submits it. */
class RichTextFieldRendererTest {

    @Test
    void theBodyOnlyReducedToTheRichTextVocabulary() {
        String document = "<html dir=\"ltr\"><head><style>p{color:red}</style></head>"
                + "<body contenteditable=\"true\"><p><font face=\"Arial\">Hi <b>there</b></font></p>"
                + "<p style=\"color:red\" onclick=\"x()\">x</p><script>alert(1)</script></body></html>";
        assertThat(RichTextFieldRenderer.valueOf(document)).isEqualTo("<p>Hi <b>there</b></p><p>x</p>");
    }

    @Test
    void anEmptyEditorSubmitsNothing() {
        assertThat(RichTextFieldRenderer.valueOf("<html><head></head><body contenteditable=\"true\"></body></html>")).isNull();
        assertThat(RichTextFieldRenderer.valueOf("<html><body><p><br></p></body></html>")).isNull();
        assertThat(RichTextFieldRenderer.valueOf(null)).isNull();
    }

    @Test
    void anImageAloneIsContent() {
        assertThat(RichTextFieldRenderer.valueOf("<body><img src=\"data:image/png;base64,iVBOR\"></body>"))
                .isEqualTo("<img src=\"data:image/png;base64,iVBOR\">");
    }
}
