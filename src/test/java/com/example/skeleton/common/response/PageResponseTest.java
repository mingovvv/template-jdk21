package com.example.skeleton.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void allWrapsTheFullListAsASinglePage() {
        PageResponse<String> response = PageResponse.all(List.of("a", "b", "c"));

        assertThat(response.items()).containsExactly("a", "b", "c");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void ofComputesTotalPagesAndHasNext() {
        PageResponse<String> middle = PageResponse.of(List.of("a", "b"), 2, 2, 5);

        assertThat(middle.totalPages()).as("5건 / 페이지당 2건 = 3페이지").isEqualTo(3);
        assertThat(middle.hasNext()).isTrue();

        PageResponse<String> last = PageResponse.of(List.of("e"), 3, 2, 5);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void exactDivisionDoesNotCreateAPhantomPage() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 2, 2, 4);

        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void emptyListIsAValidPage() {
        PageResponse<String> response = PageResponse.all(List.of());

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void itemsAreImmutable() {
        PageResponse<String> response = PageResponse.all(List.of("a"));

        assertThatThrownBy(() -> response.items().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }
}
