package com.crowncouncil.orbit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.crowncouncil.orbit.BuildConfig
import com.crowncouncil.orbit.domain.BangladeshMap
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

/** Distance within which we suggest "you are in this district". Spot coordinates are district-level, so this is deliberately wide. */
private const val IN_DISTRICT_KM = 10.0

@SuppressLint("MissingPermission")
@Composable
fun LiveMapScreen(vm: OrbitViewModel, onOpenGuide: () -> Unit) {
    val ctx = LocalContext.current
    val trip by vm.currentTrip.collectAsState()
    val dests by vm.destinations.collectAsState()
    val visited by vm.visitedIds.collectAsState()
    val target = dests.firstOrNull { it.id == trip?.destinationId }

    fun granted() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var hasPermission by remember { mutableStateOf(granted()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPermission = granted() }

    var tracking by remember { mutableStateOf(false) }
    var trail by remember { mutableStateOf(listOf<LatLng>()) }
    var lastMock by remember { mutableStateOf(false) }
    val here = trail.lastOrNull()

    // Location updates run only while this screen is open and tracking is on.
    DisposableEffect(hasPermission, tracking) {
        if (!hasPermission || !tracking) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).setMinUpdateDistanceMeters(10f).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trail = (trail + LatLng(it.latitude, it.longitude)).takeLast(500)
                    @Suppress("DEPRECATION")
                    lastMock = if (Build.VERSION.SDK_INT >= 31) it.isMock else it.isFromMockProvider
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { client.removeLocationUpdates(callback) }
    }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(23.7, 90.4), 6f) }
    LaunchedEffect(target?.id) { target?.let { camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 9f)) } }
    LaunchedEffect(here) { if (tracking && here != null) camera.animate(CameraUpdateFactory.newLatLngZoom(here, 13f)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Live map")
        Text(
            if (target != null) "Heading to ${target.name}, ${target.district}." else "No trip selected. Plan a trip to set your destination.",
            fontSize = 15.sp
        )
        if (!BuildConfig.MAPS_KEY_SET) InfoCard("Map key missing", "Map tiles need a Google Maps API key. See the README, then rebuild.")
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp)),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = hasPermission)
        ) {
            target?.let { Marker(state = rememberMarkerState(position = LatLng(it.latitude, it.longitude)), title = it.name, snippet = "District centre (approximate)") }
            if (trail.size > 1) Polyline(points = trail, color = Teal, width = 10f)
        }

        if (!hasPermission) {
            InfoCard("Location needed", "Allow location to see yourself on the map. It is used only while this screen is open and tracking is on, and your path stays on this phone.")
            Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Allow location")) }
        } else {
            Button(onClick = { tracking = !tracking }, modifier = Modifier.fillMaxWidth()) { Text(if (tracking) "Stop tracking" else "Start tracking") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { here?.let { shareLocation(ctx, it.latitude, it.longitude) } }, enabled = here != null, modifier = Modifier.weight(1f)) { Text(tr("Share my location")) }
            Button(onClick = { dialEmergency(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Rose), modifier = Modifier.weight(1f)) { Text(tr("Call 999")) }
        }
        Text("999 opens your dialer. Share my location sends a map link through the app you choose, to the people you choose.", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)

        if (target != null && here != null) {
            val km = BangladeshMap.distanceKm(here.latitude, here.longitude, target.latitude, target.longitude)
            InfoCard("%.1f km to ${target.district} centre".format(km), "Spot positions are district-level estimates, so treat this as a rough guide.")
            if (km <= IN_DISTRICT_KM && target.id !in visited) {
                Button(onClick = { vm.markVisited(target.id, null, "", "gps", here.latitude, here.longitude, lastMock) }, modifier = Modifier.fillMaxWidth()) { Text("You are in ${target.district}. Mark ${target.name} visited") }
            }
            OutlinedButton(
                onClick = { vm.ask("How far is ${target.name} and how long will it take?", here.latitude to here.longitude, target.id); onOpenGuide() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Ask the guide about this trip")) }
        }
    }
}
