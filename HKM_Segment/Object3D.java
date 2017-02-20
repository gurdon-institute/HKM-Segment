import ij.gui.Roi;

import javax.vecmath.Point3d;


public class Object3D{
public Point3d centroid;
public Roi[] rois;
public double volume;

	public Object3D(Point3d p, Roi[] r, double v){
		this.centroid = p;
		this.rois = r;
		this.volume = v;
	}

}