package epam.sureveillance.headcount.controller;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

@RestController
class HeadcountController {
    private final CascadeClassifier faceDetector;
    private final VideoCapture capture;

    public HeadcountController() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // Ensure OpenCV loads

        // Load Haar Cascade correctly by saving InputStream to a temporary file
        InputStream cascadeStream = getClass().getClassLoader().getResourceAsStream("static/haarcascade_frontalface_alt.xml");
        if (cascadeStream == null) {
            throw new RuntimeException("Error: Cascade file not found!");
        }

        // Create a temporary file to load the classifier
        File tempFile = null;
        try {
            tempFile = File.createTempFile("haarcascade", ".xml");
            tempFile.deleteOnExit(); // Ensure it is deleted when the application exits

            // Write the InputStream data to the temporary file
            try (OutputStream outStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = cascadeStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, length);
                }
            }

            // Load the classifier from the temporary file
            this.faceDetector = new CascadeClassifier(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error writing cascade file to temporary location", e);
        }

        // Check if the classifier is loaded properly
        if (this.faceDetector.empty()) {
            throw new RuntimeException("Error: Cascade classifier not loaded!");
        }

        // Initialize webcam and check if accessible
        this.capture = new VideoCapture(0);
        if (!this.capture.isOpened()) {
            throw new RuntimeException("Error: Webcam not accessible!");
        }
    }

    @GetMapping("/capture")
    public String captureFrame() throws IOException {
        Mat frame = new Mat();
        capture.read(frame);

        if (frame.empty()) {
            return "Error: No frame captured!";
        }

        // Detect faces
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(frame, faces);
        int headCount = faces.toArray().length;

        // Draw rectangles around detected heads
        for (Rect rect : faces.toArray()) {
            Imgproc.rectangle(frame, new Point(rect.x, rect.y),
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(0, 255, 0), 2);
        }

        // Convert Mat to BufferedImage
        BufferedImage image = matToBufferedImage(frame);

        // Convert BufferedImage to Base64 String
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

        return "{\"headcount\": " + headCount + ", \"image\": \"data:image/jpg;base64," + base64Image + "\"}";
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int width = mat.width();
        int height = mat.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = new byte[width * height * (int) mat.elemSize()];
        mat.get(0, 0, data);
        image.getRaster().setDataElements(0, 0, width, height, data);
        return image;
    }
}
