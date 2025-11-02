package de.hauke_stieler.geonotes.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;

import com.google.gson.JsonObject;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.utils.BitmapUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

import de.hauke_stieler.geonotes.Injector;
import de.hauke_stieler.geonotes.R;
import de.hauke_stieler.geonotes.common.BitmapRenderer;
import de.hauke_stieler.geonotes.common.GeoPoint;
import de.hauke_stieler.geonotes.database.Database;
import de.hauke_stieler.geonotes.notes.Note;
import de.hauke_stieler.geonotes.notes.NoteIconProvider;

public class MapNeo {
    public interface TouchDownListener {
        void onTouchedDown();
    }

    public interface NoteMovedListener {
        void onNoteMoved(String value, Double longitude, Double latitude);
    }

    private final Context context;
    private final PowerManager.WakeLock wakeLock;
    private final Database database;
    private final SharedPreferences preferences;
    private final NoteIconProvider noteIconProvider;
    private SymbolManager symbolManager;

    private final MapView mapView;
//    private final IMapController mapController;
//    private MyLocationNewOverlay locationOverlay;
//    private GpsMyLocationProvider gpsLocationProvider;

    private final MarkerFragmentNeo markerFragment;
//    private Marker.OnMarkerClickListener markerClickListener;

    private boolean snapNoteToGps;

    // Variables used during moving a symbol. Do not use when no symbol is currently in move mode (aka when markerToMove==null)
    private Symbol symbolToMove;
    private Point dragStartMarkerPosition;

    // TODO needed in maplibre map?
//    private SnappableRotationOverlay rotationGestureOverlay;
    // TODO needed?
//    private ClickableMapCompass compassOverlay;

    public MapNeo(Context context,
                  MapView mapView,
                  Database database,
                  SharedPreferences preferences,
                  NoteIconProvider noteIconProvider) {
        this.context = context;
        this.mapView = mapView;
        this.database = database;
        this.preferences = preferences;
        this.noteIconProvider = noteIconProvider;

        markerFragment = Injector.get(MarkerFragmentNeo.class);
        addMarkerFragmentEventHandler(markerFragment);

        // Keep device on
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, "geonotes:wakelock");
        wakeLock.acquire();

        Drawable locationIconBackground
                = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_background, null);
        Drawable locationIconForeground
                = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_foreground, null);
        locationIconForeground.setColorFilter(BlendModeColorFilterCompat.createBlendModeColorFilterCompat(Color.parseColor("#66bb6a"), BlendModeCompat.SRC_IN));
        Drawable locationIcon = BitmapRenderer.renderToBitmap(context, locationIconBackground, locationIconForeground);

        Drawable arrowIconBackground
                = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_arrow_background, null);
        Drawable arrowIconForeground
                = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_arrow_foreground, null);
        arrowIconForeground.setColorFilter(BlendModeColorFilterCompat.createBlendModeColorFilterCompat(Color.parseColor("#66bb6a"), BlendModeCompat.SRC_IN));
        Drawable arrowIcon = BitmapRenderer.renderToBitmap(context, arrowIconBackground, arrowIconForeground);

        mapView.getMapAsync(mlMap -> {
            // TODO use this file from local resources. Try e.g. via mlMap.setStyle(Uri.parse("R.drawable.image")); or similar
            mlMap.setStyle("https://roblabs.com/xyz-raster-sources/styles/openstreetmap.json", style -> {
                this.symbolManager = new SymbolManager(mapView, mlMap, style);
                this.symbolManager.setIconAllowOverlap(true);

                mlMap.addOnMapLongClickListener(coordinate -> {
                    if (preferences.getBoolean(context.getString(R.string.pref_tap_duration), false)) {
                        createMarker(coordinate);
                        return true;
                    }
                    return false;
                });

                mlMap.addOnMapClickListener(coordinate -> {
                    if (!preferences.getBoolean(context.getString(R.string.pref_tap_duration), false)) {
                        createMarker(coordinate);
                        return true;
                    }
                    return false;
                });

                this.symbolManager.addClickListener(clickedSymbol -> {
                    selectMarker(clickedSymbol, false);
                    return true;
                });

                this.noteIconProvider
                        .getIconNameToDrawableMap()
                        .forEach((name, drawable) -> style.addImage(name, BitmapUtils.getBitmapFromDrawable(drawable)));

                reloadAllNotes();
            });
            mlMap.setCameraPosition(new CameraPosition.Builder().target(new LatLng(0.0, 0.0)).zoom(1.0).build());
        });

        // TODO map configuration necessary here?
