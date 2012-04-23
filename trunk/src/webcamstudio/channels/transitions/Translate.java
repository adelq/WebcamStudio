/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.channels.transitions;

import webcamstudio.streams.Stream;
import webcamstudio.util.Tools;

/**
 *
 * @author patrick
 */
public class Translate extends Transition{
    public Translate(Stream source){
        super(source);
    }
    @Override
    protected void execute() {
        int oldX = source.getX();
        int oldY = source.getY();
        int newX = channel.getX();
        int newY = channel.getY();
        int deltaX = newX-oldX;
        int deltaY = newY - oldY;
        int rate = source.getRate();
        int totalFrames = rate * 1;
        for (int i = 0; i<totalFrames;i++){
            source.setX(oldX + (i*deltaX/totalFrames));
            source.setY(oldY+(i*deltaY/totalFrames));
            Tools.sleep(1000/rate);
        }
        
    }
    
}
