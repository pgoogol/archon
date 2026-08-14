package com.pgoogol.httpexchangelogger.sanitizer;

import java.util.List;
import java.util.Map;

public interface HeaderSanitizer {

    Map<String, List<String>> sanitize(Map<String, List<String>> headers);

}
