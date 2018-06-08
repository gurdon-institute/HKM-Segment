package uk.ac.cam.gurdon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.RoiEnlarger;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;


@Plugin(type = Command.class, menuPath = "Plugins>HKM Segment")
public class HKM_Segment implements Command{
	
HKMConfig config = new HKMConfig();
ImagePlus imp;

private ImagePlus proc;
private Calibration cal;
private Overlay ol;
private ResultsTable results;

private double pixW;

double pixD;

private double threshold;
String unit;

private String Vunit;
private int W, H, C, Z, k;
private Color[] previewColours;
private double[] means;
private ArrayList<Roi> cells;

private HKMGUI gui;
HKMParams params;
private String macroArgs;

private TargetTable target;

	public HKM_Segment(){
		macroArgs = Macro.getOptions();
		setImage();
	}
	
	public HKM_Segment(ImagePlus image){
		macroArgs = Macro.getOptions();
		setImage(image);
	}
	
	private void record(){
		Recorder.getInstance();
		if(Recorder.record){
			String args = "startK="+params.startK+" blur="+params.sigma+" minR="+params.minR+" maxR="+params.maxR+" threshold="+params.thresholdMethod+" watershed="+params.watershed;
			String rec = "run(\"HKM Segment\", \""+args+"\");\n";
			Recorder.recordString(rec);
		}
	}
	
	public boolean setImage(){
		if(WindowManager.getImageCount()==0){
			IJ.error("HK_Segment", "No Images are open.");
			return false;
		}
		else{
			imp = WindowManager.getCurrentImage();
			return setImage(imp);
		}
	}
	
	public boolean setImage(ImagePlus image){
		imp = image;
		cal = imp.getCalibration();
		W = imp.getWidth();
		H = imp.getHeight();
		C = imp.getNChannels();
		Z = imp.getNSlices();
		pixW = cal.pixelWidth;
		pixD = cal.pixelDepth;
		unit = cal.getUnit();
		if(unit.matches("[Mm]icrons?")){unit = "\u00B5m";}
		Vunit = " ("+unit+"\u00B3)";
		imp.setOverlay(null);
		imp.killRoi();
		IJ.run(imp, "Options...", "iterations=1 count=1 black");
		return true;
	}
	
	private boolean onEdge(Roi roi){
		for(int xy=0; xy<Math.max(W,H); xy++){
			if( roi.contains(xy,0) || roi.contains(xy,H-1) || roi.contains(0,xy) || roi.contains(W-1,xy) ){
				return true;
			}
		}
		return false;
	}
	
	private void processImage(boolean preview){
		int start = 1;
		int end = Z;
		if(preview){
			start = imp.getZ();
			end = start;
		}
		int channel = imp.getC();
		proc = new Duplicator().run(imp, channel, channel, start, end, 1, 1);
		IJ.run(proc, "Gaussian Blur...", "sigma="+params.sigma+" scaled stack");
	}
	
