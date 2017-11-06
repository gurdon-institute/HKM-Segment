package uk.ac.cam.gurdon;
import java.awt.Color;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ColorProcessor;

public class ColourSets {

	
	public static Color[] random(int n){
		return random(n, false);
	}
	public static Color[] random(int n, boolean showColours){
		ArrayList<Color> colourList = new ArrayList<Color>();
		ColorProcessor cp = null; int x = 0;
		if(showColours){ cp = new ColorProcessor(n*4, 100); }
		float step = 3f/n;
		for(float f=0f;f<=1f;f+=step){
			float v1 = (float)Math.random();
			float v2 = (float)Math.random();
			float v3 = (float)Math.random();
			if( v1+v2+v3 < 1 ){ v1 = 1f-v1; }
			colourList.add( new Color( v1, v3, v2) );
			colourList.add( new Color( v2, v1, v3) );
			colourList.add( new Color( v3, v2, v1) );
			if(showColours){
				cp.setColor(colourList.get(colourList.size()-1));	cp.setRoi(new Roi(x, 0, 4, 100));	cp.fill();
				cp.setColor(colourList.get(colourList.size()-1));	cp.setRoi(new Roi(x+4, 0, 4, 100));	cp.fill();
				cp.setColor(colourList.get(colourList.size()-1));	cp.setRoi(new Roi(x+8, 0, 4, 100));	cp.fill();
				x += 12;
			}
		}
		if(showColours){
			ImagePlus cimp = new ImagePlus("colours", cp);
			cimp.show();
		}
		return colourList.toArray(new Color[n]);
	}
	
	public static Color[] heatmap(int n){
		Color[] colours = new Color[n];
		for(int m=0; m<n; m++){
			float f = 2f * (m+1)/n;
			int b = (int)(Math.max(0, 255*(1f - f)));
			int r = (int)(Math.max(0, 255*(f - 1f)));
			int g = 255 - r - b;
			colours[m] = new Color( r, g, b );
		}
		return colours;
	}
	
	
}
