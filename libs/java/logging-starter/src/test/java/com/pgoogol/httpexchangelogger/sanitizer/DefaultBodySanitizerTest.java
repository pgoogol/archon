package com.pgoogol.httpexchangelogger.sanitizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultBodySanitizerTest {

    private final BodySanitizer json = mock(BodySanitizer.class);
    private final BodySanitizer xml = mock(BodySanitizer.class);
    private final BodySanitizer form = mock(BodySanitizer.class);

    private final DefaultBodySanitizer sanitizer = new DefaultBodySanitizer(json, xml, form);

    @Test
    void sanitize_whenContentTypeIsJson_delegatesToJsonSanitizer() {

        // given
        when(json.sanitize("body", "application/json")).thenReturn("json-out");

        // when
        String result = sanitizer.sanitize("body", "application/json");

        // then
        assertThat(result).isEqualTo("json-out");
        verify(json).sanitize("body", "application/json");
        verifyNoInteractions(xml);
        verifyNoInteractions(form);
    }

    @Test
    void sanitize_whenContentTypeIsXml_delegatesToXmlSanitizer() {

        // given
        when(xml.sanitize(eq("body"), any())).thenReturn("xml-out");

        // when
        String result = sanitizer.sanitize("body", "application/xml");

        // then
        assertThat(result).isEqualTo("xml-out");
        verify(xml).sanitize("body", "application/xml");
        verifyNoInteractions(json);
        verifyNoInteractions(form);
    }

    @Test
    void sanitize_whenContentTypeIsForm_delegatesToFormSanitizer() {

        // given
        when(form.sanitize(eq("body"), any())).thenReturn("form-out");

        // when
        String result = sanitizer.sanitize("body", "application/x-www-form-urlencoded");

        // then
        assertThat(result).isEqualTo("form-out");
        verify(form).sanitize("body", "application/x-www-form-urlencoded");
        verifyNoInteractions(json);
        verifyNoInteractions(xml);
    }

    @Test
    void sanitize_whenContentTypeIsPlainTextOrUnknown_returnsBodyUntouched() {

        // given / when
        String text = sanitizer.sanitize("hello", "text/plain");
        String unknown = sanitizer.sanitize("payload", "application/octet-stream");

        // then
        assertThat(text).isEqualTo("hello");
        assertThat(unknown).isEqualTo("payload");
        verifyNoInteractions(json);
        verifyNoInteractions(xml);
        verifyNoInteractions(form);
    }

    @Test
    void sanitize_whenContentTypeIsVendorJson_dispatchesToJson() {

        // given
        when(json.sanitize(eq("body"), any())).thenReturn("out");

        // when
        String result = sanitizer.sanitize("body", "application/hal+json;charset=utf-8");

        // then
        assertThat(result).isEqualTo("out");
        verify(json).sanitize("body", "application/hal+json;charset=utf-8");
    }

}
