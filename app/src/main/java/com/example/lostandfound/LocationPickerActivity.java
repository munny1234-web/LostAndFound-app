package com.example.lostandfound;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final String EXTRA_LAT = "PICKED_LAT";
    public static final String EXTRA_LNG = "PICKED_LNG";
    public static final String EXTRA_ADDRESS = "PICKED_ADDRESS";

    private static final int LOCATION_PERMISSION_REQUEST = 2001;
    // Default view: Dhaka, Bangladesh — recentres if the user shares/uses their location
    private static final LatLng DEFAULT_LATLNG = new LatLng(23.8103, 90.4125);

    GoogleMap map;
    Marker currentMarker;
    LatLng pickedLatLng;
    TextView tvPickedAddress, btnBack;
    Button btnConfirmLocation, btnUseCurrentLocation;
    FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        tvPickedAddress = findViewById(R.id.tvPickedAddress);
        btnBack = findViewById(R.id.btnBack);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        btnUseCurrentLocation = findViewById(R.id.btnUseCurrentLocation);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnBack.setOnClickListener(v -> finish());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        btnUseCurrentLocation.setOnClickListener(v -> useCurrentLocation());
        btnConfirmLocation.setOnClickListener(v -> confirmLocation());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LATLNG, 12f));
        map.setOnMapClickListener(this::dropPin);
    }

    void dropPin(LatLng latLng) {
        pickedLatLng = latLng;
        if (currentMarker != null) currentMarker.remove();
        currentMarker = map.addMarker(new MarkerOptions().position(latLng));
        btnConfirmLocation.setEnabled(true);
        reverseGeocode(latLng);
    }

    void useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
                dropPin(latLng);
            } else {
                Toast.makeText(this, "Couldn't get current location. Try tapping the map instead.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            useCurrentLocation();
        }
    }

    // Turns coordinates into a readable address string (e.g. "Dhanmondi, Dhaka")
    void reverseGeocode(LatLng latLng) {
        tvPickedAddress.setText("📍 " + String.format(Locale.US, "%.5f, %.5f", latLng.latitude, latLng.longitude) + " (looking up address...)");
        new Thread(() -> {
            String addressText = null;
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (results != null && !results.isEmpty()) {
                    Address a = results.get(0);
                    StringBuilder sb = new StringBuilder();
                    if (a.getSubLocality() != null) sb.append(a.getSubLocality()).append(", ");
                    else if (a.getThoroughfare() != null) sb.append(a.getThoroughfare()).append(", ");
                    if (a.getLocality() != null) sb.append(a.getLocality());
                    else if (a.getAdminArea() != null) sb.append(a.getAdminArea());
                    addressText = sb.toString().trim();
                    if (addressText.endsWith(",")) addressText = addressText.substring(0, addressText.length() - 1);
                }
            } catch (Exception ignored) {
                // Geocoder can fail (no network / no geocoder service on device) — fall back to raw coordinates
            }
            String finalAddress = (addressText == null || addressText.isEmpty())
                    ? String.format(Locale.US, "%.5f, %.5f", latLng.latitude, latLng.longitude)
                    : addressText;
            runOnUiThread(() -> tvPickedAddress.setText("📍 " + finalAddress));
        }).start();
    }

    void confirmLocation() {
        if (pickedLatLng == null) return;
        Intent result = new Intent();
        result.putExtra(EXTRA_LAT, pickedLatLng.latitude);
        result.putExtra(EXTRA_LNG, pickedLatLng.longitude);
        result.putExtra(EXTRA_ADDRESS, tvPickedAddress.getText().toString().replace("📍 ", ""));
        setResult(RESULT_OK, result);
        finish();
    }
}
