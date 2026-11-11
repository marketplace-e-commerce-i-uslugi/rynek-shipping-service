package pl.rynek.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ShippingServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
