from __future__ import annotations

import numpy as np
import cv2
# from ai_edge_litert.interpreter import Interpreter

try:
    from tflite_runtime.interpreter import Interpreter
except ImportError:
    from tensorflow.lite.python.interpreter import Interpreter
AGE_MIN, AGE_MAX = 1, 95
NUM_AGE_BINS     = 95
IMG_SIZE         = 224
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)
CLS_NAMES = ["child", "teen", "young_adult", "adult", "senior"]

AGE_VECTOR = np.arange(NUM_AGE_BINS, dtype=np.float32) + AGE_MIN   # 1..95
YA_VECTOR  = np.arange(15,           dtype=np.float32) + 21.0      # 21..35


class AgePredictor:

    def __init__(
        self,
        model_path: str,
        input_size: tuple[int, int] = (300, 300),
        num_threads: int | None = None,
    ) -> None:
        kwargs = {"model_path": model_path}
        if num_threads:
            kwargs["num_threads"] = num_threads

        self._interp = Interpreter(**kwargs)
        self._interp.allocate_tensors()

        self._input_detail   = self._interp.get_input_details()[0]
        self._output_details = self._interp.get_output_details()

        print(f"[AgePredictor] Loaded TFLite AgeNet from {model_path}")

    # ── Public ───────────────────────────────────────────────────────────

    def predict(self, face_crop: np.ndarray) -> tuple[str, float] | tuple[None, None]:
        """
        face_crop : BGR numpy array (as produced by FaceUtils.age_crop).
        Returns (cls_name, cls_conf), or (None, None) for empty/invalid crops.
        """
        if face_crop is None or face_crop.size == 0:
            return None, None
        try:
            return self._infer(face_crop)
        except Exception as e:
            print(f"[AgePredictor] Inference error: {e}")
            return None, None

    # ── Private ──────────────────────────────────────────────────────────

    @staticmethod
    def _softmax(x: np.ndarray, axis: int = -1) -> np.ndarray:
        x = x - np.max(x, axis=axis, keepdims=True)
        e = np.exp(x)
        return e / np.sum(e, axis=axis, keepdims=True)

    def _preprocess(self, bgr: np.ndarray) -> np.ndarray:
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        rgb = cv2.resize(rgb, (IMG_SIZE, IMG_SIZE), interpolation=cv2.INTER_LINEAR)
        arr = rgb.astype(np.float32) / 255.0
        arr = (arr - MEAN) / STD
        return arr[np.newaxis, ...].astype(np.float32)

    def _infer(self, bgr: np.ndarray) -> tuple[str, float]:
        inp = self._preprocess(bgr)

        self._interp.set_tensor(self._input_detail["index"], inp)
        self._interp.invoke()

        age_logits = self._interp.get_tensor(self._output_details[0]["index"])
        cls_logits = self._interp.get_tensor(self._output_details[1]["index"])
        ya_logits  = self._interp.get_tensor(self._output_details[2]["index"])

        # Age head is computed for parity with the original model output,
        # though only the class prediction is currently surfaced to callers.
        age_prob = self._softmax(age_logits, axis=1)
        age_pred = float((age_prob * AGE_VECTOR).sum(axis=1)[0])

        cls_probs = self._softmax(cls_logits, axis=1)[0]
        cls_idx   = int(np.argmax(cls_probs))
        cls_conf  = float(cls_probs[cls_idx])
        cls_name  = CLS_NAMES[cls_idx]

        if cls_idx == 2:  # young_adult — refine with the dedicated head
            ya_prob  = self._softmax(ya_logits, axis=1)[0]
            ya_age   = float((ya_prob * YA_VECTOR).sum())
            age_pred = 0.6 * age_pred + 0.4 * ya_age

        age_pred = float(np.clip(age_pred, AGE_MIN, AGE_MAX))
        self.last_age = round(age_pred, 1)  # available if a caller wants it

        return cls_name, cls_conf