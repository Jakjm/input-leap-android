package org.synergy.net;

import android.app.Activity;

import com.example.inputleap.MainActivity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class TLSSocketFactory extends TCPSocketFactory{
    HandshakeCompletedListener listener;
    public TLSSocketFactory( HandshakeCompletedListener listener){
        this.listener = listener;

    }

    public Socket create( Activity activity, InetSocketAddress addressPort) {
        Socket unsecured = super.create(activity, addressPort);
        if(unsecured == null)return null;
        else {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                TrustManager manager = new InputLeapTrustManager(activity);
                sslContext.init(null, new TrustManager[]{manager}, null);
                SSLSocketFactory sslFactory = sslContext.getSocketFactory();
                SSLSocket socket = (SSLSocket)sslFactory.createSocket(unsecured, addressPort.getAddress().toString(), addressPort.getPort(), true);

                //Perform TLS handshake...
                socket.addHandshakeCompletedListener(listener);
                socket.startHandshake();
                return socket;
            } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
                return null;
            }
        }
    }
}