## Getting Started

### Prerequisites

- Android Studio (latest recommended)
- Android device or emulator (API 21+)
- [TensorFlow Lite MoveNet model](https://www.tensorflow.org/lite/models/pose_estimation/overview) (already included as `movenet-lightning.tflite`)

### Build & Run

1. **Clone the repository:**

   ```sh
   git clone https://github.com/baeshiii/CapstoneProject.git
   cd CapstoneProject
   ```

2. **Open in Android Studio:**

   - Open the `CapstoneProject` folder in Android Studio.

3. **Build the project:**

   - Let Gradle sync and build the project.

4. **Run the app:**
   - Connect your Android device or start an emulator.
   - Click "Run" in Android Studio.

### Usage

- **Camera Mode:**  
  Launches the camera, detects pose in real-time, and provides squat feedback.
- **Video Mode:**  
  Select a video file to analyze pose and squats frame-by-frame.

### Key Classes

- `PoseProcessor`: Handles running the MoveNet model and smoothing keypoints.
- `SquatDepthAnalyzer`: Determines squat phase and calculates joint angles.
- `OverlayView`: Draws keypoints and skeleton overlay on frames.
- `VideoPoseProcessor`: Processes pose detection on video files.

### Customization

- **Model:**  
  You can swap `movenet-lightning.tflite` with another compatible TFLite pose model.
- **Feedback Logic:**  
  Modify `SquatDepthAnalyzer` and `FeedbackUtils` for different exercise feedback.

## License

This project is for educational and research purposes.  
For commercial use, check the licenses of TensorFlow Lite and the MoveNet model.

## Acknowledgements

- [TensorFlow Lite MoveNet](https://www.tensorflow.org/lite/models/pose_estimation/overview)
- [Google ML Kit](https://developers.google.com/ml-kit/vision/pose-detection)

---

_Capstone Project – Real-time Pose Estimation and Squat Feedback_
