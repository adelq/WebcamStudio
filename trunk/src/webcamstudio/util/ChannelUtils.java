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

package webcamstudio.util;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import org.jboss.netty.channel.ExceptionEvent;

public class ChannelUtils {


    public static void exceptionCaught(final ExceptionEvent e) {
        if (e.getCause() instanceof ClosedChannelException) {
        } else if(e.getCause() instanceof IOException) {
        } else {
        }
        if(e.getChannel().isOpen()) {
            e.getChannel().close();
        }
    }

}
