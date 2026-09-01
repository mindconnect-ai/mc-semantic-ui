package ai.mindconnect.ui.javafx.renderers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code page} is one-based, and these renderers read it as zero-based.
 *
 * <p>Found on a live admin UI whose agent list sends
 * {@code page: 1, size: 10, total: 16} — the first ten of sixteen. The desktop
 * labelled it "2 / 2", enabled Previous on the first page, and asked for page 0
 * when it was pressed. All three came from the same wrong assumption, so they
 * are all tested here against exactly that payload.
 */
class PagersTest {

    @Test
    void theFirstPageOfTheLiveAgentListReadsAsTheFirst() {
        assertThat(Pagers.label(1, 10, 16, "items")).isEqualTo("1 / 2  (16 items)");
        assertThat(Pagers.isFirst(1)).as("page 1 is the first page").isTrue();
        assertThat(Pagers.isLast(1, 10, 16)).isFalse();
    }

    @Test
    void theLastPageIsTheLast() {
        assertThat(Pagers.label(2, 10, 16, "items")).isEqualTo("2 / 2  (16 items)");
        assertThat(Pagers.isFirst(2)).isFalse();
        assertThat(Pagers.isLast(2, 10, 16)).isTrue();
    }

    @Test
    void aPartialLastPageStillCounts() {
        // 16 over 10 is two pages, not one — the remainder is a page of its own.
        assertThat(Pagers.pageCount(10, 16)).isEqualTo(2);
        assertThat(Pagers.pageCount(10, 20)).isEqualTo(2);
        assertThat(Pagers.pageCount(10, 21)).isEqualTo(3);
    }

    @Test
    void anEmptyListIsStillOnePage() {
        // "1 / 0" would be nonsense on screen, and both buttons must be dead.
        assertThat(Pagers.pageCount(10, 0)).isEqualTo(1);
        assertThat(Pagers.label(1, 10, 0, "items")).isEqualTo("1 / 1  (0 items)");
        assertThat(Pagers.isFirst(1)).isTrue();
        assertThat(Pagers.isLast(1, 10, 0)).isTrue();
    }

    @Test
    void aMissingPageSizeDoesNotDivideByZero() {
        assertThat(Pagers.pageCount(0, 16)).isEqualTo(1);
        assertThat(Pagers.isLast(1, 0, 16)).isTrue();
    }
}
