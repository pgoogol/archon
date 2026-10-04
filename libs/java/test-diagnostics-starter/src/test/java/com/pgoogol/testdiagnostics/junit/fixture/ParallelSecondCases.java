package com.pgoogol.testdiagnostics.junit.fixture;

import org.junit.jupiter.api.Test;

@FixtureCases
public class ParallelSecondCases {

    @Test
    void meetsFirstClass() throws Exception {

        ParallelMeeting.await();
    }
}
