import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JButton;

public class MButton extends JButton {
	private static final long serialVersionUID = 7879878090304256129L;
	private final ImageIcon icon = new ImageIcon( getClass().getResource("/measure.png") );
	private static final Insets insets = new Insets(2,2,2,2);
	private final Dimension dim = new Dimension(icon.getIconWidth(), icon.getIconHeight());
	
	public MButton(){
		super();
		setIcon(icon);
		setMargin(insets);
	}
	
	public Dimension getPreferredSize(){
		return dim;
	}
	
}
