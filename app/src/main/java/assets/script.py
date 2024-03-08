import numpy as np
from scipy import signal
import sklearn
from numpy.lib.stride_tricks import as_strided
from xgboost import XGBRegressor
from tensorflow.keras.models import Sequential, Model
from tensorflow.keras.layers import Dense, LSTM,Flatten, Conv3D, MaxPooling3D, Dropout, BatchNormalization, LeakyReLU, Convolution1D, Input, MaxPooling1D, concatenate, Activation
import tensorflow as tf
import pandas as pd
from os.path import dirname, join
import tflite_runtime.interpreter as tflite
from tensorflow import lite


def bandpass_filter(
        sig: np.ndarray,
        fs: int,
        lowcut: float,
        highcut: float,
        order: int=2
) -> np.ndarray:

    nyquist = 0.5 * fs
    low = lowcut / nyquist
    high = highcut / nyquist
    b, a = butter(order, [low, high], btype='band')
    sig_filtered = filtfilt(b, a, sig)
    return sig_filtered


def windowed_view(arr, window, overlap):
    arr = np.asarray(arr)
    window_step = window - overlap
    new_shape = arr.shape[:-1] + ((arr.shape[-1] - overlap) // window_step,
                                  window)
    new_strides = (arr.strides[:-1] + (window_step * arr.strides[-1],) +
                   arr.strides[-1:])
    return as_strided(arr, shape=new_shape, strides=new_strides)

def predict(segments):
    filename = join(dirname(__file__), "transformer.tflite")
    interpreter = lite.Interpreter(model_path=filename)

    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    interpreter.allocate_tensors()

    predicted_values = []
    for i in range(len(segments)):
        interpreter.set_tensor(input_details['index'], np.array(segments.iloc[i:i+1,:]).astype(np.float32).reshape(1,500,1))
        interpreter.invoke()
        # output_details[0]['index'] = the index which provides the input
        output_data = interpreter.get_tensor(output_details['index'])
        predicted_values.append(output_data)

    value = np.array(predicted_values).mean()
    return int(value)

def process_signal(ppg):
    ppg = np.array(ppg)
    filtered = bandpass_filter ( ppg,50,.5,50,2)
    segmented = windowed_view(filtered,500,387)

    df = pd.DataFrame()
    for j in range(len(segmented)):
        segment = segmented[j].squeeze()
        segment = (segment-np.min(segment))/(np.max(segment)-np.min(segment))

        new_df = pd.DataFrame([segment])

        df = pd.concat([df,new_df], axis=0, ignore_index=True)


    m = predict(df)
    print(m)
    return str(m)