//        Configuration.getInstance().setUserAgentValue(context.getPackageName());
//        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
//        map.setMultiTouchControls(true);
//        map.setTilesScaledToDpi(true);

        createOverlays((BitmapDrawable) locationIcon, (BitmapDrawable) arrowIcon);
    }

    public void reloadAllNotes() {
        Symbol currentlySelectedSymbol = getSelectedSymbol();
        if (currentlySelectedSymbol != null) {
            markerFragment.reset();
            deselectMarker(currentlySelectedSymbol);
        }

        List<Note> allNotes = this.database.getAllNotes();
        if (allNotes.isEmpty()) {
            this.markerFragment.reset();
        } else {
            for (Note n : allNotes) {
                Symbol symbol = createMarker(n);
                this.symbolManager.update(symbol);
            }
        }

        redraw();
    }

    private void createOverlays(BitmapDrawable locationIcon, BitmapDrawable arrowIcon) {
        // TODO Add scalebar (?)
        // TODO Add current location layer (?)
    }

    public void enableLocationsOverlay() {
        // TODO
//        locationOverlay.enableMyLocation();
    }

    private void saveMapRotationProperty(float angle) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(context.getString(R.string.pref_map_rotation), angle);
        editor.commit();
    }

    public void updateMapRotation(boolean rotatingMapEnabled, float angle) {
        // TODO needed in maplibre map?
//        rotationGestureOverlay.setEnabledAndRotation(rotatingMapEnabled, angle);
//        compassOverlay.setPointerMode(rotatingMapEnabled);
    }

    // TODO Add handler for all the actions (tap, drag, etc.)
    @SuppressLint("ClickableViewAccessibility")
    public void addMapListener(TouchDownListener touchDownListener, NoteMovedListener noteMovedCallback) {
        mapView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownListener.onTouchedDown();

                    // TODO Initialize movement of the symbol: Store current screen-location to keep symbol there
//                    if (symbolToMove != null) {
//                        // TODO Determine pixel<->coordinate mapping in maplibre:
//                        dragStartMarkerPosition = map.getProjection().toPixels(symbolToMove.getPosition(), null);
//                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    // TODO When in drag-mode: Keep symbol at original screen location by setting its position
//                    if (symbolToMove != null && dragStartMarkerPosition != null) {
//                        symbolToMove.setPosition((GeoPoint) map.getProjection().fromPixels(dragStartMarkerPosition.x, dragStartMarkerPosition.y));
//                    }
                    break;
                case MotionEvent.ACTION_UP:
                    // TODO
//                    if (symbolToMove != null) {
//                        selectMarker(symbolToMove, false);
//
//                        // If the ID is set, the symbol exists in the DB, therefore we store that new location
//                        String id = symbolToMove.getId();
//                        Double longitude = null;
//                        Double latitude = null;
//                        if (id != null) {
//                            database.updateNoteLocation(Long.parseLong(id), symbolToMove.getPosition());
//                            longitude = symbolToMove.getPosition().getLongitude();
//                            latitude = symbolToMove.getPosition().getLatitude();
//                        }
//
//                        dragStartMarkerPosition = null;
//                        symbolToMove = null;
//
//                        if (id != null) {
//                            noteMovedCallback.onNoteMoved(id, longitude, latitude);
//                        }
//                    }
                    break;
            }
            return false;
        });
    }

    private void addMarkerFragmentEventHandler(MarkerFragmentNeo fragment) {
        fragment.addEventHandler(new MarkerFragmentNeo.SymbolFragmentEventHandler() {
            @Override
            public void onDelete(Symbol symbol) {
                // We always have an ID and can therefore delete the note
                database.removeNote(GeoNotesSymbol.getNoteId(symbol));
                database.removePhotos(GeoNotesSymbol.getNoteId(symbol), context.getExternalFilesDir("GeoNotes"));
                symbolManager.delete(symbol);
            }

            @Override
            public void onSave(Symbol symbol) {
                // We always have an ID and can therefore update the note
                database.updateNoteDescription(GeoNotesSymbol.getNoteId(symbol), GeoNotesSymbol.getDescription(symbol));
            }

            @Override
            public void onClose(Symbol symbol) {
                deselectMarker(symbol);
            }

            @Override
            public void onMove(Symbol symbol) {
                symbolToMove = symbol;
                redraw();
            }

            @Override
            public void onCategoryChanged(Symbol symbol) {
                database.updateNoteCategory(GeoNotesSymbol.getNoteId(symbol), GeoNotesSymbol.getCategoryId(symbol));

                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong(context.getString(R.string.pref_last_category_id), GeoNotesSymbol.getCategoryId(symbol));
                editor.commit();

                // Update Icon with the new color
                setIcon(symbol, getSelectedSymbol() == symbol);

                redraw();
            }
        });
    }

    // This forces a re-draw of the map. Otherwise changes will only be visible when moving the map after e.g. the selected symbol changed.
    private void redraw() {
        // TODO necessary?
//        map.postInvalidate();
    }

    /**
     * Creates a new note in the database, creates a corresponding symbol (s. createMarker()) and also selects this new symbol.
     */
    // TODO
