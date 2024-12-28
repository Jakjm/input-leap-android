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

import android.app.Activity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TCPSocketFactory implements SocketFactoryInterface{
    private static final int SOCKET_CONNECTION_TIMEOUT_IN_MILLIS = 1000;
    private static final int SOCKET_IO_TIMEOUT = 20000;
    public TCPSocketFactory() {

    }

    public TCPSocket tcpSocketCreate(Activity activity, InetSocketAddress addressPort){
        Socket socket = create(activity, addressPort);
        if(socket == null)return null;
        else return new TCPSocket(socket);
    }

    public synchronized Socket create(Activity activity, InetSocketAddress address) {
        try {
            Socket socket = new Socket();
            socket.setSoTimeout(SOCKET_IO_TIMEOUT);
            socket.connect(address, SOCKET_CONNECTION_TIMEOUT_IN_MILLIS);
            return socket;
        }
        catch(IOException e){
            return null;
        }
    }


}

