/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package webcamstudio.streams;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import webcamstudio.channels.transitions.Transition;
import webcamstudio.sources.effects.Effect;

/**
 *
 * @author patrick
 */
public class SourceChannel {

    private int x = 0;
    private int y = 0;
    private int capWidth = 0;
    private int capHeight = 0;
    private int width = 0;
    private int height = 0;
    private int opacity = 0;
    private float volume = 0;
    private int zorder = 0;
    private String text = "";
    private String font = "";
    private int color = 0;
    private String name = "";
    boolean isPlaying = false;
    ArrayList<Effect> effects = new ArrayList<Effect>();
    private boolean followMouse = false;
    private int captureX = 0;
    private int captureY = 0;
    ArrayList<Transition> startTransitions = new ArrayList<Transition>();
    ArrayList<Transition> endTransitions = new ArrayList<Transition>();

    private SourceChannel() {
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        name = n;
    }

    public static SourceChannel getChannel(String channelName, Stream stream) {
        SourceChannel s = new SourceChannel();
        s.x = stream.x;
        s.y = stream.y;
        s.width = stream.width;
        s.height = stream.height;
        s.opacity = stream.opacity;
        s.effects.addAll(stream.effects);
        s.startTransitions.addAll(stream.startTransitions);
        s.endTransitions.addAll(stream.endTransitions);
        s.volume = stream.volume;
        s.zorder = stream.zorder;
        s.name = channelName;
        s.isPlaying = stream.isPlaying();
        s.capHeight = stream.captureHeight;
        s.capWidth = stream.captureWidth;
        if (stream instanceof SourceText) {
            SourceText st = (SourceText) stream;
            s.text = st.content;
            s.font = st.fontName;
            s.color = st.color;

        } else if (stream instanceof SourceDesktop) {
            SourceDesktop sd = (SourceDesktop) stream;
            s.followMouse = sd.followMouse;
//            s.captureX = sd.captureX;
//            s.captureY = sd.captureY;
        }
        return s;
    }

    public void apply(final Stream s) {
        final SourceChannel instance = this;
        new Thread(new Runnable() {

            @Override
            public void run() {
                ExecutorService pool = java.util.concurrent.Executors.newCachedThreadPool();
                for (Transition t : s.endTransitions) {
                    System.out.println(t.getClass().getName());
                    pool.submit(t.run(instance));
                }
                pool.shutdown();
                try {
                    pool.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(SourceChannel.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (isPlaying) {
                    if (!s.isPlaying()) {
                        s.read();
                        s.updateStatus();
                    }
                    pool = java.util.concurrent.Executors.newCachedThreadPool();
                    for (Transition t : instance.startTransitions) {
                        pool.submit(t.run(instance));
                    }
                    pool.shutdown();
                    try {
                        pool.awaitTermination(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(SourceChannel.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else {
                    if (s.isPlaying()) {
                        s.stop();
                        s.updateStatus();
                    }
                }

                s.x = getX();
                s.y = getY();
                s.width = getWidth();
                s.height = getHeight();
                s.opacity = getOpacity();
                s.effects.clear();
                s.effects.addAll(effects);
                s.startTransitions.clear();
                s.startTransitions.addAll(startTransitions);
                s.endTransitions.clear();
                s.endTransitions.addAll(endTransitions);
                s.volume = getVolume();
                s.zorder = getZorder();
                s.captureHeight = getCapHeight();
                s.captureWidth = getCapWidth();
                if (s instanceof SourceText) {
                    SourceText st = (SourceText) s;
                    st.content = getText();
                    st.fontName = getFont();
                    st.color = getColor();
                    st.updateContent(getText());
                } else if (s instanceof SourceDesktop) {
                    SourceDesktop sd = (SourceDesktop) s;
                    sd.followMouse = isFollowMouse();
//                    sd.captureX = getCaptureX();
//                    sd.captureY = getCaptureY();
                }
                s.updateStatus();
            }
        }).start();

    }

    /**
     * @return the x
     */
    public int getX() {
        return x;
    }

    /**
     * @return the y
     */
    public int getY() {
        return y;
    }

    /**
     * @return the capWidth
     */
    public int getCapWidth() {
        return capWidth;
    }

    /**
     * @return the capHeight
     */
    public int getCapHeight() {
        return capHeight;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the opacity
     */
    public int getOpacity() {
        return opacity;
    }

    /**
     * @return the volume
     */
    public float getVolume() {
        return volume;
    }

    /**
     * @return the zorder
     */
    public int getZorder() {
        return zorder;
    }

    /**
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * @return the font
     */
    public String getFont() {
        return font;
    }

    /**
     * @return the color
     */
    public int getColor() {
        return color;
    }

    /**
     * @return the followMouse
     */
    public boolean isFollowMouse() {
        return followMouse;
    }

    /**
     * @return the captureX
     */
    public int getCaptureX() {
        return captureX;
    }

    /**
     * @return the captureY
     */
    public int getCaptureY() {
        return captureY;
    }
}
