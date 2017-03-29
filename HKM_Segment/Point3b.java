

//3D point class to replace vecmath.Point3d because Rasha can't install java3d

public class Point3b {
public double x,y,z;

	public Point3b() {
		
	}
	
	public Point3b(double x, double y, double z){
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public double distance(Point3b a) throws NullPointerException{
		return Math.sqrt( ((a.x-x)*(a.x-x))+((a.y-y)*(a.y-y))+((a.z-z)*(a.z-z)) );
	}

}
