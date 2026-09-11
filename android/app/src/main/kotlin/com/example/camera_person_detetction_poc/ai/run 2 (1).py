# This file provides complete instructions for initializing, preprocessing, and running inference for the GenderPredictor, AgePredictor, and ViewerBystanderClassifier modules.

# # first import the module of gender and age and than make object of their class and call the predict method
# # #do not dirrectly give the image path directly, cropped face should be ndarray with padding of 30px 


# 1. Gender Predictor (GenderPredictor)
        # Initialization Parameter: Path to the .tflite model file (str).
        # Input: Preprocessed cropped face array (1, 224, 224, 3) of dtype float32.
        # Returns: (gender, confidence)
        # gender (str): Predicted gender label (e.g., "Male", "Female").
        # confidence (float): Prediction confidence score.

# 2. Age Predictor (AgePredictor)
        # Initialization Parameter: Path to the .tflite model file (str).
        # Input shape: Preprocessed cropped face array (1, 224, 224, 3) of dtype float32.
        # Returns: (cls_name, cls_conf)
        # cls_name (str): Predicted age class/bucket (e.g., "25-34").
        # cls_conf (float): Confidence score for the classification.

# 3. Viewer Bystander Classifier (ViewerBystanderClassifier)
        # Determines whether a detected subject is actively looking at the camera (Viewer) or looking away (Bystander) based on head orientation and eye gaze vector.

        # Initialization Parameter: no need any module uses mediapipe for landmarks and iris of eyes
        # Returns:landmarks, state, yaw, pitch, gaze
            # State : viewer/bystander ,
            # yaw: how much face is upwards or downwards, 
            # pitch: for left right rotation of face ,
            # gaze: for eye movement
        # Input: it can take any input size as it doesnot has any model we just have to give cropped face

        # Classification Thresholds
            # yaw_threshold: float = 30.0,
            # pitch_threshold: float = 30.0,
            # gaze_min_x: float = 0.40,
            # gaze_max_x: float = 0.60,
            # gaze_min_y: float = 0.30,
            # gaze_max_y: float = 0.63,

        # these are the yaw_threshold set
        # if yaw beyond +30 or -30 same as pitch than the person will be considered as bystander 
        # similarly if gaze (eye movement) beyond this threhold than it will be consider as bystander

# 4. Emotion:
         # Initialization Parameter: 
        # Returns:label  ("happy", "neutral", "confuse","surprise"})
        # Input shape: the emotion models takes image size of (48,48)
        


##change the models path accordingly
# #implementation
import cv2
from core import gender_predictor, age_predictor
from core.viewer_bystander import ViewerBystanderClassifier
img=cv2.imread(r"C:\Users\dushyant.kewat\Downloads\viewer_data\viewer_data\images\emotion\happy\Camera_0_2333_emotion_happy_yaw-7.4_pitch12.0_blur1117.3_1785830247225.jpg")

# gender
_gender  = gender_predictor.GenderPredictor(r'C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\gender_model_v2.tflite')
a=_gender.predict(img)
print(a)



# age
_age     = age_predictor.AgePredictor(r'C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\age_model_newfp16.tflite')
ag=_age.predict(img)
print(ag)



#viewer bystander
classifier = ViewerBystanderClassifier()
h, w = img.shape[:2]

# If you have the face bounding box:
x1, y1, x2, y2 = 0,0,w,h

# Run Viewer/Bystander prediction
landmarks, state, yaw, pitch, gaze = classifier.process(
    img,
    x1, y1, x2, y2,
    w, h
)
print(state)



##emotion:
from core.emotion_predictor import EmotionPredictor

emotion_predictor = EmotionPredictor(
    emotion_model_path=r"C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\emotion_model.h5",
    hand_model_path=r"C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\hand_landmarker.task"
)
emotion = emotion_predictor.predict(img) 
print(emotion)
