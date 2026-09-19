import cv2
import numpy as np
import onnxruntime as ort
import os
import sys

# Classes definition (Exact same as Android YoloLabels.kt)
CLASSES = [
    "FEMALE_FACE",               # 0
    "MALE_FACE",                 # 1
    "FEMALE_GENITALIA_COVERED",  # 2
    "FEMALE_GENITALIA_EXPOSED",  # 3
    "BUTTOCKS_COVERED",          # 4
    "BUTTOCKS_EXPOSED",          # 5
    "FEMALE_BREAST_COVERED",     # 6
    "FEMALE_BREAST_EXPOSED",     # 7
    "MALE_BREAST_EXPOSED",       # 8
    "ARMPITS_EXPOSED",           # 9
    "BELLY_EXPOSED",             # 10
    "MALE_GENITALIA_EXPOSED",    # 11
    "ANUS_EXPOSED",              # 12
    "FEET_COVERED",              # 13
    "FEET_EXPOSED",              # 14
    "EYE"                        # 15
]

CENSOR_CLASS_IDS = {3, 5, 7, 11, 12}  # Exposed genitals, buttocks, breasts, anus

def main():
    model_path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "assets", "model.onnx")
    if not os.path.exists(model_path):
        print(f"Error: Model not found at {model_path}")
        return

    print("Loading Android ONNX Runtime model...")
    session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    print("Model loaded successfully!")

    # Find a test image
    test_img_path = r"c:\Users\paixd\Downloads\Beta Blocker 3.67\app_assets\censor_images\Isla 1.png"
    if len(sys.argv) > 1 and os.path.exists(sys.argv[1]):
        test_img_path = sys.argv[1]

    if not os.path.exists(test_img_path):
        print("Please provide an image path to test: python simulate_android_censor.py <image_path>")
        return

    img = cv2.imread(test_img_path)
    if img is None:
        print(f"Failed to read image: {test_img_path}")
        return

    orig_h, orig_w = img.shape[:2]
    print(f"Testing image: {test_img_path} ({orig_w}x{orig_h})")

    # 1. Preprocess: Resize to 320x320 RGB float [0..1]
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (320, 320))
    blob = (resized.astype(np.float32) / 255.0).transpose(2, 0, 1)[None, ...]

    # 2. Run inference
    outputs = session.run(None, {"images": blob})
    raw = outputs[0][0]  # [20, 2100]

    # 3. Post-process (Exact Android YoloDetector logic)
    conf_thresh = 0.35
    boxes = []
    scores = []
    class_ids = []

    for j in range(2100):
        class_scores = raw[4:20, j]
        best_id = int(np.argmax(class_scores))
        best_score = float(class_scores[best_id])

        if best_score >= conf_thresh and best_id in CENSOR_CLASS_IDS:
            cx = float(raw[0, j])
            cy = float(raw[1, j])
            w = float(raw[2, j])
            h = float(raw[3, j])

            x1 = max(0, int((cx - w / 2.0) / 320.0 * orig_w))
            y1 = max(0, int((cy - h / 2.0) / 320.0 * orig_h))
            box_w = int(w / 320.0 * orig_w)
            box_h = int(h / 320.0 * orig_h)

            boxes.append([x1, y1, box_w, box_h])
            scores.append(best_score)
            class_ids.append(best_id)

    indices = cv2.dnn.NMSBoxes(boxes, scores, conf_thresh, 0.45)

    display = img.copy()
    censor_count = 0

    if len(indices) > 0:
        for idx in indices.flatten():
            censor_count += 1
            x, y, w, h = boxes[idx]
            cls_name = CLASSES[class_ids[idx]]
            score = scores[idx]

            # Draw solid black censor rectangle (like Android Overlay)
            cv2.rectangle(display, (x, y), (x + w, y + h), (0, 0, 0), -1)
            cv2.rectangle(display, (x, y), (x + w, y + h), (50, 50, 50), 2)
            cv2.putText(display, f"[CENSORED] {cls_name} ({score:.0%})", (x, max(20, y - 8)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1)

    out_file = os.path.join(os.path.dirname(__file__), "simulation_output.png")
    cv2.imwrite(out_file, display)
    print(f"\n[Result] Total censored areas: {censor_count}")
    print(f"Preview saved to: {out_file}")

if __name__ == "__main__":
    main()
