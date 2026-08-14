package com.pgoogol.httpexchangelogger.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BodyTruncatorTest {

    @Test
    void truncate_whenBodyBelowLimit_returnsUntruncated() {

        // given
        String body = "hello";

        // when
        BodyTruncator.TruncationResult result = BodyTruncator.truncate(body, 10);

        // then
        assertThat(result.value()).isEqualTo("hello");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void truncate_whenBodyExceedsLimit_returnsTruncatedAtLimit() {

        // given
        String body = "hello world";

        // when
        BodyTruncator.TruncationResult result = BodyTruncator.truncate(body, 5);

        // then
        assertThat(result.value()).isEqualTo("hello");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void truncate_whenBodyIsNull_returnsNullUntruncated() {

        // when
        BodyTruncator.TruncationResult result = BodyTruncator.truncate(null, 5);

        // then
        assertThat(result.value()).isNull();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void truncate_whenLimitIsZero_returnsEmptyTruncated() {

        // given
        String body = "abc";

        // when
        BodyTruncator.TruncationResult result = BodyTruncator.truncate(body, 0);

        // then
        assertThat(result.value()).isEmpty();
        assertThat(result.truncated()).isTrue();
    }

}
