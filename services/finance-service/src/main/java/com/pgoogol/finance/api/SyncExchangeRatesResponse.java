package com.pgoogol.finance.api;

import java.time.LocalDate;
import java.util.List;

public record SyncExchangeRatesResponse(LocalDate from, LocalDate to, int savedCount,
                                        List<String> codes) {

}
