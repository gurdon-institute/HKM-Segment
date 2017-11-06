package uk.ac.cam.gurdon;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

import ij.Prefs;

public class HKMConfig extends JDialog implements ActionListener{
	private static final long serialVersionUID = 36214840990066339L;
	private JSpinner strokeSpin, fontSpin;
	private float prevStrokeW;
	private int prevFontS;
	public float strokeW = (float)Prefs.get("HKM_Segment.strokeW", 1f);
	public int fontS = (int)Prefs.get("HKM_Segment.fontS", 12);

	public Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, fontS);
	public BasicStroke stroke = new BasicStroke( strokeW );
	public BasicStroke dottedStroke = new BasicStroke( strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[] {strokeW*4, strokeW*4}, 0f );
	
	
	protected HKMConfig(){
		super((Frame)null, "HKM Config", false);
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")));
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		add(HKM_Segment.guiPanel("Overlay Options"));
		SpinnerModel sm = new SpinnerNumberModel(strokeW, 0.5f, 10f, 0.5f);
		strokeSpin = new JSpinner(sm);
		add(HKM_Segment.guiPanel("Stroke Width:", strokeSpin));
		SpinnerModel fm = new SpinnerNumberModel(fontS, 6, 32, 1);
		fontSpin = new JSpinner(fm);
		add(HKM_Segment.guiPanel("Font Size:", fontSpin));
		JButton ok = new JButton("OK");
		ok.addActionListener(this);
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		add(HKM_Segment.guiPanel(ok, cancel));
	}
	
	public void display(){
		prevStrokeW = strokeW;
		prevFontS = fontS;
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().equals("OK")){
			strokeW = ((Double) strokeSpin.getValue()).floatValue();
			fontS = (int)fontSpin.getValue();
			labelFont = new Font(Font.SANS_SERIF, Font.BOLD, fontS);
			stroke = new BasicStroke(strokeW);
			dottedStroke = new BasicStroke( strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[] {strokeW*4, strokeW*4}, 0f );
			Prefs.set("HKM_Segment.strokeW", strokeW);
			Prefs.set("HKM_Segment.fontS", fontS);
		}
		else if(e.getActionCommand().equals("Cancel")){
			strokeW = prevStrokeW;
			fontS = prevFontS;
		}
		setVisible(false);
	}
	
}
