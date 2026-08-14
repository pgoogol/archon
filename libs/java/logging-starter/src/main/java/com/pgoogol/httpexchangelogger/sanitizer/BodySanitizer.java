package com.pgoogol.httpexchangelogger.sanitizer;

public interface BodySanitizer {

    String sanitize(String body, String contentType);

}
