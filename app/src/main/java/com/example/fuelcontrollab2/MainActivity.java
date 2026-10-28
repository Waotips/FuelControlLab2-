package com.example.fuelcontrollab2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private MapView map;
    private TextView tvStats;
    private EditText etConsumption, etFuel, etPrice;
    private Button btnStart;

    private LocationManager locationManager;
    private Polyline track;
    private Marker me;
    private boolean tracking = false;
    private Location last;
    private double distanceM = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Настройка osmdroid: User-Agent и новый кэш (старый с ошибками 403 не используется)
        Configuration.getInstance().setUserAgentValue("FuelControlLab2/1.0 (artemgresnakov@gmail.com)");
        File base = new File(getCacheDir(), "osmdroid_new");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.mapView);
        tvStats = findViewById(R.id.tvStats);
        etConsumption = findViewById(R.id.etConsumption);
        etFuel = findViewById(R.id.etFuel);
        etPrice = findViewById(R.id.etPrice);
        btnStart = findViewById(R.id.btnStart);
        Button btnCenter = findViewById(R.id.btnCenter);
        Button btnClear = findViewById(R.id.btnClear);

        // Карта Carto (работает без блокировок)
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(42.8806, -8.5446)); // Сантьяго-де-Компостела

        track = new Polyline();
        track.getOutlinePaint().setColor(0xFF1565C0);
        track.getOutlinePaint().setStrokeWidth(10f);
        map.getOverlays().add(track);

        me = new Marker(map);
        me.setTitle("Я здесь");
        map.getOverlays().add(me);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        btnStart.setOnClickListener(v -> {
            if (!tracking) startTracking(); else stopTracking();
        });
        btnCenter.setOnClickListener(v -> {
            if (last != null) {
                map.getController().animateTo(new GeoPoint(last));
            } else {
                Toast.makeText(this, "Позиция ещё не определена", Toast.LENGTH_SHORT).show();
            }
        });
        btnClear.setOnClickListener(v -> {
            track.setPoints(new java.util.ArrayList<>());
            distanceM = 0;
            last = null;
            updateStats();
            map.invalidate();
        });

        requestPermissionIfNeeded();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionIfNeeded() {
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
    }

    private void startTracking() {
        if (!hasPermission()) {
            requestPermissionIfNeeded();
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 3, this);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, this);
            }
            tracking = true;
            btnStart.setText("Остановить трек");
            tvStats.setText("Ожидание GPS...");
        } catch (SecurityException e) {
            Toast.makeText(this, "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopTracking() {
        locationManager.removeUpdates(this);
        tracking = false;
        btnStart.setText("Начать трек");
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        GeoPoint p = new GeoPoint(loc);
        if (last != null) {
            float d = last.distanceTo(loc);
            if (d < 2) return; // отсекаем дрожание GPS
            distanceM += d;
        }
        last = loc;
        track.addPoint(p);
        me.setPosition(p);
        map.getController().animateTo(p);
        updateStats();
        map.invalidate();
    }

    private double num(EditText e, double def) {
        try {
            return Double.parseDouble(e.getText().toString().replace(',', '.'));
        } catch (Exception ex) {
            return def;
        }
    }

    private void updateStats() {
        double cons = num(etConsumption, 7.5);
        double tank = num(etFuel, 40);
        double price = num(etPrice, 60);
        double km = distanceM / 1000.0;
        double used = km * cons / 100.0;
        double left = Math.max(0, tank - used);
        double range = cons > 0 ? left / cons * 100.0 : 0;
        tvStats.setText(String.format(Locale.getDefault(),
                "Пройдено: %.2f км\nИзрасходовано: %.2f л (%.2f)\nОстаток: %.2f л\nЗапас хода: %.1f км",
                km, used, used * price, left, range));
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(@NonNull String provider) { }
    @Override public void onProviderDisabled(@NonNull String provider) { }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tracking) locationManager.removeUpdates(this);
    }
}
