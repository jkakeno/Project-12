package com.jkdevelopment.astronomy;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.jkdevelopment.astronomy.Model.Apod;
import com.jkdevelopment.astronomy.Model.ApodAndEpic;
import com.jkdevelopment.astronomy.Model.Cover;
import com.jkdevelopment.astronomy.Model.Earth.Assets;
import com.jkdevelopment.astronomy.Model.Earth.Image;
import com.jkdevelopment.astronomy.Model.Earth.ResultsItem;
import com.jkdevelopment.astronomy.Model.Epic;
import com.jkdevelopment.astronomy.Model.LibraryImage.LibraryImageCollection;
import com.jkdevelopment.astronomy.Model.Rover;
import com.jkdevelopment.astronomy.Model.RoverImage.RoverImages;
import com.jkdevelopment.astronomy.Model.RoverList;
import com.jkdevelopment.astronomy.Retrofit.ApiInterface;
import com.jkdevelopment.astronomy.Retrofit.ApiUtils;
import com.jkdevelopment.astronomy.UI.ApodFragment;
import com.jkdevelopment.astronomy.UI.ContainerFragment;
import com.jkdevelopment.astronomy.UI.CoverListFragment;
import com.jkdevelopment.astronomy.UI.DelayedProgressDialog;
import com.jkdevelopment.astronomy.UI.EarthImageFragment;
import com.jkdevelopment.astronomy.UI.ImageListFragment;
import com.jkdevelopment.astronomy.UI.ImageSearchFragment;
import com.jkdevelopment.astronomy.UI.LocationPickFragment;
import com.jkdevelopment.astronomy.UI.MarsImageSearchFragment;
import com.jkdevelopment.astronomy.UI.MarsImageListFragment;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements InteractionListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String COVER_LIST_FRAGMENT = "cover_list_fragment";
    private static final String APOD_FRAGMENT = "apod_fragment";
    private static final String LOCATION_PICK_FRAGMENT = "location_pick_fragment";
    private static final String EARTH_IMAGE_FRAGMENT = "earth_picture_fragment";
    private static final String MARS_IMAGE_SEARCH_FRAGMENT = "mars_image_search_fragment";
    private static final String MARS_IMAGE_LIST_FRAGMENT = "mars_image_list_fragment";
    private static final String IMAGE_SEARCH_FRAGMENT = "image_search_fragment";
    private static final String IMAGE_LIST_FRAGMENT = "image_list_fragment";
    private static final String PROGRESS_DIALOG = "progress_dialog";

    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

    FragmentManager fragmentManager;
    Cover apodCover;
    Cover earthCover;
    Cover marsCover;
    Cover searchCover;
    Rover roverSelected;
    Apod apod;
    LibraryImageCollection imageCollection;
    DelayedProgressDialog progressDialog = new DelayedProgressDialog();

    ApiInterface apodAPIInterface;
    ApiInterface epicAPIInterface;
    ApiInterface imageLibraryAPIInterface;
    ApiInterface roverImagesAPIInterface;
    ApiInterface assetsAPIInterface;
    ApiInterface earthImageryAPIInterface;

    Disposable disposableApodEpic;
    Disposable disposableImageCollection;
    Disposable disposableRoverImages;
    Disposable disposableAssets;
    Disposable disposableResults;
    Disposable disposableEarthImage;

    ArrayList<Cover> coverList= new ArrayList<>();
    RoverList roverList = new RoverList();
    ArrayList<Epic> epicList= new ArrayList<>();
    ArrayList<Image> earthImages=new ArrayList<>();

    LatLng currentLocation;
    LocationManager locationManager;
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG,"wantLocation: " + wantLocation);
            if(wantLocation) {
                progressDialog.cancel();
                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                Log.d(TAG,"Latitude: " + String.valueOf(currentLocation.latitude) + '\n'
                        + "Longitude: " + String.valueOf(currentLocation.longitude));

                LocationPickFragment locationPickFragment = LocationPickFragment.newInstance(currentLocation);
                fragmentManager.beginTransaction().replace(R.id.root, locationPickFragment, LOCATION_PICK_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT).commit();

                wantLocation=false;
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    boolean gpsEnabled;
    boolean wantLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate");
        setContentView(R.layout.activity_main);

        /*Display cover list fragment.*/
        fragmentManager = getSupportFragmentManager();

        /*Get the location manager and request for location update.*/
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        /*Invoke api methods.*/
        apodAPIInterface = ApiUtils.getApodApiInterface();
        epicAPIInterface = ApiUtils.getEpicApiInterface();
        imageLibraryAPIInterface = ApiUtils.getImageLibraryInterface();
        roverImagesAPIInterface = ApiUtils.getRoverImageInterface();
        assetsAPIInterface = ApiUtils.getAssetsInterface();
        earthImageryAPIInterface = ApiUtils.getEarthImageryInterface();

        /*Get an observable apod object when calling apod api.*/
        Observable<Apod> apodObservable = apodAPIInterface.getApod().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());

        /*Get an observable epic object when calling epic api.*/
        Observable<ArrayList<Epic>> epicObservable = epicAPIInterface.getEpicImageList().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());

        /*Combine the 2 observables apod and epic.*/
        Observable<ApodAndEpic> combinedObservable = Observable.zip(apodObservable, epicObservable, new BiFunction<Apod, ArrayList<Epic>, ApodAndEpic>() {
                    @Override
                    public ApodAndEpic apply(Apod apod, ArrayList<Epic> epics) throws Exception {
                        return new ApodAndEpic(apod,epics);
                    }
                });

        /*Create an anonymous observer to observe the combine observables.*/
        combinedObservable.subscribe(new Observer<ApodAndEpic>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "ApodEpic Observable onSubscribe()");
                /*Store the disposableApodEpic to dispose of this observer later.*/
                disposableApodEpic = d;
                progressDialog.show(fragmentManager,PROGRESS_DIALOG);
            }

            @Override
            public void onNext(ApodAndEpic apodAndEpic) {
                Log.d(TAG, "ApodEpic Observable onNext()");
                /*Store apod for later use.*/
                apod = apodAndEpic.getApod();
                /*Set apod cover and add it to the cover list.*/
                apodCover = new Cover("APOD");
                apodCover.setApod(apod);
                coverList.add(apodCover);
                /*Set epic cover and add it to the cover list.*/
                earthCover = new Cover("EARTH");
                epicList = apodAndEpic.getEpicList();
                earthCover.setEpicImageList(epicList);
                coverList.add(earthCover);
            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "ApodEpic Observable onError()"+e.getMessage());
                progressDialog.cancel();
                showDialog("error",e.getMessage());
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "ApodEpic Observable onCompleted()");
                progressDialog.cancel();
                /*Once the anonymous observer has completed receiving omitted items add static covers to the cover list and start cover list fragment.*/
                coverList.add(marsCover);
                coverList.add(searchCover);

                CoverListFragment coverListFragment = CoverListFragment.newInstance(coverList);
                fragmentManager.beginTransaction().replace(R.id.root, coverListFragment, COVER_LIST_FRAGMENT).commit();
            }
        });

        /*Set up the covers with static content.*/
        marsCover = new Cover("MARS");
        marsCover.setImageTitle("Rover Photos");
        marsCover.setImageResource(Uri.parse("android.resource://com.jkdevelopment.astronomy/" + R.drawable.cover_mars));

        searchCover = new Cover("SEARCH");
        searchCover.setImageTitle("NASA Image Library");
        searchCover.setImageResource(Uri.parse("android.resource://com.jkdevelopment.astronomy/" + R.drawable.cover_library));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"onPause");
        if(disposableApodEpic!=null&&!disposableApodEpic.isDisposed()) {
            disposableApodEpic.dispose();
            Log.d(TAG,"ApodEpic Observable Disposed.");
        }
        if(disposableImageCollection!=null&&!disposableImageCollection.isDisposed()) {
            disposableImageCollection.dispose();
            Log.d(TAG,"LibraryImageCollection Observable Disposed.");
        }
        if(disposableRoverImages!=null&&!disposableRoverImages.isDisposed()) {
            disposableRoverImages.dispose();
            Log.d(TAG,"RoverImages Observable Disposed.");
        }
        if(disposableAssets!=null&&!disposableAssets.isDisposed()) {
            disposableAssets.dispose();
            Log.d(TAG,"Assets Observable Disposed.");
        }
        if(disposableResults!=null&&!disposableResults.isDisposed()) {
            disposableResults.dispose();
            Log.d(TAG,"Results Observable Disposed.");
        }
        if(disposableEarthImage!=null&&!disposableEarthImage.isDisposed()) {
            disposableEarthImage.dispose();
            Log.d(TAG,"Image Observable Disposed.");
        }
    }


    @Override
    public void onCoverSelectInteraction(Cover cover) {
        String coverTitle = cover.getCoverTitle();

        /*Replace each cover fragment with root and add cover list fragment to the back stack so that cover list fragment's onCreateView is called when back button is pushed and animation is played.*/
        switch (coverTitle) {
            case "APOD":
                ApodFragment apodFragment = ApodFragment.newInstance(apod);
                fragmentManager.beginTransaction().replace(R.id.root, apodFragment, APOD_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT).commit();
                break;
            case "EARTH":

                /*Check GSP setting*/
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if(!gpsEnabled){
                    enableGPS();
                }else if (Build.VERSION.SDK_INT >= 23) {
                    // Marshmallow+
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE_ASK_PERMISSIONS);
                    }else if(currentLocation==null){
                        wantLocation=true;
                        /*NOTE: After requestLocationUpdates() is called takes about 5sec to get a location with a location listener.*/
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 10, locationListener);
                        progressDialog.show(fragmentManager,PROGRESS_DIALOG);
                    }else{
                        LocationPickFragment locationPickFragment = LocationPickFragment.newInstance(currentLocation);
                        fragmentManager.beginTransaction().replace(R.id.root, locationPickFragment, LOCATION_PICK_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT).commit();
                    }
                } else {
                    // Pre-Marshmallow
                    if(currentLocation==null){
                        wantLocation=true;
                        /*NOTE: After requestLocationUpdates() is called takes about 5sec to get a location with a location listener.*/
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 10, locationListener);
                        progressDialog.show(fragmentManager,PROGRESS_DIALOG);
                    }else{
                        LocationPickFragment locationPickFragment = LocationPickFragment.newInstance(currentLocation);
                        fragmentManager.beginTransaction().replace(R.id.root, locationPickFragment, LOCATION_PICK_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT).commit();
                    }
                }

                break;
            case "MARS":
                MarsImageSearchFragment marsImageSearchFragment = MarsImageSearchFragment.newInstance(roverList);
                fragmentManager.beginTransaction().replace(R.id.root, marsImageSearchFragment, MARS_IMAGE_SEARCH_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT).commit();
                break;
            case "SEARCH":
                ImageSearchFragment imageSearchFragment = new ImageSearchFragment();
                ImageListFragment imageListFragment = new ImageListFragment();

                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.root,imageSearchFragment,IMAGE_SEARCH_FRAGMENT).addToBackStack(COVER_LIST_FRAGMENT);
                fragmentTransaction.add(R.id.image_list_container,imageListFragment,IMAGE_LIST_FRAGMENT);
                fragmentTransaction.commit();

                break;
        }
    }

    public void enableGPS() {
        showDialog("gps","Please enable location on your phone to use this feature.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    showDialog("permission","Location permission was granted...");

                } else {
                    // Permission Denied
                    showDialog("permission","Location permission was denied...");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onGetEarthImageryInteraction(LatLng currentLocation, String date) {

        earthImages.clear();

        Log.d(TAG,"Latitude: " + String.valueOf(currentLocation.latitude) + '\n'
                + "Longitude: " + String.valueOf(currentLocation.longitude) + '\n'
                + "Date: " + date);

        final String lat = String.valueOf(currentLocation.latitude);
        final String lon = String.valueOf(currentLocation.longitude);

        /*Get an Observable Asset. This api call returns an observable as defined in the ApiInterface.*/
        assetsAPIInterface.getAssets(lon,lat,date).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Assets>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "Assets Observable onSubscribed()");
                disposableAssets=d;
                progressDialog.show(fragmentManager,PROGRESS_DIALOG);
            }

            @Override
            public void onNext(Assets assets) {
                Log.d(TAG, "Assets Observable onNext()");

                /*Get the Result list from Assets.*/
                List<ResultsItem> resultsItemList = assets.getResults();

                if (assets.getCount()!=0 && !resultsItemList.isEmpty()) {
                    /*Make the list of results an iterable observable. Similar function as for loop.*/
                    /*Limit the number of emitted items to 15 to prevent errors (HTTP 429 Too Many Requests, Timeout).*/
                    /*Cascade observables to get the image for each result date.*/
                    Observable
                            .fromIterable(resultsItemList)
                            .take(15)
                            .concatMap(new Function<ResultsItem, ObservableSource<ResultsItem>>() {
                                @Override
                                public ObservableSource<ResultsItem> apply(final ResultsItem resultsItem) throws Exception {
                                    /*Get the image date from result and format so its acceptible to the api.*/
                                    String date = resultsItem.getDate();
                                    String[] dateParts = date.split("T");
                                    final String imageDate = dateParts[0];
                                    /*Call earthImageryAPIInterface for each result to get the image.*/
                                    return earthImageryAPIInterface.getEarthImage(lon, lat, imageDate).map(new Function<Image, ResultsItem>() {
                                        @Override
                                        public ResultsItem apply(Image image) throws Exception {
                                            /*The concatmap function expects a result as a return so add the image to a result.
                                            * NOTE: that an image field had to be added to the result class for this.
                                            * Then return a result.*/
                                            Log.d(TAG,"Get an image and set result...");
                                            resultsItem.setImage(image);
                                            return resultsItem;
                                        }
                                    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
                                }
                            })
                            .subscribe(new Observer<ResultsItem>() {
                                @Override
                                public void onSubscribe(Disposable d) {
                                    Log.d(TAG, "Results Observable onSubscribed()");
                                    disposableResults = d;
                                }

                                @Override
                                public void onNext(ResultsItem resultsItem) {
                                    Log.d(TAG, "Results Observable onNext()");
                                    /*Add a image from result to the earth image list.*/
                                    earthImages.add(resultsItem.getImage());
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.d(TAG,"Result error: " + e.getMessage());
                                    progressDialog.cancel();
                                    showDialog("error",e.getMessage());
                                }

                                @Override
                                public void onComplete() {
                                    Log.d(TAG, "Results Observable onComplete()");
                                    Log.d(TAG,"Result items completed -> Number of Images: " + earthImages.size());
                                    progressDialog.cancel();
                                    Fragment locationPickFragment = fragmentManager.findFragmentByTag(LOCATION_PICK_FRAGMENT);
                                    int locationPickFragmentId = locationPickFragment.getId();
                                    EarthImageFragment earthImageFragment = EarthImageFragment.newInstance(earthImages);

                                    fragmentManager.beginTransaction().replace(locationPickFragmentId, earthImageFragment, EARTH_IMAGE_FRAGMENT).addToBackStack(LOCATION_PICK_FRAGMENT).commit();
                                }
                            });
                }else{
                    progressDialog.cancel();
                    showDialog("message","There aren't images available for the selected date or location. Please select another date or location.");
                }
            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "Assets Observable onError()"+e.getMessage());
                progressDialog.cancel();
                showDialog("error",e.getMessage());
            }

            @Override
            public void onComplete() {
                Log.d(TAG,"Assets Observable onComplete()");
            }
        });
    }

    @Override
    public void onGetRoverImageryInteraction(Rover rover) {

        this.roverSelected = rover;

        String name = rover.getRoverName();
        String url = null;
        switch (name){
            case "curiosity":
                url="/mars-photos/api/v1/rovers/"+"curiosity"+"/photos?api_key=rT9qT3KTMkGOzKSoVtYMjFLkJ7L5sXGA3xymwEqh";
                break;
            case "opportunity":
                url="/mars-photos/api/v1/rovers/"+"opportunity"+"/photos?api_key=rT9qT3KTMkGOzKSoVtYMjFLkJ7L5sXGA3xymwEqh";
                break;
            case "spirit":
                url="/mars-photos/api/v1/rovers/"+"spirit"+"/photos?api_key=rT9qT3KTMkGOzKSoVtYMjFLkJ7L5sXGA3xymwEqh";
                break;
        }
        String sol = rover.getSolSetting();
        String camera = rover.getCameraSetting();

        if(camera.equals("All")){
            /*Get an Observable Rover Images from all cameras. This api call returns an observable as defined in the ApiInterface.*/
            roverImagesAPIInterface.getRoverAllImages(url, sol).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<RoverImages>() {
                @Override
                public void onSubscribe(Disposable d) {
                    Log.d(TAG, "RoverImages Observable onSubscribed()");
                    disposableRoverImages = d;
                    progressDialog.show(fragmentManager, PROGRESS_DIALOG);
                }

                @Override
                public void onNext(RoverImages roverImages) {
                    Log.d(TAG, "RoverImages Observable onNext()");
                    roverSelected.setRoverImages(roverImages);
                }

                @Override
                public void onError(Throwable e) {
                    Log.d(TAG, "RoverImages Observable onError()" + e.getMessage());
                    progressDialog.cancel();
                    showDialog("error", e.getMessage());
                }

                @Override
                public void onComplete() {
                    Log.d(TAG, "RoverImages Observable onComplete()");
                    progressDialog.cancel();
                    if (!roverSelected.getRoverImages().getPhotos().isEmpty()) {
                        MarsImageListFragment marsImageListFragment = MarsImageListFragment.newInstance(roverSelected);
                        ContainerFragment containerFragment = new ContainerFragment();

                        FragmentTransaction fragmentTransaction =fragmentManager.beginTransaction();
                        /*Replace container fragment with the last displayed fragment view. (Container has black background used so that previously displayed fragment doesn't shown during animation).*/
                        fragmentTransaction.replace(R.id.mars_image_container,containerFragment,null);
                        /*Replace container fragment with the fragment we want to see. Save last displayed fragment to backstack.*/
                        /*NOTE that the R.id that the new fragment replaces must be the same as the R.id that MarsImageFragment replaces for animation to work.*/
                        fragmentTransaction.replace(R.id.container, marsImageListFragment, MARS_IMAGE_LIST_FRAGMENT).addToBackStack(MARS_IMAGE_SEARCH_FRAGMENT);
                        fragmentTransaction.commit();
                    } else {
                        showDialog("message", "Threre aren't images available for the selected sol or camera. Please select a different sol or camera.");
                    }
                }
            });
        }else {
        /*Get an Observable Rover Images from a selected camera. This api call returns an observable as defined in the ApiInterface.*/
            roverImagesAPIInterface.getRoverImages(url, sol, camera).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<RoverImages>() {
                @Override
                public void onSubscribe(Disposable d) {
                    Log.d(TAG, "RoverImages Observable onSubscribed()");
                    disposableRoverImages = d;
                    progressDialog.show(fragmentManager, PROGRESS_DIALOG);
                }

                @Override
                public void onNext(RoverImages roverImages) {
                    Log.d(TAG, "RoverImages Observable onNext()");
                    roverSelected.setRoverImages(roverImages);
                }

                @Override
                public void onError(Throwable e) {
                    Log.d(TAG, "RoverImages Observable onError()" + e.getMessage());
                    progressDialog.cancel();
                    showDialog("error", e.getMessage());
                }

                @Override
                public void onComplete() {
                    Log.d(TAG, "RoverImages Observable onComplete()");
                    progressDialog.cancel();
                    if (!roverSelected.getRoverImages().getPhotos().isEmpty()) {
                        MarsImageListFragment marsImageListFragment = MarsImageListFragment.newInstance(roverSelected);
                        ContainerFragment containerFragment = new ContainerFragment();

                        FragmentTransaction fragmentTransaction =fragmentManager.beginTransaction();
                        /*Replace container fragment with the last displayed fragment view. (Container has black background used so that previously displayed fragment doesn't shown during animation).*/
                        fragmentTransaction.replace(R.id.mars_image_container,containerFragment,null);
                        /*Replace container fragment with the fragment we want to see. Save last displayed fragment to backstack.*/
                        /*NOTE that the R.id that the new fragment replaces must be the same as the R.id that MarsImageFragment replaces for animation to work.*/
                        fragmentTransaction.replace(R.id.container, marsImageListFragment, MARS_IMAGE_LIST_FRAGMENT).addToBackStack(MARS_IMAGE_SEARCH_FRAGMENT);
                        fragmentTransaction.commit();
                    } else {
                        showDialog("message", "Threre aren't images available for the selected sol or camera. Please select a different sol or camera.");
                    }
                }
            });
        }
    }

    @Override
    public void onMarsImageShareInteraction(Bitmap bitmap) {

        Uri bitmapUri=getLocalBitmapUri(bitmap);

        if (bitmapUri != null) {
            /*Construct a ShareIntent with link to image*/
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, bitmapUri);
            shareIntent.setType("image/*");
            /*Launch sharing dialog for image*/
            startActivity(Intent.createChooser(shareIntent, "Share Image"));
        } else {
            showDialog("error","Could not share image...");
        }
    }

    private Uri getLocalBitmapUri(Bitmap bitmap) {

        Uri bitmapUri = null;

        try {
            /*Create a file directory to put the image bitmap.*/
            File file = File.createTempFile("share_image_",".png",this.getExternalFilesDir(Environment.DIRECTORY_PICTURES));

            Log.d(TAG,"File dir: "+file.getAbsolutePath());

            /*Create an outputstream that writes bytes to the file directory.*/
            FileOutputStream out = new FileOutputStream(file);
            /*Write a compressed version of the bitmap to the specified outputstream. */
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            /*Close the outputstream.*/
            out.close();

            /*Get the bitmap file uri using the file provider class.*/
            bitmapUri = FileProvider.getUriForFile(this, "com.jkdevelopment.astronomy.fileprovider", file);
        }catch(IOException e){
            e.printStackTrace();
        }
        return bitmapUri;
    }


    @Override
    public void onSearchImageryInteraction(String keyword) {

        /*Get an Observable Library Images. This api call returns an observable as defined in the ApiInterface.*/
        imageLibraryAPIInterface.getImageCollectionFromLibrary(keyword).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<LibraryImageCollection>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "LibraryImageCollection Observable onSubscribed()");
                disposableImageCollection = d;
                progressDialog.show(fragmentManager, PROGRESS_DIALOG);
            }

            @Override
            public void onNext(LibraryImageCollection libraryImageCollection) {
                Log.d(TAG, "LibraryImageCollection Observable onNext()");
                imageCollection = libraryImageCollection;
            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "LibraryImageCollection Observable onError()"+e.getMessage());
                progressDialog.cancel();
                showDialog("error",e.getMessage());

            }

            @Override
            public void onComplete() {
                Log.d(TAG, "LibraryImageCollection Observable onComplete()");
                progressDialog.cancel();
                if(!imageCollection.getCollection().getItems().isEmpty()) {
                    ImageListFragment imageListFragment = ImageListFragment.newInstance(imageCollection);
                    fragmentManager.beginTransaction().replace(R.id.image_list_container, imageListFragment, IMAGE_LIST_FRAGMENT).commit();
                }else{
                    showDialog("message","There aren't images available for this keyword. Please enter a different keyword.");
                }
            }
        });
    }

    private void showDialog(String type, String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);

        switch (type){
            case "error":
                alertDialogBuilder.setTitle("Error");
                alertDialogBuilder.setMessage(message);
                alertDialogBuilder.setPositiveButton("DISMISS", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                AlertDialog errorDialog = alertDialogBuilder.create();
                errorDialog.show();
                break;
            case "gps":
                alertDialogBuilder.setMessage(message);
                alertDialogBuilder.setPositiveButton("SETTINGS", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        /*Direct user to gps location setting.*/
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                });
                alertDialogBuilder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
                AlertDialog gpsDialog = alertDialogBuilder.create();
                gpsDialog.show();
                break;
            case "permission":
                alertDialogBuilder.setMessage(message);
                final AlertDialog permissionDialog = alertDialogBuilder.create();
                permissionDialog.show();

                // Hide after some seconds
                final Handler handler  = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (permissionDialog.isShowing()) {
                            permissionDialog.dismiss();
                        }
                    }
                },3000);
                break;
            case "message":
                alertDialogBuilder.setMessage(message);
                alertDialogBuilder.setPositiveButton("DISMISS", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                AlertDialog messageDialog = alertDialogBuilder.create();
                messageDialog.show();
                break;
        }
    }
}


