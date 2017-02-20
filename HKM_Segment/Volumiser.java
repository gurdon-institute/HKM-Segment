import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;

import javax.vecmath.Point3d;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageStatistics;


public class Volumiser{
private ImagePlus imp;
private int W, H, Z;
private double pixW, pixD, joinR, minV;

	public Volumiser(ImagePlus imp, double joinR, double minV){
		this.imp = imp;
		this.joinR = joinR;
		this.minV = minV;
		this.W = imp.getWidth();
		this.H = imp.getHeight();
		this.Z = imp.getNSlices();
		Calibration cal = imp.getCalibration();
		this.pixW = cal.pixelWidth;
		this.pixD = cal.pixelDepth;
	}
	
	private Point3d getCoord(Roi r){
		Rectangle rect = r.getBounds();
		double rx = ( rect.x+(rect.width/2) ) * pixW;
		double ry = ( rect.y+(rect.height/2) ) * pixW;
		double rz = r.getPosition() * pixD;
		return new Point3d(rx, ry, rz);
	}
	
	public ArrayList<Object3D> getVolumes(ArrayList<Roi> parts){
		return getVolumes(parts.toArray(new Roi[parts.size()]));
	}
	
	public ArrayList<Object3D> getVolumes(Roi[] parts){
	try{
		ArrayList<Object3D> objects = new ArrayList<Object3D>();
		int[] partI = new int[parts.length];
		Point3d[] partCoords = new Point3d[parts.length];
		ArrayList<Point3d> centroids = new ArrayList<Point3d>();
		for(int i=0;i<parts.length;i++){
			partI[i] = i;
			partCoords[i] = getCoord(parts[i]);
			centroids.add(partCoords[i]);
		}
				
		boolean done = false;
		int its = 0;
		while(!done && its<100){
			done = true;
			its ++;
			int changei = 0;
			int zeron = 0;
			int mover = 0;
			int joiner = 0;
			for(int p=0;p<parts.length;p++){
				Point3d partCoord = partCoords[p];
				double minCost = Double.POSITIVE_INFINITY;
				int mini = -1;
				for(int c=0;c<centroids.size();c++){
					if(centroids.get(c) == null){
						continue;
					}
					double dist = Math.sqrt( Math.pow(partCoord.x-centroids.get(c).x,2) + Math.pow(partCoord.y-centroids.get(c).y,2)+ Math.pow(partCoord.z-centroids.get(c).z,2) );
					if(dist<minCost && dist<joinR){
						minCost = dist;
						mini = c;
					}
				}
				if(mini != -1 && partI[p] != mini){
					partI[p] = mini;
					changei += 1;
					done = false;
				}
			}
			ArrayList<Point3d> keepC = new ArrayList<Point3d>();
			for(int c=0;c<centroids.size();c++){
				if(centroids.get(c) == null){
					continue;
				}
				double cx=0d; double cy=0d; double cz=0d; int n=0;
				for(int i=0;i<parts.length;i++){
					if(partI[i] == c){
						Point3d partCoord = getCoord(parts[i]);
						cx += partCoord.x;
						cy += partCoord.y;
						cz += partCoord.z;
						n++;
					}
				}
				if(n==0){
					centroids.set(c, null);
					zeron++;
					done = false;
				}
				else{
					keepC.add( new Point3d(cx/n, cy/n, cz/n) );
					int a = keepC.size()-1;
					double moved = Math.sqrt( Math.pow(keepC.get(a).x-centroids.get(c).x,2) + Math.pow(keepC.get(a).y-centroids.get(c).y,2) + Math.pow(keepC.get(a).z-centroids.get(c).z,2) );
					if(moved>pixW){		//movement greater than the smallest distance between adjacent pixels -> not converged
						done = false;
						mover++;
					}
				}
			}
			centroids = new ArrayList<Point3d>(keepC);
			keepC = new ArrayList<Point3d>();
			for(int i1=0;i1<centroids.size();i1++){
				Point3d c1 = centroids.get(i1);
				if(c1==null){continue;}
				double volx = c1.x;
				double voly = c1.y;
				double volz = c1.z;
				int n = 1;
				for(int i2=i1+1;i2<centroids.size();i2++){
					Point3d c2 = centroids.get(i2);
					if( c2==null ){
						continue;
					}
					double dist = Math.sqrt( (Math.pow(c1.x-c2.x,2) + Math.pow(c1.y-c2.y,2) + Math.pow(c1.z-c2.z,2)) );
					if(dist<joinR){
						volx += c2.x;
						voly += c2.y;
						volz += c2.z;
						n++;
						centroids.set(i2, null);
						joiner++;
						done = false;
					}
				}
				if(n>0){
					keepC.add( new Point3d(volx/n, voly/n, volz/n) );
				}
			}
			centroids = new ArrayList<Point3d>(keepC);
			//IJ.log("iteration "+its+" , "+changei+" changed , "+zeron+" zeros , "+mover+" moved, "+joiner+" joined");
		}
		for(int c=0;c<centroids.size();c++){
			double volume = 0d;
			ArrayList<Roi> rois = new ArrayList<Roi>();
			for(int i=0;i<parts.length;i++){
				if(partI[i] == c){
					imp.setRoi(parts[i]);
					ImageStatistics stats = imp.getStatistics();
					double area = stats.area;
					volume += area;
					rois.add(parts[i]);
				}
			}

			if(volume>=minV){
				Roi[] roiArr = rois.toArray(new Roi[rois.size()]);
				objects.add(new Object3D(centroids.get(c), roiArr, volume));
			}
		}
		imp.killRoi();
		return objects;
	}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return null;
	}

}