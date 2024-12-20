package com.example.inputleap;

import java.io.FileOutputStream;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import javax.net.ssl.*;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    enum RESULT{
        UNDECIDED,
        YES,
        NO
    }

    ;
    AtomicInteger state = new AtomicInteger(DISCONNECTED);
    Socket socket;
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    BufferedReader reader;

    //XML elements from screen....
    Button startClientBtn;
    TextInputEditText ipText;
    EditText outputText;

    static {
        System.loadLibrary("inputleap");
    }

    void loadElements(){
        startClientBtn = (Button)findViewById(R.id.start_client_button);
        ipText = (TextInputEditText)findViewById(R.id.ip_text);
        outputText = (EditText)findViewById(R.id.editTextTextMultiLine);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadElements();


        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    updateConnectionStatus();
                } catch (Exception e) {
                    // TODO: handle exception
                } finally {
                    //also call the same runnable to call it at regular interval
                    handler.postDelayed(this, 150);
                }
            }
        };

        //runnable must be execute once
        handler.post(runnable);

    }

    boolean validateIp(String ip) {
        int firstDot = ip.indexOf('.');
        int secondDot = ip.indexOf('.', firstDot + 1);
        int thirdDot = ip.indexOf('.', secondDot + 1);
        if (firstDot == -1 || secondDot == -1 || thirdDot == -1) {
            return false;
        } else if (secondDot - firstDot <= 1 || thirdDot - secondDot <= 1) {
            return false;
        }
        try {
            int[] bytes = new int[4];
            bytes[0] = Integer.parseInt(ip.substring(0, firstDot));
            bytes[1] = Integer.parseInt(ip.substring(firstDot + 1, secondDot));
            bytes[2] = Integer.parseInt(ip.substring(secondDot + 1, thirdDot));
            bytes[3] = Integer.parseInt(ip.substring(thirdDot + 1));

            for (int i = 0; i < bytes.length; ++i) {
                if (bytes[i] < 0 || bytes[i] > 255) return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public class TrustMgr implements X509TrustManager {
        volatile RESULT result;
        public TrustMgr(){

        }
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            //not implemented....
        }

        private void saveCertificate(X509Certificate certificate) {
            try {
                // Load or create a new KeyStore
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null); // Create a new empty KeyStore

                // Add the certificate to the KeyStore
                String alias = "server-cert";
                keyStore.setCertificateEntry(alias, certificate);

                // Save the KeyStore to a file
                try (FileOutputStream fos = new FileOutputStream("truststore.jks")) {
                    keyStore.store(fos, "password".toCharArray());
                }

                System.out.println("Certificate saved to truststore.jks");
            } catch (Exception e) {
                System.out.println("Failed to save the certificate: " + e.getMessage());
            }
        }
        public String getSignature(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
            //byte[] sigBytes = cert.getSignature();
            byte[] encoded = cert.getEncoded();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sigBytes = digest.digest(encoded);

            if(sigBytes.length < 1)return "";
            StringBuilder builder = new StringBuilder();
            builder.append(Integer.toHexString(0xFF & sigBytes[0]));
            for(int i = 1; i < sigBytes.length;++i){
                builder.append(':');
                builder.append(Integer.toHexString(0xFF & sigBytes[i]));
            }
            return builder.toString().toUpperCase();
        }


        //TODO user should be able to permanently save this certificate....
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                try {
                    String signature = getSignature(cert);

               Runnable alertTask = new Runnable() {
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Confirm");
                        builder.setMessage("Do you trust server signature: " + signature);
                        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing but close the dialo
                               result = RESULT.YES;
                               dialog.dismiss();

                            }
                        });
                        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                                result = RESULT.NO;
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                };
                runOnUiThread(alertTask);
                while(result == RESULT.UNDECIDED){
                }
                if(result == RESULT.NO)throw new Exception();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            //If user does not accept it...
                //throw new java.security.cert.CertificateException("User did not trust the certificate.");
            //}
            // Save the certificate dynamically if user accepts
            saveCertificate(chain[0]);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
    public void connect(String ip){
        int result;
        Runnable task = new Runnable(){
            public void run(){
                if(state.get() == CONNECTING) {
                    try {
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, new TrustManager[]{new TrustMgr()}, null);
                        //SocketFactory sslFactory = SSLSocketFactory.getDefault();
                        SocketFactory sslFactory = sslContext.getSocketFactory();

                        socket = sslFactory.createSocket(ip, 24800);
                        SSLSocket sslSocket = (SSLSocket) socket;
                        sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
                            @Override
                            public void handshakeCompleted(HandshakeCompletedEvent event) {
                                try {
                                    socket.setSoTimeout(30);
                                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                state.set(CONNECTED);
                            }
                        });
                        sslSocket.startHandshake();
                    } catch (Exception e) {
                        state.set(DISCONNECTED);
                    }
                }
            }
        };
        executorService.execute(task);
    }

    public void disconnect(){

    }

    public void updateConnectionStatus() throws IOException {
        int status = state.get();
        FutureTask<String> task = new FutureTask<String>(new Callable<String>(){
            public String call(){
                String result = null;
                try {
                    result = "" + (char)reader.read();
                } catch (IOException e) {
                    result = null;
                }
                return result;
            }
        });

        if(status == CONNECTED){
            startClientBtn.setText("Stop InputLeap Client");
            try {
                executorService.execute(task);
                String serverMessage = task.get(30, TimeUnit.MILLISECONDS);
                if(serverMessage != null)
                    outputText.setText(outputText.getText().append(serverMessage));
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        else if(status == CONNECTING){
            startClientBtn.setText("Connecting, please wait...");
        }
        else{
            ipText.setEnabled(true);
            startClientBtn.setText("Start InputLeap Client");
            startClientBtn.setEnabled(true);
        }
    }

    @Override
    public void onClick(View v) {
        startClientBtn.setEnabled(false);
        ipText.setEnabled(false);

        Editable editable = ipText.getText();
        if(editable != null) {
            String ip = editable.toString();
            if(validateIp(ip)) {
                state.set(CONNECTING);
                connect(ip);
            }
            else{
                startClientBtn.setEnabled(true);
                ipText.setEnabled(true);
                //TODO complain that ip is incorrect with a dialog...
            }
        }
    }
}