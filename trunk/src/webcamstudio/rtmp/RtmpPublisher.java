/*
 * Flazr <http://flazr.com> Copyright (C) 2009  Peter Thomas.
 *
 * This file is part of Flazr.
 *
 * Flazr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flazr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Flazr.  If not, see <http://www.gnu.org/licenses/>.
 */

package webcamstudio.rtmp;

import webcamstudio.io.f4v.F4vReader;
import webcamstudio.io.flv.FlvReader;
//import webcamstudio.rtmp.server.RtmpServer;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import webcamstudio.rtmp.server.RtmpServer;

public abstract class RtmpPublisher {


    private final Timer timer;
    private final int timerTickSize;
    private final boolean usingSharedTimer;
    private final boolean aggregateModeEnabled;

    private final RtmpReader reader;
    private int streamId;
    private long startTime;    
    private long seekTime;
    private long timePosition;
    private int currentConversationId;    
    private int playLength = -1;
    private boolean paused;
    private int bufferDuration;

    public static class Event {

        private final int conversationId;

        public Event(final int conversationId) {
            this.conversationId = conversationId;
        }

        public int getConversationId() {
            return conversationId;
        }

    }

    public RtmpPublisher(final RtmpReader reader, final int streamId, final int bufferDuration,
            boolean useSharedTimer, boolean aggregateModeEnabled) {
        this.aggregateModeEnabled = aggregateModeEnabled;
        this.usingSharedTimer = useSharedTimer;
        if(useSharedTimer) {
            timer = RtmpServer.TIMER;
        } else {
            timer = new HashedWheelTimer(RtmpConfig.TIMER_TICK_SIZE, TimeUnit.MILLISECONDS);
        }
        timerTickSize = RtmpConfig.TIMER_TICK_SIZE;
        this.reader = reader;
        this.streamId = streamId;
        this.bufferDuration = bufferDuration;
    }

    public static RtmpReader getReader(String path) {
        if(path.toLowerCase().startsWith("mp4:")) {
            return new F4vReader(path.substring(4));
        } else if (path.toLowerCase().endsWith(".f4v")) {
            return new F4vReader(path);
        } else {
            return new FlvReader(path);
        }
    }

    public boolean isStarted() {
        return currentConversationId > 0;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setBufferDuration(int bufferDuration) {
        this.bufferDuration = bufferDuration;
    }

    public boolean handle(final MessageEvent me) {
        if(me.getMessage() instanceof Event) {
            final Event pe = (Event) me.getMessage();
            if(pe.conversationId != currentConversationId) {
                return true;
            }
            write(me.getChannel());
            return true;
        }
        return false;
    }

    public void start(final Channel channel, final int seekTime, final int playLength, final RtmpMessage ... messages) {
        this.playLength = playLength;
        start(channel, seekTime, messages);
    }

    public void start(final Channel channel, final int seekTimeRequested, final RtmpMessage ... messages) {
        paused = false;
        currentConversationId++;
        startTime = System.currentTimeMillis();
        if(seekTimeRequested >= 0) {
            seekTime = reader.seek(seekTimeRequested);
        } else {
            seekTime = 0;
        }
        timePosition = seekTime;
        for(final RtmpMessage message : messages) {
            writeToStream(channel, message);
        }
        for(final RtmpMessage message : reader.getStartMessages()) {
            writeToStream(channel, message);
        }
        write(channel);
    }

    private void writeToStream(final Channel channel, final RtmpMessage message) {
        if(message.getHeader().getChannelId() > 2) {
            message.getHeader().setStreamId(streamId);
            message.getHeader().setTime((int) timePosition);
        }
        channel.write(message);
    }

    private void write(final Channel channel) {
        if(!channel.isWritable()) {
            return;
        }
        final long writeTime = System.currentTimeMillis();
        final RtmpMessage message;
        synchronized(reader) { //=============== SYNCHRONIZE ! =================
            if(reader.hasNext()) {
                message = reader.next();
            } else {
                message = null;
            }
        } //====================================================================
        if (message == null || playLength >= 0 && timePosition > (seekTime + playLength)) {
            stop(channel);
            return;
        }
        final long elapsedTime = System.currentTimeMillis() - startTime;
        final long elapsedTimePlusSeek = elapsedTime + seekTime;
        final double clientBuffer = timePosition - elapsedTimePlusSeek;
        if(aggregateModeEnabled && clientBuffer > timerTickSize) { // TODO cleanup
            reader.setAggregateDuration((int) clientBuffer);
        } else {
            reader.setAggregateDuration(0);
        }
        final RtmpHeader header = message.getHeader();
        final double compensationFactor = clientBuffer / (bufferDuration + timerTickSize);
        final long delay = (long) ((header.getTime() - timePosition) * compensationFactor);
        timePosition = header.getTime();
        header.setStreamId(streamId);
        final ChannelFuture future = channel.write(message);
        future.addListener(new ChannelFutureListener() {
            @Override public void operationComplete(final ChannelFuture cf) {
                final long completedIn = System.currentTimeMillis() - writeTime;
                if(completedIn > 2000) {
                    System.out.println("WS4GL-"+getClass().getName()+": WARN Channel busy? time taken to write last message: " + completedIn);
                }
                final long delayToUse = clientBuffer > 0 ? delay - completedIn : 0;
                fireNext(channel, delayToUse);
            }
        });
    }

    public void fireNext(final Channel channel, final long delay) {
        final Event readyForNext = new Event(currentConversationId);
        if(delay > timerTickSize) {
            timer.newTimeout(new TimerTask() {
                @Override public void run(Timeout timeout) {
                    if(readyForNext.conversationId != currentConversationId) {
                        System.out.println("WS4GL-"+getClass().getName()+": WARN Pending 'next' event found obsolete, aborting: ");
                        return;
                    }
                    Channels.fireMessageReceived(channel, readyForNext);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            Channels.fireMessageReceived(channel, readyForNext);
        }
    }

    public void pause() {
        paused = true;
        currentConversationId++;
    }

    private void stop(final Channel channel) {
        currentConversationId++;
        final long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("WS4GL-"+getClass().getName()+": Stopping after " + elapsedTime + " ms");
        for(RtmpMessage message : getStopMessages(timePosition)) {
            writeToStream(channel, message);
        }
    }

    public void close() {
        if(!usingSharedTimer) {
            timer.stop();
        }
        reader.close();
    }

    protected abstract RtmpMessage[] getStopMessages(long timePosition);

}