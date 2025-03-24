package com.gilves.barcodescanner;

import android.animation.ObjectAnimator;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private OkHttpClient client;
    private HttpURLConnection connection;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageView imageView;
    private TextView scanResultTextView;
    private ToggleButton flashToggleButton;
    private EditText searchEditText;
    private Button searchButton;

    private DecoratedBarcodeView barcodePreview;
    private static final int BARCODE_REQUEST = 100;
    private String server = "";
    private String port = "";

    private String item_no = "";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private GestureDetector gestureDetector;

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

        client = new OkHttpClient();

        SharedPreferences sharedPreferences = getSharedPreferences("BarcodeScannerPrefs", MODE_PRIVATE);
        server = sharedPreferences.getString("SERVER", "10.0.2.2");
        server = server.isEmpty() ? "10.0.2.2" : server;
        port = sharedPreferences.getString("PORT", "3000");
        port = port.isEmpty() ? "3000" : port;

        disableSSLCertificateValidation();
        disableHostnameVerification();

//        Button buttonServerSettings = findViewById(R.id.btnServerSettings);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        imageView = findViewById(R.id.scanResultImage);
        scanResultTextView = (TextView) findViewById(R.id.scanResultText);
        flashToggleButton = findViewById(R.id.flashToggleButton);
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        gestureDetector = new GestureDetector(this, new GestureListener());
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {

                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });


//        buttonServerSettings.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
//                startActivityForResult(intent, BARCODE_REQUEST);
//            }
//        });

        barcodePreview = findViewById(R.id.barcodeScannerPreview);
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.values());

        barcodePreview.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodePreview.decodeContinuous(callback);
        barcodePreview.resume();
        flashToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                barcodePreview.setTorchOn();
            } else {
                barcodePreview.setTorchOff();
            }
        });
        scanResultTextView.setText("Try Scanning");
        search();
    }
    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() != null) {
                searchEditText.setText(result.getText());
                Toast.makeText(MainActivity.this, "Scanned: " + result.getText(), Toast.LENGTH_SHORT).show();
                barcodePreview.pause();
                search();
                new Handler().postDelayed(() -> barcodePreview.resume(), 2000);
            }
        }
        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        barcodePreview.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        barcodePreview.pause();

        SharedPreferences sharedPreferences = getSharedPreferences("BarcodeScannerPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("SERVER", server);
        editor.putString("PORT", port);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void disableSSLCertificateValidation() {
        try {
            TrustManager[] trustAllCertificates = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCertificates, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disableHostnameVerification() {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BARCODE_REQUEST && resultCode == RESULT_OK && data != null) {
            String barcode = data.getStringExtra("BARCODE_RESULT");
            scanResultTextView.setText("Scanned Code: " + barcode);
        }
    }
    private void fetchImageData(String apiUrl) {
        Request request = new Request.Builder().url(apiUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> scanResultTextView.setText("Failed to connect."));
                Log.e("Network Error", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        item_no = jsonResponse.optString("item_no", "welcome");
                        String imageUrl = "http://" + server + ":" + port + "/images/" + item_no + ".png";

                        mainHandler.post(() -> {
                            scanResultTextView.setText(jsonResponse.optString("description", "Connected."));
                            Glide.with(MainActivity.this).load(imageUrl).into(imageView);
                        });
                    } catch (Exception e) {
                        Log.e("JSON Error", e.toString());
                    }
                } else {
                    Log.e("HTTP Error", "Response Code: " + response.code());
                }
            }
        });
    }
    private JSONObject fetchJsonFromApi(String apiUrl) {
        JSONObject jsonResponse = null;
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7777);
            connection.setReadTimeout(7777);
            connection.setDoInput(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();
                jsonResponse = new JSONObject(response.toString());
                return jsonResponse;
            } else {
                Log.e("HTTP_ERROR", "Response Code: " + responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            Log.e("HTTP_EXCEPTION", "Error: " + e.getMessage());
        }
        return null;
    }
    private void search(){
        String searchString = searchEditText.getText().toString();
        if (!searchString.isEmpty()) {
            String apiUrl = "http://" + server + ":" + port + "/api/data" + "?search=" + searchString;
            fetchImageData(apiUrl);
        }
        else{
            String apiUrl = "http://" + server + ":" + port + "/status";
            fetchImageData(apiUrl);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 500;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;

            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD &&
                    Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    drawerLayout.openDrawer(GravityCompat.START);
                    return true;
                }
            }
            return false;
        }
    }
}
