/*
 * synergy -- mouse and keyboard sharing utility
 * Copyright (C) 2010 Shaun Patterson
 * Copyright (C) 2010 The Synergy Project
 * Copyright (C) 2009 The Synergy+ Project
 * Copyright (C) 2002 Chris Schoeneman
 *
 * This package is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * found in the file COPYING that should have accompanied this file.
 *
 * This package is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.synergy.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.synergy.base.Event;
import org.synergy.base.EventQueue;
import org.synergy.base.EventType;
import org.synergy.base.utils.Log;
import org.synergy.io.Stream;

public class TCPSocket implements Stream {
    private Socket socket;

    public TCPSocket(Socket socket) {
        this.socket = socket;

        try {
            // Turn off Nagle's algorithm and set traffic type (RFC 1349) to minimize delay
            // to avoid mouse pointer "lagging"
            socket.setTcpNoDelay(true);
            socket.setTrafficClass(8);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        sendEvent(EventType.SOCKET_CONNECTED);
        sendEvent(EventType.STREAM_INPUT_READY);
    }

    public void close() {
        try {
            socket.close();
        }
        catch(IOException e){
            Log.info("Error closing socket");
        }
    }

    public boolean isReady() {
        return socket.isConnected();
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    public Object getEventTarget() {
        return this;
    }

    private void sendEvent(EventType eventType) {
        EventQueue.getInstance().addEvent(new Event(eventType, getEventTarget(), null));
    }

}
