import wave
import array
import math
import numpy
import pywt
import scipy.signal
import pdb
import warnings

import argparse, sys

class BpmDetector:

    def __init__(self, filename, window_size):
        self.__window_size = window_size
        self.__bpm = 0
        self.__audioFile = wave.open(filename,'rb')

        self.__nsamps = self.__audioFile.getnframes()        
        if (self.__nsamps <= 0):
            raise Exception("Samples number must not be 0")

        self.__sample_frequency = self.__audioFile.getframerate()
        if (self.__sample_frequency <= 0):
            raise Exception("Sample frequency must not be 0")

        self.__channels = self.__audioFile.getnchannels()

        self.__sample_width = self.__audioFile.getsampwidth()

        if self.__sample_width == 1:
            self.__pcm_format = 'B'
        elif self.__sample_width == 2:
            self.__pcm_format = 'h'
        else:
            raise Exception("Sample width not supported") 

    def bpm(self):
        if self.__bpm == 0:
            samples = []
            #nsamps = len(self.__samps)
            window_samples = \
                int(self.__window_size*self.__sample_frequency)         
            last_window = self.__nsamps / window_samples
            window_bpms = numpy.zeros(last_window)

            #iterate through all windows
            for current_window in xrange(1,last_window):
                byte_string = \
                    self.__audioFile.readframes(window_samples)
                samples = \
                    list(array.array(self.__pcm_format, byte_string))

                # if we have a stereo signal we select only the first 
                # channel
                if (self.__channels > 1):
                    samples = \
                        samples[1:len(samples):self.__channels]              

                # check if we got to the end of the file
                if (len(samples) < window_samples):
                    break
                
                window_bpm = self.__window_bpm(samples)
                if window_bpm == None:
                    continue
                window_bpms[current_window] = window_bpm

            self.__bpm = numpy.median(window_bpms)

        return self.__bpm
    
    def __peak_detect(self, samples):
        max_sample = numpy.amax(abs(samples)) 
        sample_location = numpy.where(samples == max_sample)
        # if we cannot find the maximux it must be negative
        if len(sample_location[0]) == 0:
            sample_location = numpy.where(samples == -max_sample)
        return sample_location

    def __window_bpm(self, samples):
        cA = [] 
        cD = []
        correl = []
        cD_sum = []
        levels = 4
        max_decimation = 2**(levels-1);
        min_ndx = 60./ 220 * (self.__sample_frequency/max_decimation)
        max_ndx = 60./ 40 * (self.__sample_frequency/max_decimation)
        
        for loop in range(0,levels):
            # 1) Apply discrete wavelet transform (DWT)
            if loop == 0:
                [cA,cD] = pywt.dwt(samples, 'db4');
                cD_minlen = len(cD)/max_decimation+1;
                cD_sum = numpy.zeros(cD_minlen);
            else:
                [cA,cD] = pywt.dwt(cA, 'db4');

            # 2) Low pass filtering
            cD = scipy.signal.lfilter([0.01],[1 -0.99],cD);

            # 3) Decimation
            #       a. Full wave rectivation y[n] = abs(x[n]) 
            #       b. Downsampling y[n] = x[kn] 
            #       c. Normalization in each band y[n] = x[n] - E[x[n]]
            cD = abs(cD[::(2**(levels-loop-1))]);
            cD = cD - numpy.mean(cD);

            # 4) Recombine the signal before Autocorrelation (ACR)
            cD_sum = cD[0:cD_minlen] + cD_sum;

        # adding in the approximate data as well...    
        cA = scipy.signal.lfilter([0.01], [1 -0.99], cA);
        cA = abs(cA);
        cA = cA - numpy.mean(cA);
        cD_sum = cA[0:cD_minlen] + cD_sum;
        
        # Autocorrelation (ACR)
        correl = numpy.correlate(cD_sum, cD_sum, 'full') 
        
        midpoint = len(correl) / 2
        correl_midpoint_tmp = correl[midpoint:]
        peak_ndx = self.__peak_detect(correl_midpoint_tmp[min_ndx:max_ndx]);
            
        peak_ndx_adjusted = peak_ndx[0]+min_ndx;
        bpm = 60./ peak_ndx_adjusted * (self.__sample_frequency/max_decimation)
        return bpm



if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Process .wav file to determine the Beats Per Minute.')
    parser.add_argument('--filename', required=True,
                   help='.wav file for processing')
    parser.add_argument('--window', type=float, default=3,
                   help='size of the the window (seconds) that will be scanned to determine the bpm.  Typically less than 10 seconds. [3]')

    args = parser.parse_args()
    detector = BpmDetector(args.filename, args.window)

    print detector.bpm() 
