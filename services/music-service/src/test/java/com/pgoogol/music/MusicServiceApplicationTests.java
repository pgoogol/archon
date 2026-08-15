package com.pgoogol.music;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class MusicServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
