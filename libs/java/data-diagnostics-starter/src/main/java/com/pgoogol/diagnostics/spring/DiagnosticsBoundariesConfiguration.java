package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Granice jednostek pracy poza HTTP. Każda jest osobnym beanem i działa tylko wtedy, gdy
 * aplikacja ma jej bibliotekę: zadania {@code @Async}/{@code @Scheduled} zawsze, Kafka,
 * RabbitMQ i Spring Batch warunkowo.
 */
@Configuration(proxyBeanMethods = false)
class DiagnosticsBoundariesConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TaskBoundary taskBoundary(ObjectProvider<DiagnosticsEngine> engine) {

        return new TaskBoundary(engine::getObject);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RecordInterceptor.class)
    static class KafkaBoundaryConfiguration {

        @Bean
        @ConditionalOnMissingBean(RecordInterceptor.class)
        KafkaRecordBoundary kafkaRecordBoundary(ObjectProvider<DiagnosticsEngine> engine) {

            return new KafkaRecordBoundary(engine::getObject);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AbstractRabbitListenerContainerFactory.class)
    static class RabbitBoundaryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RabbitMessageBoundary rabbitMessageBoundary(ObjectProvider<DiagnosticsEngine> engine) {

            return new RabbitMessageBoundary(engine::getObject);
        }

        @Bean
        static RabbitBoundaryPostProcessor rabbitBoundaryPostProcessor(ObjectProvider<RabbitMessageBoundary> boundary) {

            return new RabbitBoundaryPostProcessor(boundary);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ChunkListener.class)
    static class BatchBoundaryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        BatchChunkBoundary batchChunkBoundary(ObjectProvider<DiagnosticsEngine> engine) {

            return new BatchChunkBoundary(engine::getObject);
        }

        @Bean
        static BatchChunkBoundaryPostProcessor batchChunkBoundaryPostProcessor(
            ObjectProvider<BatchChunkBoundary> boundary) {

            return new BatchChunkBoundaryPostProcessor(boundary);
        }
    }
}
