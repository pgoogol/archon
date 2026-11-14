package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.DiagnosticsEngine;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.batch.infrastructure.item.Chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageAndChunkBoundaryTest {

    private final BoundaryTestSupport support = new BoundaryTestSupport();

    private final DiagnosticsEngine engine = support.engine();

    @Test
    @DisplayName("każdy rekord Kafki daje osobną jednostkę nazwaną tematem")
    void kafka_whenTwoRecords_reportsUnitPerRecord() {

        // given
        KafkaRecordBoundary boundary = new KafkaRecordBoundary(() -> engine);
        ConsumerRecord<Object, Object> first = new ConsumerRecord<>("orders", 0, 1L, "k", "v");
        ConsumerRecord<Object, Object> second = new ConsumerRecord<>("orders", 0, 2L, "k", "v");

        // when
        boundary.intercept(first, null);
        engine.record(DataAccessEventFixtures.read("select 1"));
        boundary.afterRecord(first, null);
        boundary.intercept(second, null);
        boundary.afterRecord(second, null);

        // then
        assertThat(support.described()).containsExactly("message:kafka orders:1", "message:kafka orders:0");
    }

    @Test
    @DisplayName("rekord bez afterRecord nie zostawia otwartej jednostki: zamyka ją następny rekord")
    void kafka_whenAfterRecordMissing_nextRecordClosesPreviousUnit() {

        // given
        KafkaRecordBoundary boundary = new KafkaRecordBoundary(() -> engine);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("orders", 0, 1L, "k", "v");

        // when
        boundary.intercept(record, null);
        boundary.intercept(record, null);
        boundary.afterRecord(record, null);

        // then
        assertThat(support.reported()).hasSize(2);
        assertThat(engine.current()).isEmpty();
    }

    @Test
    @DisplayName("komunikat RabbitMQ daje jednostkę nazwaną kolejką, także gdy słuchacz rzuci wyjątek")
    void rabbit_whenListenerThrows_stillReportsUnitNamedAfterQueue() throws Throwable {

        // given
        RabbitMessageBoundary boundary = new RabbitMessageBoundary(() -> engine);
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("orders.created");
        Message message = new Message(new byte[0], properties);
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {new Object(), message});
        when(invocation.proceed()).thenThrow(new IllegalStateException("awaria słuchacza"));

        // when & then
        assertThatIllegalStateException().isThrownBy(() -> boundary.invoke(invocation));
        assertThat(support.described()).containsExactly("message:rabbit orders.created:0");
    }

    @Test
    @DisplayName("każdy chunk Spring Batch daje osobną jednostkę, także zakończony błędem")
    void batch_whenChunksProcessed_reportsUnitPerChunk() {

        // given
        BatchChunkBoundary boundary = new BatchChunkBoundary(() -> engine);
        Chunk<Object> chunk = new Chunk<>();

        // when
        boundary.beforeChunk(chunk);
        engine.record(DataAccessEventFixtures.read("select 1"));
        boundary.afterChunk(chunk);
        boundary.beforeChunk(chunk);
        boundary.onChunkError(new IllegalStateException("awaria zapisu"), chunk);

        // then
        assertThat(support.described()).containsExactly("batch-chunk:chunk (krok):1", "batch-chunk:chunk (krok):0");
    }
}
