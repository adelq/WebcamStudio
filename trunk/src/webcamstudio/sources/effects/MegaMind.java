/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.sources.effects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.prefs.Preferences;
import javax.swing.JPanel;

/**
 *
 * @author pballeux
 */
public class MegaMind extends Effect {

    private com.jhlabs.image.CircleFilter filter = new com.jhlabs.image.CircleFilter();
    private RGB rgb = new RGB();
    @Override
    public void applyEffect(BufferedImage img) {
        filter.setHeight(img.getHeight());
        filter.setCentreY(1);
        rgb.setGThreshold(0);
        rgb.setRThreshold(0);
        Graphics2D buffer = img.createGraphics();
        BufferedImage temp = filter.filter(img, null);
        rgb.applyEffect(temp);
        buffer.setBackground(new Color(0, 0, 0, 0));
        buffer.clearRect(0, 0, img.getWidth(), img.getHeight());
        buffer.drawImage(temp, 0, 0, img.getWidth(), img.getHeight(), 0, 0, temp.getWidth(), temp.getHeight(), null);
        buffer.dispose();

    }

    @Override
    public JPanel getControl() {
        return null;
    }

    @Override
    public void applyStudioConfig(Preferences prefs) {
    }

    @Override
    public void loadFromStudioConfig(Preferences prefs) {
        
    }


  
}
