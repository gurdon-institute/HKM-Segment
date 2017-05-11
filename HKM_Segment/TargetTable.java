import ij.*;
import ij.measure.*;
import ij.text.*;
import ij.gui.*;

import java.awt.*;

import java.util.*;


public class TargetTable{
private ImagePlus imp;
private Calibration cal;
private TextPanel textPanel;
private String[] head;
private double markR;

	public TargetTable(ImagePlus imp, ResultsTable results){
	try{
		this.imp = imp;
		cal = imp.getCalibration();
		markR = 20*cal.pixelWidth;
		String headStr = results.getColumnHeadings();	
		this.head = headStr.split("\\t");
		
		Window[] win = ImageWindow.getWindows();
		for(int w=0;w<win.length;w++){	//this is ridiculous but there is no sensible way to get the TextPanel from a ResultsTable other than the official "Results" one
			if(win[w] instanceof TextWindow){
				TextPanel tp = ((TextWindow)win[w]).getTextPanel();
				if(tp.getResultsTable()!=null && tp.getText().length()>0){
					this.textPanel = tp;
					break;
				}
			}
		}
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
	}
	
	public void target(){
	try{
		if(textPanel==null){
			IJ.error("No TextPanel.");
		}
		int start = textPanel.getSelectionStart();
		if(start==-1){
			IJ.error("No row selected in table.");
			return;
		}
		else if(textPanel.getLine(start)==null){
			IJ.error("Selected row "+start+" is null.");
			return;
		}
		else{
			String[] line = textPanel.getLine(start).split("\\t");
			double x = -1;	double y = -1; int z = -1;
			for(int i=0;i<head.length;i++){
					 if(head[i].equals("X")){x = Double.valueOf(line[i])/cal.pixelWidth;}
				else if(head[i].equals("Y")){y = Double.valueOf(line[i])/cal.pixelWidth;}
				else if(head[i].equals("Z")){z = (int)Math.round(Double.valueOf(line[i])/cal.pixelDepth);}
			}
			if(z>0){
				imp.setPosition(imp.getC(), z, 1);
			}
			if(x>=0&&y>=0){
				final OvalRoi marker = new OvalRoi(  x-markR , y-markR , 2*markR , 2*markR );
				marker.setStrokeWidth(100);
				marker.setStrokeColor(Color.RED);
				imp.setRoi(marker);
				final Timer timer = new Timer();
				TimerTask task = new TimerTask(){
					private int count = 0;
					  public void run() {
						  if(count<50){
							  marker.setStrokeWidth(100-(2*count));
							  imp.setRoi(marker);
						  }
						  else{
							  IJ.run(imp, "Select None", "");
							  timer.cancel();
						  }
						  count++;
					  }
				};
				timer.schedule(task, 5, 5 );
			}
		}
	}catch(Exception except){IJ.log("~~~~~"+except.toString()+"~~~~~\n"+Arrays.toString(except.getStackTrace()).replace(",","\n")+"\n~~~~~~~~~~");}
	}
}