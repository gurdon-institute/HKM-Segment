import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.plugin.filter.*;
import ij.process.*;
import ij.measure.*;

import java.awt.Color;

import java.lang.Exception;
import java.lang.RuntimeException;
import java.util.*;


public class HistogramCluster{
private ImagePlus imp;
private double[] histValues;
private int[] histCounts;
private double[] means;
private double imin, imax;
private int k;

	public HistogramCluster(ImagePlus imp){
		this.imp = imp;
		doHistogram();
	}
	
	private void doHistogram(){
	try{
		imp.killRoi();
		ImageStatistics stats = new StackStatistics(imp);
		imin = stats.min;
		imax = stats.max;
		double binW = stats.binSize; //imax/n;
		int Z = imp.getNSlices();
		histCounts = stats.histogram; //always use the stack histogram, even for previews
		int n = histCounts.length;	//256
		histValues = new double[n];
		for(int i=0;i<n;i++){
			histValues[i] = (i*binW)+imin;
		}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public double[] getValues(){
		return histValues;
	}
	public int[] getCounts(){
		return histCounts;
	}
	
	public Color[] createColours() throws Exception{	//create k heatmap colours
		if(k==0){throw new Exception("HistogramCluster.createColours() - no clusters to create colours for,\nrun getLevels(int startK, int minN) first.");}
		Color[] colours = new Color[k];
		for(int m=0; m<k; m++){
			float f = 2f * (m+1)/k;
			int b = (int)(Math.max(0, 255*(1f - f)));
			int r = (int)(Math.max(0, 255*(f - 1f)));
			int g = 255 - r - b;
			colours[m] = new Color( r, g, b );
		}
		return colours;
	}
	
	public double[] getLevels(){
		return getLevels(128, 1);
	}
	
	public double[] getLevels(int startK){
		return getLevels(startK, 1);
	}
	
	public double[] getLevels(int startK, double minN){
	try{
		k = startK;
		int[] clusters = new int[histValues.length];
		means = new double[startK];
		for(int c=0;c<histValues.length;c++){
			clusters[c] = c;
		}
		double step = ((imax-imin)/k);
		for(int v=0; v<k; v++){
			means[v] = v*step + imin;
		}
		double mergeD = (imax-imin)/(2*k);	//(imax-imin)/(k+1);
		boolean done = false;
		while (done==false){	//IJ.log("means = "+Arrays.toString(means));
			done = true;
			for (int c=0; c<clusters.length; c++){
				double minD = Double.POSITIVE_INFINITY;
				int mini = -1;
				for (int m=0; m<means.length; m++){
					double diff = Math.abs(histValues[c]-means[m]);
					if (diff<minD){
						mini = m;
						minD = diff;
					}
				}
				if (clusters[c] != mini){
					done = false;
					clusters[c] = mini;
				}
			}
			for (int m=0; m<means.length; m++){
				double total = 0d;
				int n = 0;
				for(int c=0;c<clusters.length;c++){
					if(clusters[c]==m){
						total += histCounts[c]*histValues[c];
						n += histCounts[c];				//IJ.log(c+","+means[m]+","+histValues[c]+","+histCounts[c]+","+n);
					}
				}
				if(n<minN){
					means[m] = -1d;
					done = false;
				}
				else{
					if(Math.abs(means[m]-(total/n))>1){
						means[m] = total/n;	//IJ.log(m+" -> "+means[m]+" = "+total+"/"+n);
						if(means[m]<imin||means[m]>imax){
							throw new RuntimeException("Calculated mean out of range: "+imin+" < "+means[m]+" < "+imax+" = false");
						}
						done = false;
					}
				}
			}
			for (int m=0; m<means.length-1; m++){
				if(means[m]<0 || means[m+1]<0){
					continue;
				}
				double d = Math.abs(means[m]-means[m+1]);
				if(d<=mergeD){
					means[m] = -1d;
					means[m+1] = (means[m]+means[m+1])/2d;
					done = false;
				}
			}
			ArrayList<Double> newMeans = new ArrayList<Double>();
			for (int m=0; m<means.length; m++){
				if(means[m] > 0){
					newMeans.add(means[m]);
				}
			}
			means = new double[newMeans.size()];
			for(int i=0;i<means.length;i++){
				means[i] = newMeans.get(i);
			}
			k = means.length;
		}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
		return means;
	}

	
}