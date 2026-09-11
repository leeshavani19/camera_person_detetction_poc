# first import the module of gender and age and than make object of their class and call the predict method

# #do not dirrectly give the image path directly, cropped face should be ndarray
# _gender.predict(cropped_face)
        # # Returns (gender, confidence)
        # Input Shape [  1 224 224   3]
        # Input dtype<class 'numpy.float32'>



# _age.predict(cropped_face)
        # # Return cls_name, cls_conf
        # Input shape: [  1 224 224   3]
        # Input dtype: <class 'numpy.float32'>



#implementation
import cv2
from core import gender_predictor, age_predictor
img=cv2.imread(r'C:\Users\dushyant.kewat\Pictures\Screenshots\Screenshot 2026-07-27 150402.png')
_gender  = gender_predictor.GenderPredictor(r'C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\gender_model_v2.tflite')
a=_gender.predict(img)
print(a)




_age     = age_predictor.AgePredictor(r'C:\Git Projects\odigo-v3-camera-use-case\src\odigo_v3_camera\Assets\models\age_model_newfp16.tflite')
ag=_age.predict(img)
print(ag)