	private ArrayList<Roi> extractObjects(boolean preview){
		try{
			ThresholdToSelection tts = new ThresholdToSelection();
			cells = new ArrayList<Roi>();
			ol = new Overlay();
			int start = 1;
			int end = Z;
			if(preview){
				start = imp.getZ();
				end = start;
			}
			int channel = imp.getC();
			boolean[] usedMean = new boolean[means.length];
			threshold = -1d;
			if(!params.thresholdMethod.equals("None")){
				IJ.setAutoThreshold(proc, params.thresholdMethod+" dark stack");
				threshold = proc.getProcessor().getMinThreshold();
			}

			for(int z=start;z<=end;z++){						//for each slice
				ByteProcessor outip = new ByteProcessor(W, H);
				outip.setColor(Color.WHITE);
				for(int m=0; m<means.length; m++){
					IJ.setThreshold(proc, means[m], Integer.MAX_VALUE);			//means in increasing order
					proc.setPositionWithoutUpdate(channel, z, 1);
					ImageProcessor procip = proc.getProcessor();
					procip.setColor(Color.BLACK);			
					if(procip.getStatistics().mean==0) continue;		//ThresholdToSelection throws ArrayIndexOutOfBoundsException if image is empty
					Roi roi = null;
					try{
						roi = tts.convert(procip);
					}catch(ArrayIndexOutOfBoundsException oob){continue;}	//ignore ArrayIndexOutOfBoundsException from ThresholdToSelection
					if(roi==null)continue;
					Rectangle offsetRect = roi.getBounds();
					procip.setRoi(roi);
					ImageProcessor mask = procip.getMask();		//returns null if the Roi is regular, eg a rectangle covering the whole image
					if(mask==null){
						continue;
					}
					ImagePlus maskimp = new ImagePlus("mask", mask);
					if(params.watershed){
						IJ.run(maskimp, "Watershed", "");
					}
					if(mask.getStatistics().mean==0) continue;
					try{
						roi = tts.convert(mask);
					}catch(ArrayIndexOutOfBoundsException oob){continue;}	//ignore ArrayIndexOutOfBoundsException from ThresholdToSelection
					if(roi==null)continue;	
					Rectangle rect = roi.getBounds();
					roi.setLocation(rect.x+offsetRect.x, rect.y+offsetRect.y);
					maskimp.close();		
					if(roi!=null){
						Roi[] split = new ShapeRoi(roi).getRois();
						for(int ri=0;ri<split.length;ri++){
							Roi r = split[ri];
							int ed = (int)Math.floor(params.minR/4d/pixW)+1;
							int blurAdjust = 0;
							if(params.sigma>pixW){
								blurAdjust = (int)Math.floor(params.sigma/pixW);
							}

							boolean tooSmall = false;
							try{
								r = RoiEnlarger.enlarge(r, -ed);
							}catch(ArrayIndexOutOfBoundsException oob){	//from ThresholdToSelection, caused by trying to convert threshold to ShapeRoi with a mask that has no signal
								tooSmall = true;	//if oob was thrown, the erosion completely removed the Roi
							}
							if(!tooSmall){
								try{
									r = RoiEnlarger.enlarge(r, ed-blurAdjust);
								}catch(ArrayIndexOutOfBoundsException oob){continue;}
								if(onEdge(r)){
									continue;
								}
							}
							procip.setRoi(r);
							ImageStatistics procStats = ImageStatistics.getStatistics(procip, ImageStatistics.AREA+ImageStatistics.MEAN, cal);
							outip.setRoi(r);
							if(outip.getStatistics().mean > 0){ //already added
								continue;
							}
							if( procStats.area>=params.minA && procStats.area<=params.maxA && procStats.mean>=threshold ){
								r.setPosition(z);
								r.setStroke(config.stroke);
								if(Z==1&&C>1){r.setPosition(channel);}
								if(preview){	//add overlay for previews using hierarchy level colours
									r.setStrokeColor(previewColours[m]);
									ol.add(r);
								}
								cells.add(r);	
								outip.fill(r); //add to the binary mask (white)
								procip.fill(r); //remove from the thresholding image (black)
								usedMean[m] = true;
							}
							else if(params.showBad){
								Roi bad = r;
								bad.setPosition(1, z, 1);
								bad.setStroke(config.dottedStroke);
								bad.setStrokeColor(Color.YELLOW);
								ol.add(bad);
							}
						}
					}
				}
				proc.close();
			}
			ArrayList<Double> keepmeans = new ArrayList<Double>();
			for(int m=0;m<means.length;m++){
				if(usedMean[m]){keepmeans.add(means[m]);}
			}
			k = keepmeans.size();
			means = new double[k];
			for(int m=0;m<k;m++){
				means[m] = keepmeans.get(m);
			}
			if(k==0){
				IJ.error("HKM Segment", "No objects found.\nTry increasing K and setting blur radius to half the minimum radius.");
			}
			if(params.showOverlay) imp.setOverlay(ol);
		}
		catch(ArrayIndexOutOfBoundsException oob){
			System.out.print(oob.toString()+" in extractObjects\n~~~~~\n"+Arrays.toString(oob.getStackTrace()).replace(",","\n"));
		}
		catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return cells;
	}
	
