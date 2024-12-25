package com.example.inputleap;

import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import javax.net.ssl.*;
import com.google.android.material.textfield.TextInputEditText;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String PROP_clientName = "clientNAME";
    private final static String PROP_serverPort = "serverPORT";
    private final static String PROP_serverURL = "serverURL";
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    enum RESULT{
        UNDECIDED,
        YES,
        NO
    }
    AtomicInteger state = new AtomicInteger(DISCONNECTED);
    private View view;

    Socket socket;
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    BufferedReader reader;

    //XML elements from screen....
    Button startClientBtn;
    TextInputEditText ipText;
    TextInputEditText portText;
    TextInputEditText clientNameText;
    EditText outputText;
    CheckBox enable_ssl_checkbox;
    static final String PROP_ENABLE_SSL = "enableSSL";

    static {
        System.loadLibrary("inputleap");
    }

    void loadElements(){ //Load elements of our activity....
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        startClientBtn = (Button)findViewById(R.id.start_client_button);
        ipText = (TextInputEditText)findViewById(R.id.ip_EditText);
        clientNameText = (TextInputEditText)findViewById(R.id.client_name_EditText);
        portText = (TextInputEditText)findViewById(R.id.port_EditText);
        outputText = (EditText)findViewById(R.id.editTextTextMultiLine);
        //enable_ssl_checkbox = (CheckBox)findViewById(R.id.enable_ssl_checkbox);
        enable_ssl_checkbox = findViewById(R.id.enable_ssl_checkbox);
        enable_ssl_checkbox.setChecked(preferences.getBoolean(PROP_ENABLE_SSL, true));
    }

    void initElements(){ //Initialize elements of our activity with the correct text...
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        ipText.setText(preferences.getString(PROP_serverURL,""));
        portText.setText(preferences.getString(PROP_serverPort, "24800"));
        clientNameText.setText(preferences.getString(PROP_clientName, android.os.Build.MODEL));
        enable_ssl_checkbox.setChecked(true);
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
        initElements();

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
                    handler.postDelayed(this, 200);
                }
            }
        };

        //runnable must be execute once
        handler.post(runnable);

    }

    boolean validatePort(int port){
        return 1 <= port && port <= 65536;
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


    public class InputLeapTrustManager implements X509TrustManager {
        volatile RESULT result;
        public InputLeapTrustManager(){

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
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException{
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                try {
                    final String signature = getSignature(cert);
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
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }

                while(result == RESULT.UNDECIDED){
                }
                if(result == RESULT.NO)throw new RuntimeException();
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
    public void connect(String ip, int port){
        int result;
        Runnable task = new Runnable(){
            public void run(){
                if(state.get() == CONNECTING) {
                    try {
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, new TrustManager[]{new InputLeapTrustManager()}, null);
                        SocketFactory sslFactory = sslContext.getSocketFactory();

                        socket = sslFactory.createSocket(ip, port);
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
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        startClientBtn.setEnabled(true);
                                    }
                                });
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
        Runnable disconnectTask = new Runnable() {
            public void run() {
                try {
                    Socket sslSocket = socket;
                    socket = null;
                    sslSocket.close();
                    reader = null;
                } catch (Exception e) {
                    Exception x = e;
                }
            }
        };
        executorService.execute(disconnectTask);

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
            startClientBtn.setEnabled(true);
            ipText.setEnabled(true);
            portText.setEnabled(true);
            clientNameText.setEnabled(true);
            startClientBtn.setText("Start InputLeap Client");
        }
    }


    public void updatePreferences(String name, String url, int port){
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        preferencesEditor.putString(PROP_clientName, name);
        preferencesEditor.putString(PROP_serverURL, url);
        preferencesEditor.putString(PROP_serverPort, Integer.toString(port));
        preferencesEditor.putBoolean(PROP_ENABLE_SSL, enable_ssl_checkbox.isChecked());
        preferencesEditor.apply();
    }

    @Override
    public void onClick(View v) {
        int status = state.get();
        if(status == CONNECTED){
            disconnect();
            state.set(DISCONNECTED);
        }
        else {
            startClientBtn.setEnabled(false);
            ipText.setEnabled(false);
            portText.setEnabled(false);
            clientNameText.setEnabled(false);

            try {
                String ip = ipText.getText().toString();
                int port = Integer.parseInt(portText.getText().toString());
                String clientName = clientNameText.getText().toString();

                if (validateIp(ip) && validatePort(port)) {
                    updatePreferences(clientName, ip, port);
                    state.set(CONNECTING);
                    connect(ip, port);
                } else {
                    startClientBtn.setEnabled(true);
                    ipText.setEnabled(true);
                    //TODO complain that ip or something is incorrect with a dialog...
                }
            }
            catch(NumberFormatException | NullPointerException e){
                //TODO complain that something is incorrect....
            }
        }
    }

    public void onMoveMouseClick(View view) {
        this.view = view;
        moveMouseInCircle();
    }

    private void moveMouseInCircle() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            Path path = new Path();
            int centerX = 500; // Center X coordinate
            int centerY = 500; // Center Y coordinate
            int radius = 100; // Radius of the circle

            for (int i = 0; i <= 360; i += 10) {
                double angle = Math.toRadians(i);
                float x = (float) (centerX + radius * Math.cos(angle));
                float y = (float) (centerY + radius * Math.sin(angle));
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1000));
            GestureDescription gesture = gestureBuilder.build();

            dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Handle completion
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    // Handle cancellation
                }
            }, null);
        }
    }

    @SuppressLint("ServiceCast")
    private void dispatchGesture(GestureDescription gesture, AccessibilityService.GestureResultCallback gestureResultCallback, Object o) {
        ((AccessibilityService) getSystemService(ACCESSIBILITY_SERVICE)).dispatchGesture(gesture, gestureResultCallback, null);
    }

}