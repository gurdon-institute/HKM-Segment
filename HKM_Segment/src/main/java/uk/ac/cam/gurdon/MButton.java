package uk.ac.cam.gurdon;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JButton;

public class MButton extends JButton {
	private static final long serialVersionUID = 7879878090304256129L;
	private static final Insets insets = new Insets(2,2,2,2);
	private final ImageIcon icon = new ImageIcon( getClass().getResource("measure.png") );
	
	private final Dimension dim = new Dimension(icon.getIconWidth(), icon.getIconHeight());
	
	public static enum Type{
		MEASURE("measure"), CONFIG("config");
		ImageIcon icon;
		Type(String name){
			try{
			this.icon = new ImageIcon( getClass().getResource(name+".png") );
			}catch(Exception e){System.out.println("Icon Exception: "+e.toString());}
		}
	}
	
	public MButton(Type t){
		super();
		setIcon(t.icon);
		setMargin(insets);
	}
	
	public Dimension getPreferredSize(){
		return dim;
	}
	
}
