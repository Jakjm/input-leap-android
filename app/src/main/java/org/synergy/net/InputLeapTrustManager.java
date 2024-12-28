package org.synergy.net;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.example.inputleap.MainActivity;

import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

public class InputLeapTrustManager implements X509TrustManager {
    enum RESULT{
        UNDECIDED,
        YES,
        NO
    }
    private volatile RESULT result;
    private Activity activity;
    public InputLeapTrustManager(Activity activity){
        this.activity = activity;
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
        result = RESULT.UNDECIDED;
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = chain[i];
            try {
                final String signature = getSignature(cert);
                final Activity activity = this.activity;
                Runnable alertTask = new Runnable() {
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setTitle("Confirm");
                        builder.setMessage("Do you trust server signature: " + signature);
                        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                result = RESULT.YES;
                                dialog.dismiss();

                            }
                        });
                        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result = RESULT.NO;
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                };
                this.activity.runOnUiThread(alertTask);
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
