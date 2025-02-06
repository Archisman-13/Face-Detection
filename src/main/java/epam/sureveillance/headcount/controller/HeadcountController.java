package epam.sureveillance.headcount.controller;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

@RestController
class HeadcountController {
    private final VideoCapture capture;

    // YOLO Model
    private final Net yoloNet;

    // Class names (for filtering 'person')
    private final List<String> classNames;

    public HeadcountController() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // Ensure OpenCV loads

        // Load YOLO model (with absolute paths)
        String cfgPath = "C:\\Users\\archisman_das\\Downloads\\yolov3.cfg";
        String weightsPath = "C:\\Users\\archisman_das\\Downloads\\yolov3.weights";
        this.yoloNet = Dnn.readNetFromDarknet(cfgPath, weightsPath);

        // Load the class names (the 'coco.names' file contains the names of all the detected objects)
        this.classNames = loadClassNames("C:\\Users\\archisman_das\\Downloads\\coco.names");  // Replace with the absolute path to coco.names

        // Open external camera (Change index if needed)
        this.capture = new VideoCapture(0);
        if (!this.capture.isOpened()) {
            throw new RuntimeException("Error: External Webcam not accessible!");
        }
    }

    @GetMapping("/capture")
    public String captureFrame() throws IOException {
        Mat frame = new Mat();
        capture.read(frame);

        if (frame.empty()) {
            return "Error: No frame captured!";
        }

        // Detect objects using YOLO
        List<Rect> yoloDetections = detectWithYolo(frame);

        int headCount = yoloDetections.size();

        // Draw rectangles around detected objects (persons only)
        for (Rect rect : yoloDetections) {
            Imgproc.rectangle(frame, new Point(rect.x, rect.y),
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(0, 255, 0), 2);  // Green color for persons
        }

        // Convert Mat to BufferedImage
        BufferedImage image = matToBufferedImage(frame);

        // Convert BufferedImage to Base64 String
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

        return "{\"headcount\": " + headCount + ", \"image\": \"data:image/jpg;base64," + base64Image + "\"}";
    }

    private List<Rect> detectWithYolo(Mat frame) {
        List<Rect> boxes = new ArrayList<>();
        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255, new Size(416, 416), new Scalar(0, 0, 0), true, false);

        yoloNet.setInput(blob);
        List<Mat> detections = new ArrayList<>();
        yoloNet.forward(detections, yoloNet.getUnconnectedOutLayersNames());

        // Iterate over each detection and filter based on the 'person' class
        for (Mat detection : detections) {
            for (int i = 0; i < detection.rows(); i++) {
                double confidence = detection.get(i, 4)[0];
                if (confidence > 0.99) {
                    // Get class index for the detected object (classId)
                    int classId = (int) detection.get(i, 5)[0];  // The class ID starts from index 5 in YOLO output
                    System.out.println(classId);
                    // Only process 'person' class (classId 0 corresponds to "person" in COCO dataset)
                    if (classId == 0) {
                        // Get bounding box coordinates for the detected object
                        int x = (int) (detection.get(i, 0)[0] * frame.cols());
                        int y = (int) (detection.get(i, 1)[0] * frame.rows());
                        int width = (int) (detection.get(i, 2)[0] * frame.cols());
                        int height = (int) (detection.get(i, 3)[0] * frame.rows());
                        boxes.add(new Rect(x, y, width, height));  // Add the bounding box for 'person'
                    }
                }
            }
        }

        return boxes;
    }

    private List<String> loadClassNames(String path) {
        List<String> classNames = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                classNames.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classNames;
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
