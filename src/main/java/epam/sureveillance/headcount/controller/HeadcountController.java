package epam.sureveillance.headcount.controller;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@RestController
public class HeadcountController {
    private final VideoCapture capture;
    private static final String IMAGE_PATH = "src/main/resources/capture/captured_frame.jpg";
    private static final String DETECTIONS_PATH = "src/main/resources/capture/detections/";
    private static final String DETECTED_IMAGE_PATH = DETECTIONS_PATH + "detected_frame.jpg";
    private static final String PYTHON_API_URL = "http://127.0.0.1:5000/detect";

    public HeadcountController() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // Load OpenCV

        // Open webcam
        this.capture = new VideoCapture(0);
        if (!this.capture.isOpened()) {
            throw new RuntimeException("❌ Error: Camera not accessible!");
        }

        // Ensure detection directory exists
        File detectionsDir = new File(DETECTIONS_PATH);
        if (!detectionsDir.exists()) {
            detectionsDir.mkdirs();
        }
    }

    /**
     * Capture a frame and save it to `resources/capture/`
     */
    @PostMapping("/capture")
    public ResponseEntity<String> captureFrame() {
        if (!capture.isOpened()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Camera not accessible\"}");
        }

        Mat frame = new Mat();
        capture.read(frame);

        if (frame.empty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"No frame captured\"}");
        }

        // Ensure directory exists
        File directory = new File("src/main/resources/capture/");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Save the frame as an image
        boolean saved = Imgcodecs.imwrite(IMAGE_PATH, frame);
        if (!saved) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to save image\"}");
        }

        System.out.println("✅ Image successfully saved: " + new File(IMAGE_PATH).getAbsolutePath());
        return ResponseEntity.ok("{\"message\": \"Frame captured successfully\", \"path\": \"" + IMAGE_PATH + "\"}");
    }

    /**
     * Send the captured image to the Python API for headcount detection
     */
    @GetMapping("/headcount")
    public ResponseEntity<String> getHeadcount() throws IOException {
        File file = new File(IMAGE_PATH);

        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"No captured image found\"}");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response = restTemplate.exchange(
                PYTHON_API_URL, HttpMethod.POST, requestEntity, Map.class
        );

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to get response from detection API\"}");
        }

        // Extract headcount and Base64 image from Python response
        Map<String, Object> responseBody = response.getBody();
        int headcount = (int) responseBody.get("headcount");
        String base64Image = (String) responseBody.get("image");

        // Convert Base64 to image and save it
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        Files.write(Paths.get(DETECTED_IMAGE_PATH), imageBytes);

        // Return JSON response with headcount and detected image
        return ResponseEntity.ok("{\"headcount\": " + headcount + ", \"image\": \"data:image/jpg;base64," + base64Image + "\"}");
    }
}
