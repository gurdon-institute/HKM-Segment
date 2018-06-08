package uk.ac.cam.gurdon;

import java.util.Arrays;

import ij.IJ;
import ij.Prefs;

public class HKMParams {

	static final String[] methods = {"None", "Huang", "IsoData", "Li", "MaxEntropy",
			 "Mean", "Minimum", "Moments", "Otsu", "Percentile", 
			 "RenyiEntropy", "Shanbhag", "Triangle", "Yen" };
	
	
	double minA, maxA, minV;
	int W, H, C, Z, k;
	boolean showOverlay = true;
	
	double minR = Prefs.get("HKM_Segment.minR", 5.0);
	double maxR = Prefs.get("HKM_Segment.maxR", 30.0);
	double sigma = Prefs.get("HKM_Segment.sigma", 0.0);
	int startK = (int)Prefs.get("HKM_Segment.startK", 10);
	String thresholdMethod = Prefs.get("HKM_Segment.thresholdMethod", "None");
	boolean watershed = Prefs.get("HKM_Segment.watershed", false);
	boolean showBad = Prefs.get("HKM_Segment.showBad", true);
	boolean showResults = true;
	
	
	public boolean isValid(){
		
		if(startK<2){
			IJ.error("HKM Segment", "Starting K should be at least 2.");
			return false;
		}
		if(startK>256){
			IJ.error("HKM Segment", "Starting K should be no greater than 256");
			return false;
		}
		if(sigma<0){
			IJ.error("HKM Segment", "Blur Radius cannot be negative.");
			return false;
		}
		if(minR<0||maxR<0){
			IJ.error("HKM Segment", "Object radius cannot be negative.");
			return false;
		}
		if(minR>=maxR){
			IJ.error("HKM Segment", "Minimum radius should be smaller than maximum radius.");
			return false;
		}
		if((!thresholdMethod.equals("None")) && !Arrays.asList(methods).contains(thresholdMethod)){
			IJ.error("HKM Segment", "Unknown thresholding method : "+thresholdMethod);
			return false;
		}
		return true;
	}
	
	
}
