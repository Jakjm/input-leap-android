package org.synergy.net;

import com.example.inputleap.MainActivity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

public class TLSSocketFactory extends TCPSocketFactory{
    MainActivity activity;
    HandshakeCompletedListener listener;
    public TLSSocketFactory(MainActivity activity, HandshakeCompletedListener listener){
        this.activity = activity;
        this.listener = listener;

    }
    public Socket create(InetSocketAddress addressPort) {
        try {

            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager manager = new InputLeapTrustManager(activity);
            sslContext.init(null, new TrustManager[]{manager}, null);
            SocketFactory sslFactory = sslContext.getSocketFactory();

            SSLSocket socket = (SSLSocket)sslFactory.createSocket(addressPort.getAddress(), addressPort.getPort());
            socket.addHandshakeCompletedListener(listener);
            socket.startHandshake();
            return socket;
        }
        catch(IOException | NoSuchAlgorithmException | KeyManagementException e){
            return null;
        }
    }
}
