/**
 * This class makes use of KML file structured in a format as mentioned in the documentation
 * for displaying and navigating Purdue campus locations, buildings, and rooms.
 *
 * @author      Jefferey Ostapchuk
 * @date        04/21/2015
 * @version     1.2
 *
 * Any developer that makes changes to this class, it is required per development agreement that a
 * chronological order of developer contributions is retained.
 *
 * Next Developer           ?.?     04/??/2015
 * Jefferey Ostapchuk       1.2     04/21/2015
 */

package edu.pnc.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Pair;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.PolyStyle;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleMap;
import com.ekito.simpleKML.model.StyleSelector;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsFragment extends Fragment {
    private final String name = "maps-fragment";

    private Container cUniversity = new Container("University", "University");
    private Map<String, StyleSelector> cStyleSelectors = new HashMap<>();


    // Ui Controls
    private GoogleMap cGoogleMap;
    private Spinner spinnerSelectFocus;
    private Spinner spinnerSelectFloor;
    private SharedPreferences prefMapLegend;

    private AsyncTask<Void, String, Void> atLoaderTask = null;

    private boolean interiorEnabled = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // put in main activity?
        MapsInitializer.initialize(getActivity());
this.
        // 
        prefMapLegend = getActivity().getSharedPreferences(name + "-legend", Context.MODE_PRIVATE);

        // load kml objects to containers
        atLoaderTask = (new AsyncTask<Void, String, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Log.w(name, "Starting KML update!");
                    ConnectivityManager connMgr = (ConnectivityManager)
                            getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
                    String filename = getResources().getString(R.string.maps_update_filename);
                    File file = new File(getActivity().getFilesDir(), filename);

                    if (networkInfo != null && networkInfo.isConnected()) {
                        publishProgress(getResources().getString(R.string.maps_toast_update_check));
                        URL url = new URL(getResources().getString(R.string.maps_update_url));
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                        if (!file.exists() || file.lastModified() < connection.getLastModified()) {
                            FileOutputStream fileOutputStream = null;
                            InputStream inputStream = null;
                            try {
                                publishProgress(getResources().getString(R.string.maps_toast_update_download));
                                inputStream = connection.getInputStream();
                                fileOutputStream = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);

                                int size = 65536;
                                int count = 0;
                                byte[] buffer = new byte[size];
                                while ((count = inputStream.read(buffer)) >= 0)
                                    fileOutputStream.write(buffer, 0, count);


                            } catch (IOException ex) {
                                publishProgress(getResources().getString(R.string.maps_toast_update_failed));
                                ex.printStackTrace();
                            } finally {
                                if (fileOutputStream != null)
                                    fileOutputStream.close();

                                if(inputStream != null)
                                    inputStream.close();
                            }
                        }
                    } else {
                        publishProgress(getResources().getString(R.string.maps_toast_network_enable));
                        if (!file.exists())
                            throw new Exception("File does not exist!");

                    }
                    Log.w(name, "Finished KML update!");

                    Log.w(name, "Parsing KML started!");
                    publishProgress(getResources().getString(R.string.maps_toast_load_kml));
                    Serializer serializer = new Serializer();
                    Kml kmlDocument = serializer.read(getActivity().openFileInput(filename));

                    publishProgress(getResources().getString(R.string.maps_toast_read_kml));
                    Feature root = kmlDocument.getFeature();

                    // retrieve styles in document root
                    findKmlStyles(root);

                    // need starting folder
                    if (root instanceof Document)
                        for (Feature node : ((Document) root).getFeatureList())
                            if (node instanceof Folder)
                                root = node; //findKmlFolder((Document) root, "Purdue");

                    // used to update map legend list
                    ArrayList<String> categories = new ArrayList<String>();

                    // begin enumerating hierarchy
                    for (Feature nodeSite : ((Folder) root).getFeatureList()) {
                        if (nodeSite instanceof Folder) {

                            // add location to university container
                            Container cLocation = createContainer(nodeSite, cUniversity);

                            if (((Folder) nodeSite).getFeatureList() == null)
                                break;


                            for (Feature nodeLocation : ((Folder) nodeSite).getFeatureList()) {

                                if (nodeLocation instanceof com.ekito.simpleKML.model.GroundOverlay)
                                    cLocation.setAttribute("LatLngBounds", createBoundsFromOverlay((com.ekito.simpleKML.model.GroundOverlay) nodeLocation));


                                if (nodeLocation instanceof Folder) {

                                    // add building to current location container
                                    Container cBuilding = createContainer(nodeLocation, cLocation);

                                    for (Feature nodeBuilding : ((Folder) nodeLocation).getFeatureList()) {


                                        // if building is polygon
                                        if (nodeBuilding instanceof Placemark) {
                                            cBuilding.setAttribute("StyleUrl", nodeBuilding.getStyleUrl());

                                            for (Geometry nodeGeometry : ((Placemark) nodeBuilding).getGeometryList()) {
                                                PolygonOptions polygonOptions = new PolygonOptions();
                                                for (Coordinate coordinate : ((com.ekito.simpleKML.model.Polygon) nodeGeometry).getOuterBoundaryIs().getLinearRing().getCoordinates().getList())
                                                    polygonOptions.add(new LatLng(coordinate.getLatitude(), coordinate.getLongitude()));

                                                // set polygon style options
                                                //applyPolygonStyle(nodeBuilding, "normal", polygonOptions);
                                                polygonOptions.zIndex(-1F);

                                                cBuilding.setAttribute("PolygonOptions", polygonOptions);
                                                cBuilding.setAttribute("LatLngBounds", getPolygonBounds(polygonOptions.getPoints()));
                                            }
                                        }

                                        // if building is floor
                                        if (nodeBuilding instanceof Folder) {

                                            // add floor to current building container
                                            Container cFloor = createContainer(nodeBuilding, cBuilding);
                                            //floor.setAttribute("categories", new HashMap<String, Container>());

                                            for (Feature nodeFloor : ((Folder) nodeBuilding).getFeatureList()) {

                                                // if category container
                                                if (nodeFloor instanceof Folder) {

                                                    // add category
                                                    Container cCategory = createContainer(nodeFloor, cFloor);
                                                    categories.add(nodeFloor.getName());


                                                    for (Feature nodeCategory : ((Folder) nodeFloor).getFeatureList()) {
                                                        if (nodeCategory instanceof Placemark) {


                                                            // add room to current floor container
                                                            Container cRoom = createContainer(nodeCategory, cCategory);
                                                            cRoom.setAttribute("StyleUrl", nodeCategory.getStyleUrl());


                                                            for (Geometry nodeGeometry : ((Placemark) nodeCategory).getGeometryList()) {

                                                                if (nodeGeometry instanceof com.ekito.simpleKML.model.Point) {
                                                                    Style style = getKmlStyle(nodeCategory.getStyleUrl(), "normal");
                                                                    Coordinate coordinate = ((Point) nodeGeometry).getCoordinates();

                                                                    Bitmap bitmap = BitmapFactory.decodeStream(getFile(style.getIconStyle().getIcon().getHref()));
                                                                    MarkerOptions markerOptions = new MarkerOptions()
                                                                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                                                                            .title(cRoom.getLabel())
                                                                            .snippet(cRoom.getStringAttribute("snippet", cRoom.getLabel()))
                                                                            .position(new LatLng(coordinate.getLatitude(), coordinate.getLongitude()));

                                                                    cRoom.setAttribute("MarkerOptions", markerOptions);
                                                                }
                                                                if (nodeGeometry instanceof com.ekito.simpleKML.model.Polygon) {
                                                                    PolygonOptions polygonOptions = new PolygonOptions();

                                                                    // set polygon latlng vertice points
                                                                    for (Coordinate coordinate : ((com.ekito.simpleKML.model.Polygon) nodeGeometry).getOuterBoundaryIs().getLinearRing().getCoordinates().getList())
                                                                        polygonOptions.add(new LatLng(coordinate.getLatitude(), coordinate.getLongitude()));


                                                                    // set polygon style options
                                                                    //applyPolygonStyle(nodeCategory, "normal", polygonOptions);

                                                                    Bitmap bitmap = createTextLabel(cRoom.getId());
                                                                    MarkerOptions markerOptions = new MarkerOptions()
                                                                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                                                                            .title(cRoom.getLabel())
                                                                            .snippet(cRoom.getStringAttribute("snippet", cRoom.getLabel()))
                                                                            .position(getPolygonCenter(polygonOptions.getPoints()));

                                                                    cRoom.setAttribute("MarkerOptions", markerOptions);
                                                                    cRoom.setAttribute("PolygonOptions", polygonOptions);
                                                                }


                                                            }


                                                        }


                                                    }


                                                }

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // update shared legend with current list
                    SharedPreferences.Editor editor = prefMapLegend.edit();

                    for(String category : categories)
                        if(!prefMapLegend.contains(category))
                            editor.putBoolean(category, true);

                    for(String category : prefMapLegend.getAll().keySet().toArray(new String[] {}))
                        if(!categories.contains(category))
                            editor.remove(category);

                    editor.commit();

                    Log.w(name, "Parsing KML finished!");
                } catch (Exception e) {
                    publishProgress(e.getMessage());
                    e.printStackTrace();

                    // exit fragment
                    //cancel(true);
                    getFragmentManager().popBackStackImmediate();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                // go to first available location
                if (cUniversity.hasChildren())
                    gotoLocation(cUniversity.getChild(0));

            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                Toast.makeText(getActivity(), values[0], Toast.LENGTH_LONG).show();
            }
        }).execute();
        /**/


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        // cancel loader task
        if(atLoaderTask != null && atLoaderTask.getStatus() == AsyncTask.Status.RUNNING)
            atLoaderTask.cancel(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //inflate layout
        View view = inflater.inflate(R.layout.fragment_maps, container, false);


        spinnerSelectFocus = (Spinner)view.findViewById(R.id.mapsFragment_spinFocus);
        spinnerSelectFocus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Container container = (Container) parent.getSelectedItem();

                if (position > 0) {
                    container.getParent().setAttribute("current", container.getParent().indexOf(container));

                    List<Container> containers = new ArrayList<Container>(container.getChildren());
                    spinnerSelectFloor.setVisibility(View.VISIBLE);
                    spinnerSelectFloor.setAdapter(new ArrayAdapter(
                            getActivity(),
                            android.R.layout.simple_spinner_dropdown_item,
                            containers));
                    spinnerSelectFloor.setSelection(container.getIntAttribute("current", 0));

                } else {
                    container.setAttribute("current", -1);

                    spinnerSelectFloor.setVisibility(View.GONE);
                    spinnerSelectFloor.setAdapter(null);

                    interiorEnabled = false;
                    refreshGoogleMap();

                    animateCameraTo((LatLngBounds) container.getAttribute("LatLngBounds"));
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spinnerSelectFloor = (Spinner)view.findViewById(R.id.mapsFragment_spinFloor);
        spinnerSelectFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Container container = (Container) parent.getSelectedItem();
                container.getParent().setAttribute("current", position);

                interiorEnabled = true;
                refreshGoogleMap();

                animateCameraTo((LatLngBounds) container.getParent().getAttribute("LatLngBounds"));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        SupportMapFragment supportMapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                Log.w(name, "GoogleMap ready!");

                // set class scope variable
                cGoogleMap = googleMap;

                //googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                // set googleMap user interface settings
                UiSettings settings = googleMap.getUiSettings();
                settings.setRotateGesturesEnabled(false);
                settings.setZoomControlsEnabled(true);
                googleMap.setIndoorEnabled(false);
                googleMap.setMyLocationEnabled(true);
                settings.setMapToolbarEnabled(false);
                settings.setMyLocationButtonEnabled(false);

                googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                    @Override
                    public void onCameraChange(CameraPosition cameraPosition) {
                        try {


                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                    }
                });

                // set google map onMarkerClickListener
                googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        marker.showInfoWindow();
                        return true;
                    }
                });

                // set google map onMapClickListener
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

                    @Override
                    public void onMapClick(LatLng latLng) {
                        try {
                            // get current active location
                            final Container cLocation = cUniversity.getChild(cUniversity.getIntAttribute("current", 0));

                            // determine which building was clicked
                            boolean selected = false;
                            for (final Container cBuilding : cLocation.getChildren()) {
                                if (((LatLngBounds) cBuilding.getAttribute("LatLngBounds")).contains(latLng)) {
                                    selected = true; // building was selected

                                    // allow building click if not viewing interior
                                    if (!interiorEnabled) {

                                        // change building floor polygons
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                                        builder.setTitle(getResources().getString(R.string.maps_dialog_choose_floor));
                                        builder.setAdapter(
                                                new ArrayAdapter<Container>(
                                                        getActivity(),
                                                        android.R.layout.simple_list_item_1,
                                                        cBuilding.getChildren()
                                                ),
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        gotoFloor(cBuilding.getChild(which));

                                                        // close dialog
                                                        dialog.dismiss();

                                                    }
                                                });
                                        builder.create().show();
                                    }

                                }
                            } // end building loop

                            if (!selected)
                                gotoLocation(cLocation);

                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }

                    }
                });


                /**/
            }
        });


        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_maps, menu);
        final MenuItem searchMenuItem = menu.findItem(R.id.maps_action_search);
        final SearchView searchView = (SearchView)searchMenuItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // take focus off search view
                //searchMenuItem.collapseActionView();
                searchView.clearFocus();

                final String[] terms = query.split("\\s+");
                (new AsyncTask<String[], String, List<Container>>() {

                    private String join(String separator, String[] strings) {
                        String temp = "";
                        for(int x = 0; x < strings.length - 1; x++)
                            temp += strings[x] + separator;

                        temp += strings[strings.length - 1];

                        return temp;
                    }
                    private String join(String separator, List<String> strings) {
                        return join(separator, strings.toArray(new String[] {}));
                    }
                    private List<Container> findContainers(Container container, String[] criteria) {
                        List<Container> temp = new ArrayList<Container>();


                        for(Container child : container.getChildren()){
                            String search = join(" ", getCrumbs(child));
                            search += " " + child.getId();
                            search += " " + child.getStringAttribute("label", "");
                            search += " " + child.getStringAttribute("snippet", "");

                            //String regex = "(?i).*\\b(?:" + join("|", criteria) + ").*";
                            String regex = "(?i)^(?=.*\\b" + join(")(?=.*\\b", criteria) + ").*$";
                            if(search.matches(regex))
                                temp.add(child);

                            if(child.hasChildren())
                                temp.addAll(findContainers(child, criteria));
                        }

                        return temp;
                    }
                    private String[] getCrumbs(Container container) {
                        List<String> crumbs = new ArrayList<String>();
                        Container obj = container;
                        while(obj.hasParent()){
                            obj = obj.getParent();
                            crumbs.add(obj.getId());
                        }
                        String[] reversed = new String[crumbs.size()];
                        for(int x = 0; x < crumbs.size(); x++)
                            reversed[x] = crumbs.get(crumbs.size() - 1 - x);

                        return reversed;
                    }

                    @Override
                    protected List<Container> doInBackground(String[]... params) {
                        return findContainers(cUniversity, params[0]);
                    }

                    @Override
                    protected void onPostExecute(final List<Container> containers) {
                        super.onPostExecute(containers);
                        List<Map<String, ?>> results = new ArrayList<Map<String, ?>>();
                        for(Container container : containers){
                            Map<String, Object> row = new HashMap<String, Object>();
                            row.put("container", container);
                            row.put("breadcrumb", join(" > ", getCrumbs(container)));

                            results.add(row);
                        }


                        new AlertDialog.Builder(getActivity())
                                .setTitle(getResources().getString(R.string.maps_dialog_search_results))
                                .setAdapter(new SimpleAdapter(
                                                getActivity(),
                                                results,
                                                android.R.layout.simple_list_item_2,
                                                new String[] {"container", "breadcrumb"},
                                                new int[] {android.R.id.text1, android.R.id.text2}
                                        ),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                Container container = containers.get(which);

                                                // not enough planning; definitely did not think this through
                                                // find depth of object in hierarchy
                                                int counter = 0;
                                                Container obj = container;
                                                while(obj.hasParent()) {
                                                    obj = obj.getParent();
                                                    counter++;
                                                }

                                                // then call appropriate function
                                                switch(counter){
                                                    case 1:
                                                        gotoLocation(container);
                                                        break;
                                                    case 2:
                                                        gotoBuilding(container);
                                                        break;
                                                    case 3:
                                                        gotoFloor(container);
                                                        break;
                                                    case 4:
                                                        // this is a category/legend container
                                                        break;
                                                    case 5:
                                                        gotoRoom(container);
                                                        break;

                                                }

//                                                // hide keyboard after search executes
//                                                InputMethodManager inputMethodManager = (InputMethodManager) getActivity()
//                                                        .getSystemService(Activity.INPUT_METHOD_SERVICE);
//                                                inputMethodManager.hideSoftInputFromWindow(
//                                                        getActivity().getCurrentFocus().getWindowToken(),
//                                                        0
//                                                );


                                            }
                                        })
                                .create().show();
                    }
                }).execute(terms);

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //String[] terms = newText.split("(?ix)[\\w]+");

                return false;
            }
        });



        super.onCreateOptionsMenu(menu, inflater);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.maps_action_goto:
                // go to new location
                new AlertDialog.Builder(getActivity())
                        .setTitle(getResources().getString(R.string.maps_dialog_choose_location))
                        .setSingleChoiceItems(new ArrayAdapter<Container>(
                                getActivity(),
                                android.R.layout.simple_list_item_single_choice,
                                cUniversity.getChildren()),
                                cUniversity.getIntAttribute("current", 0), // set default selection
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        gotoLocation(cUniversity.getChild(which));
                                        dialog.dismiss();
                                    }
                                })
                        .create().show();

                break;

            case R.id.maps_action_legend:
                Map<String, ?> pref = prefMapLegend.getAll();
                final String[] keys = pref.keySet().toArray(new String[] {});
                final Boolean[] vals = pref.values().toArray(new Boolean[]{});
                new AlertDialog.Builder(getActivity())
                        .setTitle(getResources().getString(R.string.maps_dialog_choose_legend))
                        .setMultiChoiceItems(
                                keys,
                                unboxBooleanClassToPrim(vals),
                                new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                        prefMapLegend.edit().putBoolean(keys[which], isChecked).commit();

                                    }
                                }
                        )
                        .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                refreshGoogleMap();
                                dialog.dismiss();
                            }
                        })
                        .create().show();


        }
        //return false;
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.w(name, "Attached!");
    }



    // CUSTOM CLASSES AND METHODS

    private void refreshGoogleMap() {
        Log.w(name, "Refreshing GoogleMap!");
        cGoogleMap.clear();


        try{
            Container cLocation = cUniversity.getChild(cUniversity.getIntAttribute("current", 0));

            // add building polygons to google map
            for (Container cBuilding : cLocation.getChildren()) {
                Polygon polygon = cGoogleMap.addPolygon((PolygonOptions) cBuilding.getAttribute("PolygonOptions"));
                applyPolygonStyle(cBuilding.getStringAttribute("StyleUrl"), "normal", polygon);
            }

            // add floor polygons to google map
            if(interiorEnabled) {
                Container cBuilding = cLocation.getChild(cLocation.getIntAttribute("current"));
                Container cFloor = cBuilding.getChild(cBuilding.getIntAttribute("current"));

                for(Container cCategory : cFloor.getChildren()) {
                    for (Container cRoom : cCategory.getChildren()) {
                        if(cRoom.hasAttribute("PolygonOptions")) {
                            Polygon polygon = cGoogleMap.addPolygon((PolygonOptions) cRoom.getAttribute("PolygonOptions"));

                            if (prefMapLegend.getBoolean(cCategory.getId(), true))
                                applyPolygonStyle(
                                        cRoom.getStringAttribute("StyleUrl"),
                                        "normal",
                                        polygon
                                );


                        }
                        if(cRoom.hasAttribute("MarkerOptions")) {
                            Marker marker = cGoogleMap.addMarker((MarkerOptions) cRoom.getAttribute("MarkerOptions"));

                            // a crude way to display the selected room set in gotoRoom function
                            if(cFloor.hasAttribute("current")) {
                                if (cFloor.getAttribute("current").equals(cRoom)) {
                                    cFloor.removeAttribute("current");
                                    marker.showInfoWindow();
                                }
                            }

                            marker.setVisible(prefMapLegend.getBoolean(cCategory.getId(), true));
                        }



                    }
                }

            }
        }catch(Exception ex) {
            ex.printStackTrace();
        }

        // map-fragment programmers add your Easter Egg here  ;3
        if(interiorEnabled) {
            Bitmap bitmap = BitmapFactory.decodeStream(getFile("http://maps.google.com/mapfiles/kml/shapes/info-i.png"));
            MarkerOptions m1 = new MarkerOptions()
                    //.icon(BitmapDescriptorFactory.fromBitmap(createTextLabel("Jefferey Ostapchuk")))
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .title("Jefferey Ostapchuk")
                    .snippet("Developer of Class 2015!")
                    .position(new LatLng(41.530696, -87.144952));
            cGoogleMap.addMarker(m1);
        }


    }
    private void animateCameraTo(LatLngBounds bounds) {
        cGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
    }
    private void moveCameraTo(LatLngBounds bounds) {
        cGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
    }


    private void gotoLocation(Container cLocation) {
        // set current location
        cLocation.getParent().setAttribute("current", cLocation.getParent().indexOf(cLocation));

        List<Container> containers = new ArrayList<Container>();
        containers.add(cLocation);
        containers.addAll(cLocation.getChildren());
        spinnerSelectFocus.setAdapter(new ArrayAdapter(
                getActivity(),
                android.R.layout.simple_spinner_dropdown_item,
                containers));
        spinnerSelectFocus.setSelection(0);
    }
    private void gotoBuilding(Container cBuilding) {
        Container cLocation = cBuilding.getParent();

        // set current building
        cLocation.setAttribute("current", cBuilding.getParent().indexOf(cBuilding));

        if(cUniversity.getIntAttribute("current", -1) != cUniversity.indexOf(cLocation))
            gotoLocation(cLocation);

        // set spinner
        try {
            if (!spinnerSelectFocus.getSelectedItem().equals(cBuilding))
                setSpinner(spinnerSelectFocus, cBuilding);

        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
    private void gotoFloor(Container cFloor) {
        Container cBuilding = cFloor.getParent();
        Container cLocation = cBuilding.getParent();

        // set current floor
        cBuilding.setAttribute("current", cBuilding.indexOf(cFloor));

        if(cUniversity.getIntAttribute("current", -1) != cUniversity.indexOf(cLocation))
            gotoLocation(cLocation);

        if(cLocation.getIntAttribute("current", -1) != cLocation.indexOf(cBuilding))
            gotoBuilding(cBuilding);

        try {
            if (!spinnerSelectFloor.getSelectedItem().equals(cFloor))
                setSpinner(spinnerSelectFloor, cBuilding.getChild(cBuilding.getIntAttribute("current")));

        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
    private void gotoRoom(Container cRoom) {
        Container cCategory = cRoom.getParent();
        Container cFloor = cCategory.getParent();
        Container cBuilding = cFloor.getParent();
        Container cLocation = cBuilding.getParent();

        cFloor.setAttribute("current", cRoom);

        if(cUniversity.getIntAttribute("current", -1) != cUniversity.indexOf(cLocation))
            gotoLocation(cLocation);

        if(cLocation.getIntAttribute("current", -1) != cLocation.indexOf(cBuilding))
            gotoBuilding(cBuilding);

        if(cBuilding.getIntAttribute("current", -1) != cBuilding.indexOf(cFloor))
            gotoFloor(cFloor);
        else
            refreshGoogleMap();


    }


    public void setSpinner(Spinner spinner, Container container) {
        for(int x = 0; x < spinner.getCount(); x++)
            if(spinner.getItemAtPosition(x).equals(container))
                spinner.setSelection(x);
    }


    public String normalize(String text) {
        //return Normalizer.normalize(text, Normalizer.Form.NFKD);
        return text.replaceAll("\\P{Print}", "");
    }

    public LatLngBounds getPolygonBounds(List<LatLng> points) {
        List<Double> lat = new ArrayList<Double>();
        List<Double> lng = new ArrayList<Double>();

        for(LatLng point : points) {
            lat.add(point.latitude);
            lng.add(point.longitude);
        }

        return new LatLngBounds(
                new LatLng(
                        Collections.min(lat),
                        Collections.min(lng)
                ),
                new LatLng(
                        Collections.max(lat),
                        Collections.max(lng)
                )
        );
    }

    public LatLng getPolygonCenter(List<LatLng> points) {
        return getPolygonBounds(points).getCenter();
    }

    public InputStream getFile(String uri)  {
        try {
            final URL url = new URL(uri);
            final String filename = (new File(url.getFile())).getName();
            final File file = new File(getActivity().getFilesDir(), filename);

            Thread thread = (new Thread(new Runnable() {

                @Override
                public void run() {
                    ConnectivityManager connMgr = (ConnectivityManager)
                            getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

                    try {
                        if (networkInfo != null && networkInfo.isConnected()) {

                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                            if (!file.exists() || file.lastModified() < connection.getLastModified()) {
                                FileOutputStream fileOutputStream = null;
                                InputStream inputStream = null;
                                try {
                                    inputStream = connection.getInputStream();
                                    fileOutputStream = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);

                                    int size = 65536;
                                    int count = 0;
                                    byte[] buffer = new byte[size];
                                    while ((count = inputStream.read(buffer)) >= 0)
                                        fileOutputStream.write(buffer, 0, count);


                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                } finally {
                                    if (fileOutputStream != null)
                                        fileOutputStream.close();

                                    if(inputStream != null)
                                        inputStream.close();
                                }
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
            thread.start();
            thread.join();

            if(file.exists())
                return getActivity().openFileInput(filename);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return null;
    }

    public LatLngBounds createBoundsFromOverlay(com.ekito.simpleKML.model.GroundOverlay groundOverlay) {
        com.ekito.simpleKML.model.GroundOverlay temp =
                (com.ekito.simpleKML.model.GroundOverlay) groundOverlay;

        // convert GroundOverlay to LatLngBounds
        return new LatLngBounds(
                new LatLng(
                        Double.parseDouble(temp.getLatLonBox().getSouth()),
                        Double.parseDouble(temp.getLatLonBox().getWest())),
                new LatLng(
                        Double.parseDouble(temp.getLatLonBox().getNorth()),
                        Double.parseDouble(temp.getLatLonBox().getEast())
                )
        );
    }


    public Container createContainer(Feature f, Container parent) {

        Container c = new Container(
                f.getName(),
                f.getName(),
                parent
        );
        if (f.getDescription() != null)
            for (String kvp : f.getDescription().trim().split("\\n"))
                c.setAttribute(kvp);

        if (c.hasAttribute("label"))
            c.setLabel(c.getStringAttribute("label"));

        return c;
    }

    private Bitmap createTextLabel(String text) {
        int textSize = Integer.parseInt(getResources().getString(R.string.maps_setting_font_size));

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        paint.setTextSize(textSize);

        Rect dimensions = new Rect();
        paint.getTextBounds(text, 0, text.length(), dimensions);

        Bitmap bitmap = Bitmap.createBitmap(dimensions.width() + 5, dimensions.height() + 5, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(text, 0, bitmap.getHeight(), paint);

        return bitmap;
    }


    public void findKmlStyles(Feature kml) {
        if (kml.getStyleSelector() != null)
            for (StyleSelector style : kml.getStyleSelector())
                if (style instanceof StyleSelector)
                    cStyleSelectors.put(normalize(style.getId()), style);

    }
    public Style getKmlStyle(String styleUrl, String styleKey) {
        StyleMap styleMap = (StyleMap) cStyleSelectors.get(normalize(styleUrl).substring(1));
        for (Pair pair : styleMap.getPairList()) {
            if (normalize(pair.getKey()).equals(normalize(styleKey))) {
                return (Style) cStyleSelectors.get(normalize(pair.getStyleUrl()).substring(1));
            }
        }


        return null;
    }
    public Polygon applyPolygonStyle(String styleUrl, String style, Polygon polygon) {
        Style s = getKmlStyle(styleUrl, style);

        try {
            LineStyle lineStyle = s.getLineStyle();

            Float width = lineStyle.getWidth();
            polygon.setStrokeWidth(lineStyle.getWidth());

            String color = lineStyle.getColor();
            String[] abgr = splitEqually(normalize(color), 2).toArray(new String[]{});
            polygon.setStrokeColor(Color.parseColor('#' + abgr[0] + abgr[3] + abgr[2] + abgr[1]));
        }catch (Exception ex) {
            ex.printStackTrace();
        }

        try{
            PolyStyle polyStyle = s.getPolyStyle();

            String color = polyStyle.getColor();
            if(color != null) {
                String[] abgr = splitEqually(normalize(s.getPolyStyle().getColor()), 2).toArray(new String[]{});
                polygon.setFillColor(Color.parseColor('#' + abgr[0] + abgr[3] + abgr[2] + abgr[1]));
            }
        }catch (Exception ex) {
            ex.printStackTrace();
        }

        return polygon;
    }

    private boolean[] unboxBooleanClassToPrim(Boolean[] array) {
        boolean[] temp = new boolean[array.length];
        for(int x = 0; x < array.length; x++)
            temp[x] = array[x].booleanValue();

        return temp;
    }

    // String.split sucks at "(?<=\\G.{2})"
    public static List<String> splitEqually(String text, int size) {
        // Give the list the right capacity to start with. You could use an array
        // instead if you wanted.
        List<String> ret = new ArrayList<String>((text.length() + size - 1) / size);

        for (int start = 0; start < text.length(); start += size) {
            ret.add(text.substring(start, Math.min(text.length(), start + size)));
        }
        return ret;
    }

    // generic class to store xml objects for google map
    private class Container {
        private String _id;
        private String _label;
        private String _toString;
        private Container _parent;
        private ArrayList<Container> _children = new ArrayList<>();

        private HashMap<String, Object> _attributes = new HashMap<>();

        public Container(String id, String label) {
            this(id, label, null);
        }
        public Container(String id, String label, Container parent) {
            _id = id;
            _label = label;

            if(parent != null)
                _parent = parent.addChild(this);
        }

        public Container addChild(Container child) {
            // prevent duplicate entries
            if(!_children.contains(child))
                _children.add(child);
            return this;
        }
        public Container getChild(int index) {
            return _children.get(index);
        }
        public int indexOf(Container child) {
            return _children.indexOf(child);
        }
        public Container removeChild(int index) {
            return _children.remove(index);
        }
        public boolean hasChildren() {
            return _children.size() > 0;
        }
        public ArrayList<Container> getChildren() {
            return (ArrayList<Container>)_children.clone();
        }
        public Container findChildByAttribute(String key, Object val) {
            for(Container child : _children)
                if(child.hasAttribute(key))
                    if(child.getAttribute(key) == val)
                        return child;

            return null;
        }

        public String getId() {
            return _id;
        }
        public String getLabel() {
            return _label;
        }
        public void setLabel(String value) { _label = value; }
        public void setParent(Container parent) {
            if(parent != null)
                _parent = parent.addChild(this);
        }
        public Container getParent() {
            return _parent;
        }
        public boolean hasParent() {
            return _parent != null;
        }
        public void setToString(String toString) {
            _toString = toString;
        }

        public void setAttribute(String kvp) {
            String[] temp = kvp.split("=");
            setAttribute(temp[0], temp[1]);
        }
        public void setAttribute(String key, Object val) {
            if(!_attributes.containsKey(key))
                _attributes.remove(key);

            _attributes.put(key, val);
        }
        public Object getAttribute(String key) {
            return _attributes.get(key);
        }
        public Object getAttribute(String key, Object defaultValue) {
            if(!hasAttribute(key))
                return defaultValue;

            return getAttribute(key);
        }
        public Object removeAttribute(String key) {
            return _attributes.remove(key);
        }
        public String getStringAttribute(String key) {
            return (String)getAttribute(key);
        }
        public String getStringAttribute(String key, String defaultValue) {
            if(hasAttribute(key))
                return getStringAttribute(key);

            return defaultValue;
        }
        public Integer getIntAttribute(String key) {
            if(getAttribute(key) instanceof String)
                return Integer.parseInt(getStringAttribute(key));

            return (Integer)getAttribute(key);
        }
        public Integer getIntAttribute(String key, int defaultValue) {
            if(hasAttribute(key))
                return getIntAttribute(key);

            return defaultValue;
        }
        public Integer removeIntAttribute(String key) {
            return (Integer)removeAttribute(key);
        }
        public Float getFloatAttribute(String key) {
            if(getAttribute(key) instanceof String)
                return Float.parseFloat(getStringAttribute(key));

            return (Float)getAttribute(key);
        }
        public Float getFloatAttribute(String key, float defaultValue) {
            if(hasAttribute(key))
                return getFloatAttribute(key);

            return defaultValue;
        }
        public Float removeFloatAttribute(String key) {
            return (Float)removeAttribute(key);
        }
        public boolean hasAttribute(String key) {
            return _attributes.containsKey(key);
        }

        @Override
        public String toString() {
            if(_toString != null)
                return _toString;

            return _id + " - " + _label;
        }

        public String getPath(String delimiter) {
            String temp = "";
            String[] strings = getPath();
            for(int x = 0; x < strings.length - 1; x++)
                temp += strings[x] + delimiter;

            temp += strings[strings.length - 1];

            return temp;
        }

        public String[] getPath() {
            List<String> crumbs = new ArrayList<String>();

            Container obj = this;
            while(obj.hasParent()){
                obj = obj.getParent();
                crumbs.add(obj.getId());
            }

            String[] reversed = new String[crumbs.size()];
            for(int x = 0; x < crumbs.size(); x++)
                reversed[x] = crumbs.get(crumbs.size() - 1 - x);

            return reversed;
        }
    }

}