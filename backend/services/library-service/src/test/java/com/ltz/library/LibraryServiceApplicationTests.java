package com.ltz.library;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// JWKS adresi yalnız yer tutucuyu çözmek içindir; bağlantı kurulmaz (-Pci dışı koşuda AUTH_SERVICE_URL ortamda olmayabilir).
@SpringBootTest(properties = "AUTH_SERVICE_URL=http://127.0.0.1:9")
class LibraryServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
