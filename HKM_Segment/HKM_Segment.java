import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.RoiEnlarger;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;


public class HKM_Segment implements PlugIn{
	
private JFrame gui, helpFrame;
private CardLayout card;
private JTextField kField, blurField, minField, maxField;
private JCheckBox watershedTick, badTick;
private JButton previewButton, okButton, cancelButton, targetButton, helpButton, minMeasureButton, maxMeasureButton, configButton;
private static final String[] methods = {"None", "Huang", "IsoData", "Li", "MaxEntropy",
										 "Mean", "Minimum", "Moments", "Otsu", "Percentile", 
										 "RenyiEntropy", "Shanbhag", "Triangle", "Yen" };

private HKMConfig config = new HKMConfig();
private JComboBox<String> thresholdCombo;
private ActionListener listener;
private ImagePlus imp, proc;
private Calibration cal;
private ResultsTable results;
private ThresholdToSelection tts = new ThresholdToSelection();

private double pixW, pixD, minA, maxA, minV, threshold;
private String unit;
private String Vunit;
private double minR = Prefs.get("HKM_Segment.minR", 5.0);
private double maxR = Prefs.get("HKM_Segment.maxR", 30.0);
private double sigma = Prefs.get("HKM_Segment.sigma", 0.0);
private int W, H, C, Z, k;
private int startK = (int)Prefs.get("HKM_Segment.startK", 10);
private String thresholdMethod = Prefs.get("HKM_Segment.thresholdMethod", "None");
private Color[] previewColours;
private double[] means;
private ArrayList<Roi> cells;
private boolean watershed = Prefs.get("HKM_Segment.watershed", false);
private boolean showBad = Prefs.get("HKM_Segment.showBad", true);
private TargetTable target;
private static final String helpText = "<html>"+
"<head>"+
"<style>"+
"body{"+
"	padding: 10px;"+
"	font-family: sans-serif;"+
"	font-size: 12px;"+
"	width: 800px;"+
"}"+
"h4{"+
"	text-align: center;"+
"	font-weight: bold;"+
"	font-size: 16px;"+
"}"+
"ul{"+
"	list-style-type: none;"+
"}"+
"</style>"+
"</head>"+
"<body>"+
"<h4>HKM Segment</h4>"+
"<p>"+
"HKM Segment for ImageJ is inspired by Alexandre Dufour's Hierarchical K-Means segmentation algorithm [1], available in icy [2]. In this implementation, agglomerative K-Means clustering is applied to the image histogram to determine K threshold levels, which are applied in ascending order to extract objects within the specified size range set as radii.<br>"+
"The Watershed transform can be applied to the intermediate binary images, and a thresholding algorithm can be chosen to filter out objects of low intensity, giving robust results in biological images without requiring subsequent level-sets segmentation. When object have been extracted, they are clustered in 3D to reconstruct objects based on the specified size range.<br>"+
"When run on a multi-channel stack, objects are mapped in the currently displayed channel and measured in all channels. Select a row in the results table and press the \"target\" button to highlight the object location in the image. "+
"</p>"+
"<ul>"+
"<li><b>Starting K</b> - the initial number of intensity sub-populations.</li>"+
"<li><b>Blur Radius</b> - the sigma value of the Gaussian blur applied to the image before segmentation. Set to 0 to disable.</li>"+
"<li><b>Object Radius</b> - the radius range of objects to be detected, used to calculate areas in 2D and volumes in 3D assuming circular and spherical objects respectively.</li>"+
"<li><b>Threshold</b> - the automatic thresholding algorithm used to determine the required mean object intensity for inclusion.</li>"+
"<li><b>Watershed</b> - apply the 2D Watershed transform to the binary image at each intensity level.</li>"+
"<li><b>Show Rejected Objects</b> - show objects that were detected but do not meet the filtering criteria. Useful when testing parameters to see what is being excluded.</li>"+
"</ul>"+
"<ul style=\"font-size:10px\">"+
"<li>1) Dufour A, Meas-Yedid V, Grassart A, and Olivo-Marin JC, \"Automated Quantification of Cell Endocytosis Using Active Contours and Wavelet\", Proc. ICPR 2008, Tampa, FL, USA.</li>"+
"<li>2) de Chaumont F, Dallongeville S, Chenouard N et al. \"Icy: an open bioimage informatics platform for extended reproducible research\" Nature Methods. 2012;9(7):690-696. doi:10.1038/nmeth.2075.</li>"+
"</ul>"+
"<p style='font-size:8px;font-style:italic;'>Copyright 2016, Richard Butler<br>"+
"HKM Segment is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. HKM Segment is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.<br>"+
"You should have received a copy of the GNU General Public License along with HKM Segment.  If not, see http://www.gnu.org/licenses/.</p>"+
"</body>"+
"</html>";

