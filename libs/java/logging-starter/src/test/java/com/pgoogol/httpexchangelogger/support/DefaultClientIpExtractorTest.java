package com.pgoogol.httpexchangelogger.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultClientIpExtractorTest {

    private final DefaultClientIpExtractor extractor = new DefaultClientIpExtractor();

    @Test
    void extract_whenXForwardedForPresent_returnsFirstAddress() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void extract_whenOnlyXRealIpPresent_returnsXRealIp() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "192.168.0.1");
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("192.168.0.1");
    }

    @Test
    void extract_whenNoForwardingHeaders_returnsRemoteAddr() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void extract_whenForwardedForStartsWithUnknownToken_returnsFirstRealAddress() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown, 203.0.113.7");
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void extract_whenForwardedForHasEmptyLeadingSegment_returnsFirstRealAddress() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", ", 203.0.113.7");
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void extract_whenForwardedForHasOnlyUnknown_returnsRemoteAddr() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("127.0.0.1");

        // when / then
        assertThat(extractor.extract(request)).isEqualTo("127.0.0.1");
    }

}
