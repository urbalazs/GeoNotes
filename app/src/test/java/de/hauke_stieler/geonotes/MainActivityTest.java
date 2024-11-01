package de.hauke_stieler.geonotes;

import static android.app.Activity.RESULT_OK;
import static de.hauke_stieler.geonotes.MainActivity.REQUEST_CATEGORIES_REQUEST_CODE;

import android.content.SharedPreferences;
import android.os.Build;
import android.view.MenuItem;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mockito;
import org.osmdroid.events.DelayedMapListener;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import de.hauke_stieler.geonotes.categories.Category;
import de.hauke_stieler.geonotes.database.Database;
import de.hauke_stieler.geonotes.export.Exporter;
import de.hauke_stieler.geonotes.map.Map;
import de.hauke_stieler.geonotes.map.MarkerFragment;
import de.hauke_stieler.geonotes.notes.Note;
import de.hauke_stieler.geonotes.notes.NoteIconProvider;

/**
 * These tests currently don't work. Neither with JUnit4 runner nor with JUnit5.
 */
@Config(maxSdk = Build.VERSION_CODES.P, minSdk = Build.VERSION_CODES.P)
// Value of Build.VERSION_CODES.P is 28
public class MainActivityTest {

    public ActivityScenarioRule<MainActivity> activityRule;
    public GeoNotesTestRule testRule;

    @Rule
    public TestRule chain = RuleChain.outerRule(testRule = new GeoNotesTestRule()).around(activityRule = new ActivityScenarioRule(MainActivity.class));

    private Database databaseMock;
    private Exporter exporterMock;
    private SharedPreferences sharedPreferencesMock;
    private Map mapMock;
    private NoteIconProvider noteIconProviderMock;

    @BeforeEach
    public void setup() {
        databaseMock = testRule.get(Database.class);
        exporterMock = testRule.get(Exporter.class);
        sharedPreferencesMock = testRule.get(SharedPreferences.class);
        mapMock = testRule.get(Map.class);
        noteIconProviderMock = testRule.get(NoteIconProvider.class);
    }

    //    @Test
    public void testExportGeoJsonClicked_callsExporter() {
        // Arrange
        List<Note> notes = new ArrayList<>();
        notes.add(new Note(1, "foo", 1.23f, 4.56f, "2021-03-01 12:34:56", new Category(2, "", "", 1)));
        notes.add(new Note(2, "bar", 2.34f, 5.67f, "2021-03-02 11:11:11", new Category(2, "", "", 2)));
        Mockito.when(databaseMock.getAllNotes()).thenReturn(notes);

        // Act
//        activityRule.getScenario().onActivity(activity -> activity.exportPopupMenu.getMenu().performIdentifierAction(0, 0));

        // Assert
        Mockito.verify(exporterMock).shareAsGeoJson();
    }

    //    @Test
    public void testExportGpxClicked_callsExporter() {
        // Arrange
        List<Note> notes = new ArrayList<>();
        notes.add(new Note(1, "foo", 1.23f, 4.56f, "2021-03-01 12:34:56", new Category(1, "", "", 1)));
        notes.add(new Note(2, "bar", 2.34f, 5.67f, "2021-03-02 11:11:11", new Category(1, "", "", 2)));
        Mockito.when(databaseMock.getAllNotes()).thenReturn(notes);

        // Act
//        activityRule.getScenario().onActivity(activity -> activity.exportPopupMenu.getMenu().performIdentifierAction(1, 0));

        // Assert
        Mockito.verify(exporterMock).shareAsGpx();
    }

//    @Test
    public void testloadPreferences_setsLocation() {
        // Arrange
        Mockito.when(sharedPreferencesMock.getFloat("PREF_LAST_LOCATION_LAT", 0f)).thenReturn(1.23f);
        Mockito.when(sharedPreferencesMock.getFloat("PREF_LAST_LOCATION_LON", 0f)).thenReturn(4.56f);
        Mockito.when(sharedPreferencesMock.getFloat("PREF_LAST_LOCATION_ZOOM", 2)).thenReturn(7f);

        // Act
        activityRule.getScenario().onActivity(activity -> activity.loadPreferences());

        // Assert
        Mockito.verify(mapMock).setLocation(1.23f, 4.56f, 7f);
    }

//    @Test
    public void testloadPreferences_setsMapListener() {
        // Act & Assert
        Mockito.verify(mapMock).addMapListener(Mockito.any(DelayedMapListener.class), Mockito.any(Map.TouchDownListener.class), Mockito.any(Map.NoteMovedListener.class));
    }

//    @Test
    public void testloadPreferences_setsPhotoListener() {
        // Act & Assert
        Mockito.verify(mapMock).addRequestPhotoHandler(Mockito.any(MarkerFragment.RequestPhotoEventHandler.class));
    }

//    @Test
    public void testCategoryChange_updatesNoteIcons() {
        // Act
        activityRule.getScenario().onActivity(activity -> activity.onActivityResult(REQUEST_CATEGORIES_REQUEST_CODE, RESULT_OK, null));

        // Assert
        Mockito.verify(noteIconProviderMock).updateIcons();
    }

    private MenuItem getMenuItem(int id) {
        MenuItem mock = Mockito.mock(MenuItem.class);
        Mockito.when(mock.getItemId()).thenReturn(id);
        return mock;
    }
}
