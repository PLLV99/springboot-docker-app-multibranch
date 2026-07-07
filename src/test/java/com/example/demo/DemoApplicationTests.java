package com.example.demo;

import com.example.demo.controller.HelloController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private HelloController helloController;

	@Test
	void contextLoads() {
		// ตรวจสอบว่า Spring Context โหลดได้สำเร็จ
		assertThat(helloController).isNotNull();
	}

	@Test
	void applicationStartsSuccessfully() {
		// Test ว่า application สามารถ start ได้
		// ถ้า test นี้ผ่าน แปลว่า Spring Boot application start ได้สำเร็จ
	}

}
