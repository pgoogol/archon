package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.Test;

@FixtureCases
public class ParallelFirstCases {

    @Test
    void meetsSecondClass() throws Exception {

        ParallelMeeting.await();
    }
}
