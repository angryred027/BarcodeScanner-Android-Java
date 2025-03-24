package com.gilves.barcodescanner;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import java.lang.ref.WeakReference;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ServerSettingActivity extends AppCompatActivity {
    private static final String TAG = "ServerSettingActivity";
    private static String Classes = "net.sourceforge.jtds.jdbc.Driver";
    private SqlServerConnectionTask sqlServerConnectionTask;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_server_setting);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(Level.FINEST);

        Button connectButton = findViewById(R.id.btn_connect);
        textView = findViewById(R.id.tv_status);

        connectButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(ServerSettingActivity.this,
                    Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ServerSettingActivity.this,
                        new String[]{Manifest.permission.INTERNET}, 1);
            } else {
                sqlServerConnectionTask = new SqlServerConnectionTask(textView);
                sqlServerConnectionTask.execute();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sqlServerConnectionTask != null && sqlServerConnectionTask.getStatus() != AsyncTask.Status.FINISHED) {
            sqlServerConnectionTask.cancel(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sqlServerConnectionTask = new SqlServerConnectionTask(textView);
                sqlServerConnectionTask.execute();
            } else {
                Toast.makeText(this, "Permission denied. Cannot connect to server.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class SqlServerConnectionTask extends AsyncTask<Void, Void, String> {
        private WeakReference<TextView> textViewRef;

        public SqlServerConnectionTask(TextView textView) {
            textViewRef = new WeakReference<>(textView);
        }

        @Override
        protected String doInBackground(Void... params) {
            String result = "";
            Connection conn = null;

            String url = "jdbc:sqlserver://10.0.2.2:1433;databaseName=Android;encryption=false;trustServerCertificate=true;";
            String username = "android";
            String password = "android";

            TrustManager[] trustAllCerts = new TrustManager[]{new TrustAllCertificates()};
            SSLContext sslContext = null;
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (Exception e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new TrustAllHostnameVerifier());

            try {
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

                conn = DriverManager.getConnection(url, username, password);

                if (conn != null) {
                    result = "Success: Connected.";
                } else {
                    result = "Failed: Not connected.";
                }
            } catch (Exception e) {
                result = "Error: " + e.getMessage();
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            TextView textView = textViewRef.get();
            if (textView != null) {
                textView.setText(result);
            }
        }
    }

    public static class TrustAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    public static class TrustAllCertificates implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
