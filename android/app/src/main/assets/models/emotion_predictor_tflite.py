"""
EmotionPredictor (TFLite)
─────────────────────────
TFLite-inference version of the original Keras-based EmotionPredictor.
Same preprocessing, same 3-class (happy/neutral/surprise) renormalization,
same hand-on-chin -> "confuse" logic. Only the model backend changed
(tf.lite.Interpreter instead of a loaded Keras model).

Usage
-----
    predictor = EmotionPredictorTFLite(
        tflite_model_path="emotion_model.tflite",
        hand_model_path="hand_landmarker.task",
    )
    label = predictor.predict(face_crop, face_id)  # -> "neutral"|"happy"|"confuse"|"surprise"|None
    predictor.forget(face_id)                       # no-op, kept for drop-in compatibility
"""

from __future__ import annotations

import numpy as np
import cv2
import mediapipe as mp
import tensorflow as tf


# Original 7-class label order (FER-2013)
_FULL_LABELS = {0: "Angry", 1: "Disgusted", 2: "Fearful",
                3: "Happy", 4: "Neutral", 5: "Sad", 6: "Surprised"}

# Indices we keep, lower-cased to match the pipeline's MOOD_MAP keys
_KEEP_INDICES = {3: "happy", 4: "neutral", 6: "surprise"}

# Hand-on-chin ("thinking pose") -> confuse
_HAND_KEY_POINTS          = (0, 4, 8, 12, 16, 20)  # wrist + all 5 fingertips
_HAND_CHIN_DISTANCE_RATIO = 0.6


class EmotionPredictorTFLite:
    """
    Same behavior as the Keras EmotionPredictor, but runs the emotion
    model through a tf.lite.Interpreter instead of a Keras Model.
    """

    LABELS = ("neutral", "happy", "confuse", "surprise")

    def __init__(self, tflite_model_path: str, hand_model_path: str) -> None:
        self._interpreter = self._build_interpreter(str(tflite_model_path))
        self._face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
        )
        self._hand_model      = self._load_hand(str(hand_model_path))
        self._hand_ts_counter = 0

    # ── Public ────────────────────────────────────────────────────────

    def predict(self, face_crop: np.ndarray, face_id: str | None = None) -> str | None:
        """
        Returns one of LABELS, or None if the model failed to load.
        face_id is accepted for drop-in compatibility but unused here.
        """
        if self._interpreter is None:
            return None
        return self._detect(face_crop)

    def forget(self, face_id: str) -> None:
        """No-op: no per-face state to clean up."""
        pass

    # ── Private: setup ───────────────────────────────────────────────

    @staticmethod
    def _build_interpreter(tflite_path: str):
        try:
            interpreter = tf.lite.Interpreter(model_path=tflite_path)
            interpreter.allocate_tensors()
            print(f"[EmotionPredictorTFLite] Model loaded from: {tflite_path}")
            return interpreter
        except Exception as exc:
            print(f"[EmotionPredictorTFLite] Load failed: {exc}")
            return None

    @staticmethod
    def _load_hand(task_file_path: str):
        try:
            opts = mp.tasks.vision.HandLandmarkerOptions(
                base_options=mp.tasks.BaseOptions(model_asset_path=task_file_path),
                num_hands=2,
                running_mode=mp.tasks.vision.RunningMode.VIDEO,
            )
            model = mp.tasks.vision.HandLandmarker.create_from_options(opts)
            print("[EmotionPredictorTFLite] Hand model loaded.")
            return model
        except Exception as exc:
            print(f"[EmotionPredictorTFLite] Hand model load failed: {exc}")
            return None

    # ── Private: hand-on-chin ────────────────────────────────────────

    def _detect_hand_on_chin(self, mp_image: mp.Image, chin_pt: np.ndarray, face_width: float) -> bool:
        if self._hand_model is None:
            return False

        self._hand_ts_counter += 1
        try:
            result = self._hand_model.detect_for_video(mp_image, self._hand_ts_counter)
        except Exception as exc:
            print(f"[EmotionPredictorTFLite] Hand inference error: {exc}")
            return False

        if not result or not result.hand_landmarks:
            return False

        h, w      = mp_image.height, mp_image.width
        threshold = face_width * _HAND_CHIN_DISTANCE_RATIO

        for hand in result.hand_landmarks:
            for idx in _HAND_KEY_POINTS:
                pt = np.array([hand[idx].x * w, hand[idx].y * h])
                if np.linalg.norm(pt - chin_pt) < threshold:
                    return True
        return False

    # ── Private: TFLite forward pass ─────────────────────────────────

    def _run_tflite(self, x_in: np.ndarray) -> np.ndarray:
        """x_in: (1, 48, 48, 1) float32 -> returns (7,) softmax scores."""
        input_details  = self._interpreter.get_input_details()
        output_details = self._interpreter.get_output_details()

        self._interpreter.set_tensor(input_details[0]["index"], x_in)
        self._interpreter.invoke()
        out = self._interpreter.get_tensor(output_details[0]["index"])
        return out[0]

    # ── Private: main per-call detection ─────────────────────────────

    def _detect(self, frame: np.ndarray) -> str:
        gray    = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray_eq = cv2.equalizeHist(gray)

        faces = self._face_cascade.detectMultiScale(
            gray_eq, scaleFactor=1.1, minNeighbors=3, minSize=(30, 30)
        )

        if len(faces) == 0:
            fh, fw = gray.shape
            x, y   = 0, 0
        else:
            x, y, fw, fh = max(faces, key=lambda f: f[2] * f[3])

        roi  = cv2.resize(gray[y:y + fh, x:x + fw], (48, 48))
        x_in = np.expand_dims(np.expand_dims(roi, -1), 0).astype('float32') / 255.0

        full_pred = self._run_tflite(x_in)  # shape (7,)

        # Restrict to 3 classes + renormalize
        keep_idx = list(_KEEP_INDICES.keys())
        sub      = full_pred[keep_idx].astype("float64")
        total    = sub.sum()
        if total <= 1e-9:
            sub = np.array([0.0, 1.0, 0.0])   # fallback → neutral
        else:
            sub = sub / total

        best  = int(np.argmax(sub))
        label = _KEEP_INDICES[keep_idx[best]]

        # ── Hand-on-chin -> confuse ───────────────────────────────────
        chin_pt    = np.array([x + fw / 2.0, y + fh], dtype=np.float64)
        face_width = fw + 1e-6

        rgb      = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        hand_on_chin = self._detect_hand_on_chin(mp_image, chin_pt, face_width)

        if hand_on_chin and label != "happy":
            return "confuse"
        return label