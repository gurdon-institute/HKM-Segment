package uk.ac.cam.gurdon;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import ij.Prefs;


public class HKMGUI extends JFrame {
	private static final long serialVersionUID = 1353194999994691580L;
	
	private JFrame helpFrame;
	CardLayout card;
	private JTextField kField, blurField, minField, maxField;
	private JCheckBox watershedTick, badTick, overlayToggle;
	private JButton previewButton, okButton, cancelButton, targetButton, helpButton, minMeasureButton, maxMeasureButton, configButton;
	private static final String[] methods = {"None", "Huang", "IsoData", "Li", "MaxEntropy",
											 "Mean", "Minimum", "Moments", "Otsu", "Percentile", 
											 "RenyiEntropy", "Shanbhag", "Triangle", "Yen" };
	private JComboBox<String> thresholdCombo;

	double minA, maxA, minV;
	int W, H, C, Z, k;
	boolean showOverlay = true;
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
	"<p style='font-size:8px;font-style:italic;'>Copyright 2016, 2017 Richard Butler<br>"+
	"HKM Segment is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. HKM Segment is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.<br>"+
	"You should have received a copy of the GNU General Public License along with HKM Segment.  If not, see http://www.gnu.org/licenses/.</p>"+
	"</body>"+
	"</html>";
	
	double minR = Prefs.get("HKM_Segment.minR", 5.0);
	double maxR = Prefs.get("HKM_Segment.maxR", 30.0);
	double sigma = Prefs.get("HKM_Segment.sigma", 0.0);
	int startK = (int)Prefs.get("HKM_Segment.startK", 10);
	String thresholdMethod = Prefs.get("HKM_Segment.thresholdMethod", "None");
	boolean watershed = Prefs.get("HKM_Segment.watershed", false);
	boolean showBad = Prefs.get("HKM_Segment.showBad", true);
	
	private HKM_Segment parent;
	
	
	public HKMGUI(HKM_Segment hkm){
		super("HKM Segment");
		this.parent = hkm;
		ActionListener listener = new ActionListener(){
				public void actionPerformed(ActionEvent ae){
					
					if(ae.getSource()==cancelButton){
						dispose();
						return;
					}
					if (ae.getSource()==minMeasureButton){
						double roiL = parent.measureRoi(minR);
						minField.setText( String.format("%.2f",roiL) );
						return;
					}
					else if (ae.getSource()==maxMeasureButton){
						double roiL = parent.measureRoi(maxR);
						maxField.setText(String.format("%.2f",roiL));
						return;
					}
					
					getParams();
					
					if(!parent.validParameters()){return;}
					
					minA = Math.PI*(minR*minR);
					if(Z>1){
						minV = (4d/3d)*Math.PI*(minR*minR*minR);
					}
					else{ minV = minA*parent.pixD; }
					maxA = Math.PI*(maxR*maxR);
					if(ae.getSource()==previewButton){
						Runnable run = new Runnable(){
							public void run(){
								try{
									parent.segment(true, false);
								}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
							}
						};
						new Thread(run).start();
					}
					else if(ae.getSource()==okButton){
						Runnable run = new Runnable(){
							public void run(){
								try{
									parent.segment(false, false);
									setPrefs();
								}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
							}
						};
						new Thread(run).start();
					}
					else if (ae.getSource()==targetButton){
						parent.targeter();
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
						parent.config.display();
					}
					else if(ae.getSource()==overlayToggle){
						showOverlay = overlayToggle.isSelected();
						parent.overlay(showOverlay);
					}
				}
			};
			
			
			setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")));
			card = new CardLayout();
			setLayout(card);
			JPanel main = new JPanel();
			main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
			JPanel logoPanel = new JPanel();
			logoPanel.add(new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_borded_336x104.gif")))));
			main.add(logoPanel);
			kField = new JTextField(""+startK, 3);
			main.add(HKM_Segment.guiPanel("Starting K:", kField));
			blurField = new JTextField(""+sigma, 3);
			main.add(HKM_Segment.guiPanel("Blur Radius:", blurField, parent.unit));
			minField = new JTextField(""+minR, 6);
			maxField = new JTextField(""+maxR, 6);
			minMeasureButton = new MButton(MButton.Type.MEASURE);
			minMeasureButton.addActionListener(listener);
			maxMeasureButton = new MButton(MButton.Type.MEASURE);
			maxMeasureButton.addActionListener(listener);
			main.add(HKM_Segment.guiPanel("Object Radius",minField, minMeasureButton, "to", maxField, maxMeasureButton, parent.unit));
			thresholdCombo = new JComboBox<String>(methods);
			thresholdCombo.setSelectedItem(thresholdMethod);
			main.add(HKM_Segment.guiPanel("Threshold:", thresholdCombo));
			JPanel tickPanel = new JPanel();
			watershedTick = new JCheckBox("Watershed", watershed);
			tickPanel.add(watershedTick);
			badTick = new JCheckBox("Show Rejected Objects", showBad);
			tickPanel.add(badTick);
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
			
			JPanel optionPanel = new JPanel();
			overlayToggle = new JCheckBox("Show Overlay", showOverlay);
			overlayToggle.addActionListener(listener);
			optionPanel.add(overlayToggle);
			configButton = new MButton(MButton.Type.CONFIG);
			configButton.addActionListener(listener);
			optionPanel.add(configButton);
			main.add(optionPanel);
			
			JPanel buttonPanel = new JPanel();
			buttonPanel.add(previewButton);
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);
			buttonPanel.add(Box.createHorizontalStrut(20));
			buttonPanel.add(helpButton);
			buttonPanel.add(targetButton);
			main.add(buttonPanel);
			add(main, "main");
			JPanel working = new JPanel();
			working.setBackground(Color.WHITE);
			working.setLayout(new GridLayout(1,1));
			working.add(new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("HKMworking.gif")))));
			add(working, "working");
		
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	
}
	
	private void getParams() {
		startK = HKM_Segment.getInt(kField.getText());
		sigma = HKM_Segment.getDouble(blurField.getText());
		minR = HKM_Segment.getDouble(minField.getText());
		maxR = HKM_Segment.getDouble(maxField.getText());
		thresholdMethod = (String)thresholdCombo.getSelectedItem();
		watershed = watershedTick.isSelected();
		showBad = badTick.isSelected();
	}

	private void setPrefs() {
		Prefs.set("HKM_Segment.minR", minR);
		Prefs.set("HKM_Segment.maxR", maxR);
		Prefs.set("HKM_Segment.sigma", sigma);
		Prefs.set("HKM_Segment.startK", startK);
		Prefs.set("HKM_Segment.thresholdMethod", thresholdMethod);
		Prefs.set("HKM_Segment.watershed", watershed);
		Prefs.set("HKM_Segment.showBad", showBad);
	}
	
}
