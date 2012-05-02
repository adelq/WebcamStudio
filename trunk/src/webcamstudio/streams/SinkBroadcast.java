/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.streams;

import java.awt.image.BufferedImage;
import webcamstudio.ffmpeg.ProcessRenderer;
import webcamstudio.ffmpeg.FME;
import webcamstudio.mixers.MasterMixer;

/**
 *
 * @author patrick
 */
public class SinkBroadcast extends Stream {

    private ProcessRenderer capture = null;
    private FME fme = null;
    private boolean isPlaying = false;
    public SinkBroadcast(FME fme) {
        this.fme=fme;
        name=fme.getName();
        url = fme.getUrl()+"/"+fme.getStream();
    }
    @Override
    public String getName(){
        return name;
    }
    @Override
    public void read() {
        isPlaying=true;
        rate = MasterMixer.getInstance().getRate();
        captureWidth = MasterMixer.getInstance().getWidth();
        captureHeight = MasterMixer.getInstance().getHeight();
        rate = MasterMixer.getInstance().getRate();
        capture = new ProcessRenderer(this,fme,"broadcast");
        capture.write();
    }

    @Override
    public void stop() {
        isPlaying=false;
        if  (capture!=null){
            capture.stop();
            capture=null;
        }
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public BufferedImage getPreview() {
        return null;
    }

    @Override
    public boolean hasAudio() {
        return true;
    }

    @Override
    public boolean hasVideo() {
        return true;
    }
}
