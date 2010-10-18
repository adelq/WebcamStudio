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

import webcamstudio.rtmp.message.Metadata;
import java.util.ArrayList;
import java.util.List;

public class LoopedReader implements RtmpReader {


    private final int loopCount;
    private final RtmpReader reader;
    private long timePosition;
    private double duration = -1;
    private int loopsCompleted = 0;
    private final Metadata metadata;
    private RtmpMessage[] startMessages;

    public LoopedReader(final RtmpReader reader, final int loopCount) {
        this.reader = reader;
        this.loopCount = loopCount;        
        this.metadata = reader.getMetadata();
        double originalDuration = metadata.getDuration();
        if(originalDuration > 0) {
            double durationSeconds = originalDuration * loopCount;
            metadata.setDuration(durationSeconds);            
        } else {
            metadata.setDuration(-1);
        }        
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public RtmpMessage[] getStartMessages() {
        if(startMessages == null) {
            final List<RtmpMessage> list = new ArrayList<RtmpMessage>();
            list.add(metadata);
            for(final RtmpMessage message : reader.getStartMessages()) {
                if(!message.getHeader().isMetadata()) {
                    list.add(message);
                }
            }
            startMessages = list.toArray(new RtmpMessage[list.size()]);
        }
        return startMessages;
    }

    @Override
    public void setAggregateDuration(int targetDuration) {
        reader.setAggregateDuration(targetDuration);
    }

    @Override
    public long getTimePosition() {
        return timePosition;
    }

    @Override
    public long seek(long timePosition) {
        if(duration < 0 || timePosition < duration) {
            return reader.seek(timePosition);
        }                               
        loopsCompleted = (int) Math.floor(timePosition / duration);
        return reader.seek((long) (timePosition % duration));
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public boolean hasNext() {
        if(reader.hasNext()) {
            return true;
        }
        if(loopsCompleted == 0 && duration == -1) {
            duration = timePosition;
        }
        loopsCompleted++;
        if(loopsCompleted < loopCount) {
            reader.seek(0);
            return true;
        }
        return false;
    }

    @Override
    public RtmpMessage next() {
        final RtmpMessage message = reader.next();
        if(loopsCompleted == 0) {
            timePosition = message.getHeader().getTime();
            return message;
        }        
        timePosition = (long) duration * loopsCompleted + message.getHeader().getTime();        
        message.getHeader().setTime((int) timePosition); // TODO find and cleanup all these (int) casts
        return message;
    }

}
