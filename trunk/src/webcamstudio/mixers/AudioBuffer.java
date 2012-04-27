/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.mixers;

import java.util.ArrayList;
import webcamstudio.util.Tools;

/**
 *
 * @author patrick
 */
public class AudioBuffer {

    private ArrayList<byte[]> buffer = new ArrayList<byte[]>();
    private int bufferSize = MasterMixer.BUFFER_SIZE;
    private static final long TIMEOUT = 5000;
    private boolean abort = false;
    int currentIndex = 0;
    long framePushed = 0;
    long framePopped = 0;

    public AudioBuffer(int rate) {
        for (int i = 0; i < bufferSize; i++) {
            buffer.add(new byte[(44100 * 2 * 2) / rate]);
        }
    }
    public AudioBuffer(int rate,int bufferSize) {
        this.bufferSize=bufferSize;
        for (int i = 0; i < bufferSize; i++) {
            buffer.add(new byte[(44100 * 2 * 2) / rate]);
        }
    }

    public void push(byte[] data) {
        while (!abort && (framePushed - framePopped) >= bufferSize) {
            Tools.sleep(30);
        }
        currentIndex++;
        currentIndex = currentIndex % bufferSize;
        byte[] d = buffer.get(currentIndex);
        for (int i = 0; i < d.length; i++) {
            d[i] = data[i];
        }
        framePushed++;

    }
    public void doneUpdate(){
        currentIndex++;
        currentIndex = currentIndex % bufferSize;
        framePushed++;
    }    
    public byte[] getAudioToUpdate(){
        while (!abort && (framePushed - framePopped) >= bufferSize) {
            Thread.yield();
            Tools.sleep(30);
        }
        return buffer.get((currentIndex+1)%bufferSize);
    }
    public byte[] pop() {
        long mark = System.currentTimeMillis();
        while (!abort && framePopped >= framePushed) {
            if (System.currentTimeMillis()-mark >= TIMEOUT){
                //Resetting everyting;
                System.err.println("Resetting audio!");
                currentIndex=0;
                framePopped=0;
                framePushed=0;
                break;
            }
            Thread.yield();
            Tools.sleep(10);
        }
        framePopped++;
        return buffer.get(currentIndex);
    }

    public void abort() {
        abort = true;
        currentIndex = 0;
    }

    public void clear() {
        abort = false;
        currentIndex = 0;
    }
}
