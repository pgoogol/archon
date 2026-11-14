package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import com.pgoogol.diagnostics.core.UnitOfWorkBoundary;
import com.pgoogol.diagnostics.core.UnitOfWorkScope;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.Chunk;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Granica chunka Spring Batch: jedna jednostka pracy na chunk, nazwana krokiem
 * ({@code chunk importStep}). N+1 ma sens w obrębie chunka, nie całego kroku, który
 * potrafi wykonać miliony zapytań z założenia.
 *
 * <p>Obsługuje oba style kroków Batch 6. Krok {@code ChunkOrientedStep} woła słuchacza po
 * odczycie pozycji, więc jednostka obejmuje przetwarzanie i zapis, bez zapytań czytnika.
 * Krok oparty na tasklecie woła go przed odczytem, więc jednostka obejmuje cały chunk.
 * Krok wielowątkowy przetwarza pozycje w innych wątkach i ich zapytania do jednostki
 * nie trafiają.</p>
 */
public class BatchChunkBoundary implements ChunkListener<Object, Object>, UnitOfWorkBoundary {

    private static final String UNKNOWN_STEP = "(krok)";

    private final Supplier<DiagnosticsEngine> engine;

    private final ThreadLocal<UnitOfWorkScope> open = new ThreadLocal<>();

    public BatchChunkBoundary(Supplier<DiagnosticsEngine> engine) {

        this.engine = Objects.requireNonNull(engine, "dostęp do silnika jest wymagany");
    }

    @Override
    public UnitOfWorkType type() {

        return UnitOfWorkType.BATCH_CHUNK;
    }

    @Override
    public void beforeChunk(Chunk<Object> chunk) {

        String step = Optional.ofNullable(StepSynchronizationManager.getContext())
            .map(StepContext::getStepName)
            .orElse(UNKNOWN_STEP);
        begin(step);
    }

    @Override
    public void afterChunk(Chunk<Object> chunk) {

        end();
    }

    @Override
    public void onChunkError(Exception exception, Chunk<Object> chunk) {

        end();
    }

    @Override
    public void beforeChunk(ChunkContext context) {

        begin(context.getStepContext().getStepName());
    }

    @Override
    public void afterChunk(ChunkContext context) {

        end();
    }

    @Override
    public void afterChunkError(ChunkContext context) {

        end();
    }

    private void begin(String step) {

        end();
        UnitOfWorkScope scope = engine.get().open("chunk " + step, type());
        open.set(scope);
    }

    private void end() {

        UnitOfWorkScope scope = open.get();
        if (Objects.nonNull(scope)) {

            open.remove();
            scope.close();
        }
    }
}
