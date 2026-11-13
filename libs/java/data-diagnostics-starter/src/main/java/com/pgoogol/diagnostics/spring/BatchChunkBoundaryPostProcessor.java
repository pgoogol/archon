package com.pgoogol.diagnostics.spring;

import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Rejestruje {@link BatchChunkBoundary} w każdym kroku Spring Batch, który jest beanem.
 * Batch nie ma globalnej listy słuchaczy chunków, więc granica dochodzi krok po kroku.
 */
class BatchChunkBoundaryPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<BatchChunkBoundary> boundary;

    BatchChunkBoundaryPostProcessor(ObjectProvider<BatchChunkBoundary> boundary) {

        this.boundary = boundary;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object postProcessAfterInitialization(Object bean, String beanName) {

        if (bean instanceof ChunkOrientedStep step) {

            boundary.ifAvailable(listener -> step.registerChunkListener(listener));
        }
        if (bean instanceof TaskletStep step) {

            boundary.ifAvailable(step::registerChunkListener);
        }
        return bean;
    }
}
