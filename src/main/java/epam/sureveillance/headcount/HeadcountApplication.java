package epam.sureveillance.headcount;

import org.opencv.core.Core;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HeadcountApplication {
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // Load OpenCV
		SpringApplication.run(HeadcountApplication.class, args);
	}
}