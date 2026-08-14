package com.pgoogol.httpexchangelogger.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRequestIdProviderTest {

    private final DefaultRequestIdProvider provider = new DefaultRequestIdProvider();

    @Test
    void getOrCreateRequestId_whenIncomingHeaderPresent_returnsAndEchoesIt() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "external-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = provider.getOrCreateRequestId(request, response);

        // then
        assertThat(result).isEqualTo("external-id");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("external-id");
    }

    @Test
    void getOrCreateRequestId_whenHeaderMissing_generatesUuidAndSetsResponseHeader() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = provider.getOrCreateRequestId(request, response);

        // then
        assertThat(result).isNotBlank();
        UUID.fromString(result);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(result);
    }

    @Test
    void getOrCreateRequestId_whenResponseHeaderAlreadySet_preservesIt() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "preset");

        // when
        String result = provider.getOrCreateRequestId(request, response);

        // then
        assertThat(result).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("preset");
    }

    @Test
    void getOrCreateRequestId_whenHeaderContainsCrlfInjection_rejectsItAndGeneratesUuid() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc\r\nSet-Cookie: evil=1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = provider.getOrCreateRequestId(request, response);

        // then
        assertThat(result).doesNotContain("\r").doesNotContain("\n").doesNotContain("Set-Cookie");
        UUID.fromString(result);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(result);
    }

    @Test
    void getOrCreateRequestId_whenHeaderIsOverlong_rejectsItAndGeneratesUuid() {

        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "a".repeat(5000));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = provider.getOrCreateRequestId(request, response);

        // then
        UUID.fromString(result);
    }

}
