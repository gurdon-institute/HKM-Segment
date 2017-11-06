package uk.ac.cam.gurdon;
import ij.gui.Roi;


public class Object3D{
public Point3b centroid;
public Roi[] rois;
public double volume;

	public Object3D(Point3b p, Roi[] r, double v){
		this.centroid = p;
		this.rois = r;
		this.volume = v;
	}

}