//    private void initAndSelectMarker(GeoPoint location) {
//        long categoryId = preferences.getLong(context.getString(R.string.pref_last_category_id), 1);
//
//        long id = database.addNote("", location.getLatitude(), location.getLongitude(), categoryId);
//
//        if (snapNoteToGps) {
//            location = snapToGpsLocation(location);
//        }
//
//        Symbol newMarker = createMarker("" + id, "", location, categoryId, markerClickListener);
//        selectMarker(newMarker, true);
//    }

    /**
     * Tries to snap the location to the last known GPS of the distance on the screen is below 50dp.
     * If no GPS location available or if the distance to the current GPS location is lower than 50dp, then the GPS location is returned, otherwise the input is returned.
     *
     * @return The new location, snapped if possible.
     */
    // TODO
//    private GeoPoint snapToGpsLocation(GeoPoint location) {
//        if (gpsLocationProvider.getLastKnownLocation() == null) {
//            return location;
//        }
//
//        GeoPoint gpsLocation = new GeoPoint(gpsLocationProvider.getLastKnownLocation());
//
//        Point markerLocationOnScreen = map.getProjection().toPixels(location, null);
//        Point gpsLocationOnScreen = map.getProjection().toPixels(gpsLocation, null);
//
//        int diffY = gpsLocationOnScreen.y - markerLocationOnScreen.y;
//        int diffX = gpsLocationOnScreen.x - markerLocationOnScreen.x;
//        double distanceOnScreen = Math.sqrt(diffY * diffY + diffX * diffX);
//
//        if (distanceOnScreen < 50) {
//            location = gpsLocation;
//        }
//
//        return location;
//    }
    public void selectNote(long noteId) {
        // TODO
//        String noteIdString = "" + noteId;
//        for (Overlay symbol : map.getOverlays()) {
//            if (symbol instanceof Symbol && ((Symbol) symbol).getId().equals(noteIdString)) {
//                this.selectMarker((Symbol) symbol, false);
//            }
//        }
    }

    /**
     * @param symbolToSelect          The symbol to select.
     * @param transferEditTextContent When set to true: If the user typed any text into the input
     *                                field without a selected note and *then* tapped on the map
     *                                to create or select one, this prior entered text schould be
     *                                used as the content of the note.
     *                                When set to false: The text of the tapped note will be read
     *                                and shown in the edit field.
     */
    private void selectMarker(Symbol symbolToSelect, boolean transferEditTextContent) {
        // TODO Deselect previously selected symbol
//        Symbol currentlySelectedMarker = markerFragment.getSelectedMarker();
//        if (currentlySelectedMarker != null) {
//            markerFragment.reset();
//            deselectMarker(currentlySelectedMarker);
//        }

        // TODO
//        setIcon(symbolToSelect, true);

//        for (int i = 0; i < this.symbolManager.getAnnotations().size(); i++) {
//            Symbol otherSymbol = this.symbolManager.getAnnotations().valueAt(i);
//            if (GeoNotesSymbol.hasSelectedStyle(otherSymbol)) {
//                otherSymbol.setIconImage(GeoNotesSymbol.getIconName(otherSymbol));
//                this.symbolManager.update(otherSymbol);
//            }
//        }

        Symbol currentlySelectedSymbol = getSelectedSymbol();
        if (currentlySelectedSymbol != null) {
            setIcon(currentlySelectedSymbol, false);
        }

        setIcon(symbolToSelect, true);

        this.markerFragment.selectSymbol(symbolToSelect, transferEditTextContent);
        zoomToSelectedMarker();

        addImagesToMarkerFragment();
        redraw();
    }

    private void deselectMarker(Symbol symbol) {
        if (symbol == null) {
            return;
        }

        // This icon will not be the selected symbol after "showInfoWindow", therefore we set the normal icon here.
        setIcon(symbol, false);
    }

    public Symbol getSelectedSymbol() {
        return markerFragment.getSelectedSymbol();
    }

    /**
     * Loads images of current symbol (which contains the note-ID) from database and show them.
     */
    public List<String> addImagesToMarkerFragment() {
        markerFragment.resetImageList();
        Symbol symbol = getSelectedSymbol();

        // It could happen that the user rotates the device (e.g. while taking a photo) and this
        // causes the whole activity to be reset. Therefore we might not have a symbol here.
        if (symbol == null) {
            return Collections.emptyList();
        }

        List<String> photoFileNames = database.getPhotos(GeoNotesSymbol.getNoteId(symbol).toString());
        for (String photoFileName : photoFileNames) {
            File storageDir = context.getExternalFilesDir("GeoNotes");
            File image = new File(storageDir, photoFileName);
            markerFragment.addPhoto(image);
        }

        setIcon(symbol, true);
        redraw();

        return photoFileNames;
    }

    private void setIcon(Symbol symbol, boolean isSelected) {
        boolean hasPhotos = database.hasPhotos(GeoNotesSymbol.getNoteId(symbol));
        symbol.setIconImage(GeoNotesSymbol.getIconName(symbol, hasPhotos, isSelected));
        this.symbolManager.update(symbol);
    }

    public void setZoomButtonVisibility(boolean visible) {
        // TODO
//        map.getZoomController().setVisibility(visible ? CustomZoomButtonsController.Visibility.ALWAYS : CustomZoomButtonsController.Visibility.NEVER);
    }

    public void setMapScaleFactor(float factor) {
        // TODO
//        map.setTilesScaleFactor(factor);
    }

    private void zoomToSelectedMarker() {
        // Before resuming (e.g. when switching back from the list of notes to the main activity),
        // the map doesn't zoom to markers. Therefore we here zoom to the currently selected symbol.
        // TODO
//        Marker selectedMarker = getSelectedMarker();
//        if (selectedMarker != null) {
//            zoomToLocation(selectedMarker.getPosition(), map.getZoomLevelDouble());
//        }
    }

    // TODO