	public HKM_Segment(){
		setImage();
	}
	
	public HKM_Segment(ImagePlus image){
		setImage(image);
	}
	
	private void record(){
		Recorder.getInstance();
		if(Recorder.record){
			String args = "startK="+startK+" blur="+sigma+" minR="+minR+" maxR="+maxR+" threshold="+thresholdMethod+" watershed="+watershed;
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
		Vunit = " ("+unit+"^3)";
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
		IJ.run(proc, "Gaussian Blur...", "sigma="+sigma+" scaled stack");
	}
	
	private ArrayList<Roi> extractObjects(boolean preview){
		try{
			cells = new ArrayList<Roi>();
			Overlay ol = new Overlay();
			int start = 1;
			int end = Z;
			if(preview){
				start = imp.getZ();
				end = start;
			}
			int channel = imp.getC();
			boolean[] usedMean = new boolean[means.length];
			threshold = -1d;
			if(!thresholdMethod.equals("None")){
				IJ.setAutoThreshold(proc, thresholdMethod+" dark stack");
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
					if(watershed){
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
						//if(split.length>50000){
						//String advice = (sigma<pixW)?"\nApplying a blur of half the minimum radius may give better results.":"";
						//int ans = JOptionPane.showConfirmDialog(gui, split.length+" objects found in slice "+z+", continue?"+advice, "Continue?", JOptionPane.YES_NO_OPTION);
						//if(ans==JOptionPane.NO_OPTION){return null;}
						//}
						for(int ri=0;ri<split.length;ri++){
							Roi r = split[ri];
							int ed = (int)Math.floor(minR/4/pixW)+1;
							int blurAdjust = 0;
							if(sigma>pixW){
								blurAdjust = (int)Math.floor(sigma/pixW);
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
							if( procStats.area>=minA && procStats.area<=maxA && procStats.mean>=threshold ){
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
							else if(showBad){
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
			imp.setOverlay(ol);
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
			Overlay ol = imp.getOverlay();
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
		}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
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
	
	private static int getInt(String text){
		int i;
		try{
			i = Integer.parseInt(text);
		}catch(NumberFormatException nfe){
			IJ.error("HKM Segment",text+" is not an integer.");
			throw nfe;
		}
		return i;
	}
	private static double getDouble(String text){
		double d;
		try{
			d = Double.valueOf(text);
		}catch(NumberFormatException nfe){
			IJ.error("HKM Segment",text+" is not a number.");
			throw nfe;
		}
		return d;
	}
	
	private boolean validParameters(){
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
		if(!Arrays.asList(methods).contains(thresholdMethod)){
			IJ.error("HKM Segment", "Unknown thresholding method : "+thresholdMethod);
			return false;
		}
		return true;
	}
	
	private double measureRoi(double current){
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
	
	public JFrame showGui(){
		if(gui==null){
			listener = new ActionListener(){
				public void actionPerformed(ActionEvent ae){
					if(ae.getSource()==cancelButton){
						gui.dispose();
						return;
					}
					
					if (ae.getSource()==minMeasureButton){
						double roiL = measureRoi(minR);
						minField.setText( String.format("%.2f",roiL) );
						return;
					}
					else if (ae.getSource()==maxMeasureButton){
						double roiL = measureRoi(maxR);
						maxField.setText(String.format("%.2f",roiL));
						return;
					}
					
					startK = getInt(kField.getText());
					sigma = getDouble(blurField.getText());
					minR = getDouble(minField.getText());
					maxR = getDouble(maxField.getText());
					thresholdMethod = (String)thresholdCombo.getSelectedItem();
					watershed = watershedTick.isSelected();
					showBad = badTick.isSelected();
					
					if(!validParameters()){return;}
					
					minA = Math.PI*(minR*minR);
					if(Z>1){
						minV = (4d/3d)*Math.PI*(minR*minR*minR);
					}
					else{ minV = minA*pixD; }
					maxA = Math.PI*(maxR*maxR);
					if(ae.getSource()==previewButton){
						Runnable run = new Runnable(){
							public void run(){
								try{
									segment(true, false);
								}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
							}
						};
						new Thread(run).start();
					}
					else if(ae.getSource()==okButton){
						Runnable run = new Runnable(){
							public void run(){
								try{
									segment(false, false);
								}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
							}
						};
						new Thread(run).start();
					}
					else if (ae.getSource()==targetButton){
						if(imp==null||results==null){
							IJ.error("HKM_Segment", "This function highlights the location of the selected results table object in the current image.");
							return;
						}
						if(target==null){
							target = new TargetTable(imp, results);
						}
						target.target();
					}
					else if (ae.getSource()==helpButton){
						if(helpFrame==null){
							helpFrame = new JFrame();
							JEditorPane textPane = new JEditorPane("text/html", helpText);
							textPane.setEditable(false);
							JScrollPane scrollPane = new JScrollPane(textPane, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
							helpFrame.add(scrollPane);
							helpFrame.pack();
						}
						helpFrame.setLocationRelativeTo(null);
						helpFrame.setVisible(true);
					}
					else if(ae.getSource()==configButton){
						config.display();
					}
				}
			};
			
			gui = new JFrame("HKM Segment");
			gui.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")));
			card = new CardLayout();
			gui.setLayout(card);
			JPanel main = new JPanel();
			main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
			JPanel logoPanel = new JPanel();
			logoPanel.add(new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_borded_336x104.gif")))));
			main.add(logoPanel);
			kField = new JTextField(""+startK, 3);
			main.add(guiPanel("Starting K:", kField));
			blurField = new JTextField(""+sigma, 3);
			main.add(guiPanel("Blur Radius:", blurField, unit));
			minField = new JTextField(""+minR, 6);
			maxField = new JTextField(""+maxR, 6);
			minMeasureButton = new MButton(MButton.Type.MEASURE);
			minMeasureButton.addActionListener(listener);
			maxMeasureButton = new MButton(MButton.Type.MEASURE);
			maxMeasureButton.addActionListener(listener);
			main.add(guiPanel("Object Radius",minField, minMeasureButton, "to", maxField, maxMeasureButton, unit));
			thresholdCombo = new JComboBox<String>(methods);
			thresholdCombo.setSelectedItem(thresholdMethod);
			main.add(guiPanel("Threshold:", thresholdCombo));
			JPanel tickPanel = new JPanel();
			watershedTick = new JCheckBox("Watershed", watershed);
			tickPanel.add(watershedTick);
			badTick = new JCheckBox("Show Rejected Objects", showBad);
			tickPanel.add(badTick);
			configButton = new MButton(MButton.Type.CONFIG);
			configButton.addActionListener(listener);
			tickPanel.add(configButton);
			main.add(tickPanel);
			previewButton = new JButton("Preview");
			previewButton.addActionListener(listener);
			okButton = new JButton("OK");
			okButton.addActionListener(listener);
			cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(listener);
			targetButton = new JButton("Target");
			targetButton.addActionListener(listener);
			helpButton = new JButton("?");
			helpButton.setMargin(new Insets(0,5,0,5));
			helpButton.addActionListener(listener);
			JPanel buttonPanel = new JPanel();
			buttonPanel.add(previewButton);
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);
			buttonPanel.add(Box.createHorizontalStrut(20));
			buttonPanel.add(helpButton);
			buttonPanel.add(targetButton);
			main.add(buttonPanel);
			gui.add(main, "main");
			JPanel working = new JPanel();
			working.setBackground(Color.WHITE);
			working.setLayout(new GridLayout(1,1));
			working.add(new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("HKMworking.gif")))));
			gui.add(working, "working");
		}
		gui.pack();
		gui.setLocationRelativeTo(null);
		gui.setVisible(true);
		return gui;
	}
	
	private void setPrefs(){
		Prefs.set("HKM_Segment.minR", minR);
		Prefs.set("HKM_Segment.maxR", maxR);
		Prefs.set("HKM_Segment.sigma", sigma);
		Prefs.set("HKM_Segment.startK", startK);
		Prefs.set("HKM_Segment.thresholdMethod", thresholdMethod);
		Prefs.set("HKM_Segment.watershed", watershed);
		Prefs.set("HKM_Segment.showBad", showBad);
	}
	
	public void segment(){
		segment(false, false);
	}
	
	private void segment(final boolean preview, final boolean isMacro){
		try{
			if(!setImage()){return;}
			if(!isMacro){ card.show(gui.getContentPane(), "working"); }
			
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
			int minN = (int)Math.ceil(minA/pixW/pixW);
			means = hc.getLevels(startK, minN);
			if(means.length<1){IJ.error("Clusters could not be separated. Try decreasing the minimum radius.");return;}
			previewColours = ColourSets.heatmap(hc.getK());
		//long time0 = System.nanoTime();
			extractObjects(preview);
		//IJ.log( "extractObjects "+((System.nanoTime()-time0)/1000000000f)+" sec" );			
			if(!preview){
				double join = maxR;
				Volumiser vol = new Volumiser(imp, join, minV);
				ArrayList<Object3D> o3d = vol.getVolumes( cells );
				output(o3d);
			}
			setPrefs();
			
			if(isMacro){
				IJ.run("ROI Manager...", "");
				RoiManager rm = RoiManager.getInstance();
				for(Roi cell : cells){
					Roi olRoi = (Roi)cell.clone();
					olRoi.setPosition(0);	//remove position info
					rm.addRoi(olRoi);
				}
			}
			
		}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		finally{
			if(!isMacro){ card.show(gui.getContentPane(), "main"); }
		}
	}	
	
	public void run(String runarg){
	try{
		if(IJ.isMacro()){
			String args = Macro.getOptions();
			if(args.length()>0){
				String[] params = args.split(" ");
				for(String param : params ){
					String[] kv = param.split("=");
					if(kv[0].equals("startK")){ startK = getInt(kv[1]); }
					else if(kv[0].equals("blur")){ sigma = getDouble(kv[1]); }
					else if(kv[0].equals("minR")){ minR = getDouble(kv[1]); }
					else if(kv[0].equals("maxR")){ maxR = getDouble(kv[1]); }
					else if(kv[0].equals("threshold")){  thresholdMethod = kv[1]; }
					else if(kv[0].equals("watershed")){  watershed = Boolean.valueOf(kv[1]);}
					else if(kv[0].length()>0){
						IJ.error("HKM Segment", "Unknown argument: "+kv[0]);
						return;
					}
				}
				if(!validParameters()){return;}
				minA = Math.PI*(minR*minR);
				if(Z>1){
					minV = (4d/3d)*Math.PI*(minR*minR*minR);
				}
				else{ minV = minA; }
				maxA = Math.PI*(maxR*maxR);
			}
			showBad = false;	//don't show rejected objects when called from a macro
			segment(false, true);
		}
		else{
			showGui();
		}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
}