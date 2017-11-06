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
	

private static final String[] methods = {"None", "Huang", "IsoData", "Li", "MaxEntropy",
										 "Mean", "Minimum", "Moments", "Otsu", "Percentile", 
										 "RenyiEntropy", "Shanbhag", "Triangle", "Yen" };

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

private TargetTable target;

	public HKM_Segment(){
		setImage();
	}
	
	public HKM_Segment(ImagePlus image){
		setImage(image);
	}
	
	private void record(){
		Recorder.getInstance();
		if(Recorder.record){
			String args = "startK="+gui.startK+" blur="+gui.sigma+" minR="+gui.minR+" maxR="+gui.maxR+" threshold="+gui.thresholdMethod+" watershed="+gui.watershed;
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
		IJ.run(proc, "Gaussian Blur...", "sigma="+gui.sigma+" scaled stack");
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
			if(!gui.thresholdMethod.equals("None")){
				IJ.setAutoThreshold(proc, gui.thresholdMethod+" dark stack");
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
					if(gui.watershed){
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
							int ed = (int)Math.floor(gui.minR/4/pixW)+1;
							int blurAdjust = 0;
							if(gui.sigma>pixW){
								blurAdjust = (int)Math.floor(gui.sigma/pixW);
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
							if( procStats.area>=gui.minA && procStats.area<=gui.maxA && procStats.mean>=threshold ){
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
							else if(gui.showBad){
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
			if(gui.showOverlay) imp.setOverlay(ol);
		}
		catch(ArrayIndexOutOfBoundsException oob){
			System.out.print(oob.toString()+" in extractObjects\n~~~~~\n"+Arrays.toString(oob.getStackTrace()).replace(",","\n"));
		}
		catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return cells;
	}
	
	private void output(ArrayList<Object3D> objects){
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
	
	boolean validParameters(){
		if(gui.startK<2){
			IJ.error("HKM Segment", "Starting K should be at least 2.");
			return false;
		}
		if(gui.startK>256){
			IJ.error("HKM Segment", "Starting K should be no greater than 256");
			return false;
		}
		if(gui.sigma<0){
			IJ.error("HKM Segment", "Blur Radius cannot be negative.");
			return false;
		}
		if(gui.minR<0||gui.maxR<0){
			IJ.error("HKM Segment", "Object radius cannot be negative.");
			return false;
		}
		if(gui.minR>=gui.maxR){
			IJ.error("HKM Segment", "Minimum radius should be smaller than maximum radius.");
			return false;
		}
		if(!Arrays.asList(methods).contains(gui.thresholdMethod)){
			IJ.error("HKM Segment", "Unknown thresholding method : "+gui.thresholdMethod);
			return false;
		}
		return true;
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
	
	public void segment(){
		segment(false, false);
	}
	
	void segment(final boolean preview, final boolean isMacro){
		try{
			if(!setImage()){return;}
			if(!isMacro){ gui.card.show(gui.getContentPane(), "working"); }
			
			if(imp.getBitDepth()==32){
				IJ.error("32-bit images are not supported.");
				return;
			}
			if(imp.getNFrames()>1){
				IJ.error("Time series are not supported.");
				return;
			}
			if(!preview && !isMacro){ record(); }
			processImage(preview);
			HistogramCluster hc = new HistogramCluster(proc);
			//if(true){proc.show();return;}
			int minN = (int)Math.ceil(gui.minA/pixW/pixW);
			means = hc.getLevels(gui.startK, minN);
			if(means.length<1){IJ.error("Clusters could not be separated. Try decreasing the minimum radius.");return;}
			previewColours = ColourSets.heatmap(hc.getK());
		//long time0 = System.nanoTime();
			extractObjects(preview);
		//System.out.println( "extractObjects "+((System.nanoTime()-time0)/1000000000f)+" sec" );			
			if(!preview){
				double join = gui.maxR;
				Volumiser vol = new Volumiser(imp, join, gui.minV);
				ArrayList<Object3D> o3d = vol.getVolumes( cells );
				output(o3d);
			}
			
			if(isMacro){
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
			}
			
		}catch(Exception e){System.out.println(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		finally{
			if(!isMacro){ gui.card.show(gui.getContentPane(), "main"); }
		}
	}	
	
	public void run(){
	try{
		if(IJ.isMacro()){
			String args = Macro.getOptions();
			if(args.length()>0){
				String[] params = args.split(" ");
				for(String param : params ){
					String[] kv = param.split("=");
					if(kv[0].equals("startK")){ gui.startK = getInt(kv[1]); }
					else if(kv[0].equals("blur")){ gui.sigma = getDouble(kv[1]); }
					else if(kv[0].equals("minR")){ gui.minR = getDouble(kv[1]); }
					else if(kv[0].equals("maxR")){ gui.maxR = getDouble(kv[1]); }
					else if(kv[0].equals("threshold")){  gui.thresholdMethod = kv[1]; }
					else if(kv[0].equals("watershed")){  gui.watershed = Boolean.valueOf(kv[1]);}
					else if(kv[0].length()>0){
						IJ.error("HKM Segment", "Unknown argument: "+kv[0]);
						return;
					}
				}
				if(!validParameters()){return;}
				gui.minA = Math.PI*(gui.minR*gui.minR);
				if(Z>1){
					gui.minV = (4d/3d)*Math.PI*(gui.minR*gui.minR*gui.minR);
				}
				else{ gui.minV = gui.minA; }
				gui.maxA = Math.PI*(gui.maxR*gui.maxR);
			}
			gui.showBad = false;	//don't show rejected objects when called from a macro
			segment(false, true);
		}
		else{
			//showGui();
			gui = new HKMGUI(this);
		}
	}catch(Exception e){System.out.println(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public static void main(String[] arg){
		final ij.ImageJ ij = new ij.ImageJ();
		ImagePlus image = new ImagePlus("E:\\test data\\DAPI1.tif");
		image.show();
		ij.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent we){
				System.exit(1);
			}
		});
		
		new HKM_Segment().run();
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
	
}