//    private void zoomToLocation(IGeoPoint p, double zoom) {
//        mapController.setCenter(new GeoPoint(p));
//        mapController.setZoom(zoom);
//    }

    private void createMarker(LatLng location) {
        // No marker to move here -> deselect or create marker
        // (selecting marker on the map is handles via the separate markerClickListener)
        if (markerFragment.getSelectedSymbol() != null) {
            // Deselect selected marker:
            setIcon(markerFragment.getSelectedSymbol(), false);
        }

        // Create new marker at this location and select it
        long categoryId = preferences.getLong(context.getString(R.string.pref_last_category_id), 1);

        long id = database.addNote("", location.getLatitude(), location.getLongitude(), categoryId);
        Note note = database.getNote(id);

        // TODO
//        if (snapNoteToGps) {
//            location = snapToGpsLocation(location);
//        }

        Symbol newSymbol = createMarker(note);
        selectMarker(newSymbol, true);
    }

    private Symbol createMarker(Note note) {
        boolean hasPhoto = database.hasPhotos(note.getId());

        JsonObject data = GeoNotesSymbol.getData(note);

        return this.symbolManager.create(
                new SymbolOptions()
                        .withLatLng(new LatLng(note.getLat(), note.getLon()))
                        .withIconImage(GeoNotesSymbol.getIconName(note, hasPhoto, false))
                        .withIconAnchor("bottom")
                        .withData(data)
        );
    }

    public void onResume() {
        // TODO Necessary with maplibre?
//        map.onResume();
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        zoomToSelectedMarker();
    }

    public void onPause() {
        // TODO Necessary with maplibre?
//        map.onPause();
    }

    public void onDestroy() {
        markerFragment.reset();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public de.hauke_stieler.geonotes.common.GeoPoint getLocation() {
        // TODO
//        return map.getMapCenter();
        return new de.hauke_stieler.geonotes.common.GeoPoint(0.0, 0.0);
    }

    public void setLocation(float lat, float lon, float zoom) {
        // TODO
//        zoomToLocation(new GeoPoint(lat, lon), zoom);
    }

    public float getZoom() {
        // TODO
//        return (float) map.getZoomLevelDouble();
        return 0f;
    }

    /**
     * Turns the follow mode on or off. If it's turned on, the map will follow the current location.
     */
    public void setLocationFollowMode(boolean followingLocationEnabled) {
        // TODO
//        if (followingLocationEnabled) {
//            this.locationOverlay.enableFollowLocation();
//        } else {
//            this.locationOverlay.disableFollowLocation();
//        }
    }

    public boolean isFollowLocationEnabled() {
        // TODO
//        return this.locationOverlay.isFollowLocationEnabled();
        return false;
    }

    public void addRequestPhotoHandler(MarkerFragmentNeo.RequestPhotoEventHandler requestPhotoEventHandler) {
        this.markerFragment.addRequestPhotoHandler(requestPhotoEventHandler);
    }

    public void setSnapNoteToGps(boolean snapNoteToGps) {
        this.snapNoteToGps = snapNoteToGps;
    }
}