	private void output(ArrayList<Object3D> objects){
			if(!params.showResults) return;
		try{
			if(imp.getWindow()!=null){	//won't have an ImageWindow if called from a batch mode macro
				imp.getWindow().setVisible(false);	
			}
			Color[] objectColours = ColourSets.heatmap(objects.size());//ColourSets.random(objects.size());
			results = new ResultsTable();
			if(IJ.isMacro()){ results = ResultsTable.getResultsTable(); }
			results.setPrecision(3);
			results.showRowNumbers(false);
			ol = imp.getOverlay();
			if(ol==null) ol = new Overlay();
			int row = results.getCounter();
			int startC = imp.getChannel();
			int startZ = imp.getSlice();
			for(int i=0;i<objects.size();i++){
				
				Object3D obj = objects.get(i);
				double[] sum = new double[C+1];
				int[] count = new int[C+1];
				for(Roi roi : obj.rois){
					imp.setRoi(roi);
					int z = roi.getPosition();
					for(int c=1;c<=C;c++){
						imp.setPositionWithoutUpdate(c, z, 1);
						ImageStatistics stats = imp.getStatistics();
						sum[c] += stats.mean*stats.pixelCount;
						count[c] += stats.pixelCount;
					}
					roi.setStrokeColor(objectColours[i]);
					if(Z==1){
						roi.setPosition(0, 0, 0);
					}
					ol.add(roi);
				}

				if(!IJ.isMacro()){
					results.setValue("Index", row, i+1);
					results.setValue("X", row, obj.centroid.x);
					results.setValue("Y", row, obj.centroid.y);
					results.setValue("Z", row, obj.centroid.z);
					results.setValue("Volume"+Vunit, row, obj.volume);
					for(int c=1;c<=C;c++){
						double mean = sum[c]/count[c];
						results.setValue("C"+c+" Mean", row, mean);
					}
				}
				
				row++;
				TextRoi marker3D = new TextRoi((obj.centroid.x/pixW)-(config.fontS/4), (obj.centroid.y/pixW)-(config.fontS/2), ""+(i+1), config.labelFont);
				if(Z==1){
					marker3D.setPosition(0);
				}
				else{
					marker3D.setPosition( (int)Math.round(obj.centroid.z/pixD) );
				}
				marker3D.setStrokeColor(objectColours[i]);
				ol.add(marker3D);
			}
			imp.killRoi();
			imp.setPositionWithoutUpdate(startC, startZ, 1);
			//if(IJ.isMacro()){ results.show("Results"); }
			//else{ results.show(imp.getTitle()+"-HKM Segmentation"); }
			if(!IJ.isMacro()){ results.show(imp.getTitle()+"-HKM Segmentation"); }	//don't show table if run from macro - probably only the Rois needed
		}catch(Exception e){System.out.println(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		finally{ if(imp.getWindow()!=null) imp.getWindow().setVisible(true); }
	}
	
	public static JPanel guiPanel(Object... comp){
		JPanel panel = new JPanel();
		for(Object obj : comp){
			if(obj==null){  //probably because there is no image.
			}
			else if(obj instanceof String){
				panel.add(new JLabel((String)obj));
			}
			else if(obj instanceof Component){
				panel.add((Component)obj);
			}
			else{
				throw new IllegalArgumentException("guiPanel Object not supported - "+obj.toString()+"\nString or Component required.");
			}
		}
		return panel;
	}
	
	static int getInt(String text){
		int i;
		try{
			i = Integer.parseInt(text);
		}catch(NumberFormatException nfe){
			IJ.error("HKM Segment",text+" is not an integer.");
			throw nfe;
		}
		return i;
	}
	static double getDouble(String text){
		double d;
		try{
			d = Double.valueOf(text);
		}catch(NumberFormatException nfe){
			IJ.error("HKM Segment",text+" is not a number.");
			throw nfe;
		}
		return d;
	}
	

	
	double measureRoi(double current){
		Roi roi = imp.getRoi();
		double size = current;
		if(imp==null||imp.getRoi()==null){
			IJ.error("HKM Segment", "No Roi to measure.");
		}
		else if(roi instanceof Line){
			size = roi.getLength();
		}
		else if(roi instanceof Roi){ //rectangular
			Rectangle bounds = roi.getBounds();
			size = Math.max(bounds.width, bounds.height)*cal.pixelWidth;
		}
		else{	//polygon, shape, freehand
			size = roi.getFeretValues()[0];
		}
		return size;
	}
	
	public void segment(HKMParams params){
		segment(params, false, false);
	}
	
	public void segment(HKMParams params, final boolean preview, final boolean isMacro){
		try{
			if(imp==null){
				if(!setImage()){return;}
			}
			if(!isMacro&&gui.card!=null){ gui.card.show(gui.getContentPane(), "working"); }
			
			if(imp.getBitDepth()==32){
				IJ.error("32-bit images are not supported.");
				return;
			}
			if(imp.getNFrames()>1){
				IJ.error("Time series are not supported.");
				return;
			}
			
			this.params = params;
			
			if(!preview && !isMacro){ record(); }
			processImage(preview);
			HistogramCluster hc = new HistogramCluster(proc);
			//if(true){proc.show();return;}
			int minN = (int)Math.ceil(params.minA/pixW/pixW);
			means = hc.getLevels(params.startK, minN);
			if(means.length<1){IJ.error("Clusters could not be separated. Try decreasing the minimum radius.");return;}
			previewColours = ColourSets.heatmap(hc.getK());
		//long time0 = System.nanoTime();
			extractObjects(preview);
		//System.out.println( "extractObjects "+((System.nanoTime()-time0)/1000000000f)+" sec" );			
			if(!preview){
				double join = params.maxR;
				Volumiser vol = new Volumiser(imp, join, params.minV);
				ArrayList<Object3D> o3d = vol.getVolumes( cells );
				output(o3d);
			}
			
			if(isMacro&&params.showResults){
				/*IJ.run("ROI Manager...", "");
				RoiManager rm = RoiManager.getInstance();	//fubar in IJ2
				if(rm==null){
					rm = new RoiManager();
				}*/
				RoiManager rm = new RoiManager();
				rm = RoiManager.getInstance2();	//new method in IJ2
				for(Roi cell : cells){
					Roi olRoi = (Roi)cell.clone();
					olRoi.setPosition(0);	//remove position info
					rm.addRoi(olRoi);
				}
				rm.setVisible(true);
			}
			
		}catch(Exception e){System.out.println(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		finally{
			if(!isMacro&&gui.card!=null){ gui.card.show(gui.getContentPane(), "main"); }
		}
	}	
	
	public void run(){
		run("");
	}
	
	public void run(String arg){
	try{
		if(IJ.isMacro()){
			
			if(macroArgs!=null&&macroArgs.length()>0){
				String[] args = macroArgs.split(" ");
				params = new HKMParams();
				for(String m : args ){
					String[] kv = m.split("=");
					if(kv[0].equals("startK")){ params.startK = getInt(kv[1]); }
					else if(kv[0].equals("blur")){ params.sigma = getDouble(kv[1]); }
					else if(kv[0].equals("minR")){ params.minR = getDouble(kv[1]); }
					else if(kv[0].equals("maxR")){ params.maxR = getDouble(kv[1]); }
					else if(kv[0].equals("threshold")){  params.thresholdMethod = kv[1]; }
					else if(kv[0].equals("watershed")){  params.watershed = Boolean.valueOf(kv[1]);}
					else if(kv[0].length()>0){
						IJ.error("HKM Segment", "Unknown argument: "+kv[0]);
						return;
					}
				}
				if(!params.isValid()){return;}
				params.minA = Math.PI*(params.minR*params.minR);
				if(Z>1){
					params.minV = (4d/3d)*Math.PI*(params.minR*params.minR*params.minR);
				}
				else{ params.minV = params.minA; }
				params.maxA = Math.PI*(params.maxR*params.maxR);
			}
			else{
				gui = new HKMGUI(this);
				return;
			}
			params.showBad = false;	//don't show rejected objects when called from a macro
			segment(params, false, true);
		}
		else{
			gui = new HKMGUI(this);
		}
	}catch(Exception e){System.out.println(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public ArrayList<Roi> run(ImagePlus imp,int startK,double sigma,double minR,double maxR,String thresholdMethod,boolean watershed,boolean showResults){
		
		setImage(imp);
		
		params = new HKMParams();
		params.startK = startK;
		params.sigma = sigma;
		params.minR = minR;
		params.maxR = maxR;
		params.thresholdMethod = thresholdMethod;
		params.watershed = watershed;
		if(params.isValid()){return null;}
		params.minA = Math.PI*(params.minR*params.minR);
		
		if(Z>1){
			params.minV = (4d/3d)*Math.PI*(params.minR*params.minR*params.minR);
		}
		else{ params.minV = params.minA; }
		params.maxA = Math.PI*(params.maxR*params.maxR);
		params.showBad = false;	//don't show rejected objects
		
		params.showResults = showResults;
		
		segment(params, false, false);
		
		return cells;
	}
	
	public void targeter() {
		if(imp==null||results==null){
			IJ.error("HKM_Segment", "This function highlights the location of the selected results table object in the current image.");
			return;
		}
		if(target==null){
			target = new TargetTable(imp, results);
		}
		target.target();
	}

	public void overlay(boolean showOverlay) {
		if(showOverlay){
			imp.setOverlay(ol);
		}
		else{
			imp.setOverlay(null);
		}
	}
	
	public ResultsTable getResultsTable(){
		return results;
	}
	
	public static void main(String[] arg){
		final ij.ImageJ ij = new ij.ImageJ();
		ImagePlus image = new ImagePlus("E:\\Heleene\\C1-slice-test1.tif");
		image.show();
		ij.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent we){
				System.exit(1);
			}
		});
		
		new HKM_Segment().run(); //show GUI
		//new HKM_Segment().run(8, 0.2, 2, 4, "Huang", true, false); //pass parameters
	}
	
}