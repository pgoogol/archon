package com.pgoogol.llm;

import com.pgoogol.llm.exception.LlmMessages;
import com.pgoogol.llm.exception.LlmUnsupportedInputException;

/**
 * Zużycie tokenów pojedynczego wywołania. Tokeny z cache liczone są osobno, bo
 * kosztują inaczej niż zwykłe wejście — bez tego rozdziału szacunek kosztu przy
 * długim prompcie systemowym rozjeżdża się o rząd wielkości.
 */
public record LlmUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

    private static final LlmUsage NONE = new LlmUsage(0, 0, 0, 0);

    public LlmUsage {

        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0) {

            throw new LlmUnsupportedInputException(LlmMessages.NEGATIVE_USAGE);
        }
    }

    public static LlmUsage none() {

        return NONE;
    }

    public static LlmUsage of(long inputTokens, long outputTokens) {

        return new LlmUsage(inputTokens, outputTokens, 0, 0);
    }

    public long totalTokens() {

        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }
}
