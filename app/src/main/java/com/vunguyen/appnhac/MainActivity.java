package com.vunguyen.appnhac;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter;

public class MainActivity extends AppCompatActivity {
    //Members
    RecyclerView recyclerview;
    SongAdapter songAdapter;
    List<Song> allSongs = new ArrayList<>();
    ActivityResultLauncher<String> storagePermissionLauncher;
    final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

    //today
    ExoPlayer player;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //set the tool bar, and app title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getResources().getString(R.string.app_name));

        //recyclerview
        recyclerview = findViewById(R.id.recyclerview);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->{
            if(granted){
                //fetch songs
                fetchSongs();
            }
            else{
                userResponses();

            }
        });

        //launch
        storagePermissionLauncher.launch(permission);

        player = new  ExoPlayer.Builder(this).build();
    }

    private void userResponses() {
        if(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED){
            //fetch songs
            fetchSongs();
        }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(shouldShowRequestPermissionRationale(permission)){
                //show an educational Ui to user explaining why need this permission
                new AlertDialog.Builder(this)
                        .setTitle("Requesting permission")
                        .setMessage("Allow us to fetch songs on your device")
                        .setPositiveButton("allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //request permission
                                storagePermissionLauncher.launch(permission);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(getApplicationContext(),"You denied us to show songs",Toast.LENGTH_SHORT).show();
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
            }
        }
        else {
            Toast.makeText(this,"You canceld to show songs",Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchSongs() {
        //define a list to carry songs
        List<Song> songs = new ArrayList<>();
        Uri mediaStoreUri;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }else {
            mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        //define projection
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
        };

        //order
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        //get the songs
        try(Cursor cursor = getContentResolver().query(mediaStoreUri, projection, null, null, sortOrder)) {
            //cache cursor indices
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

            //clear the previous loaded before adding loading again
            while (cursor.moveToNext()){
                //get the values of a column for a given audio file
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                int duration = cursor.getInt(durationColumn);
                int size = cursor.getInt(sizeColumn);
                long albumId = cursor.getLong(albumIdColumn);

                //song uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                //album artwork uri
                Uri albumArtworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);

                //remove .mp3 extension from the song's name
                name = name.substring(0, name.lastIndexOf("."));

                //song item
                Song song = new Song(name, uri, albumArtworkUri, size, duration);

                //add song item to song list
                songs.add(song);
            }

            //display songs
            showSongs(songs);
        }
    }

    private void showSongs(List<Song> songs) {
        if(songs.size() == 0){
            Toast.makeText(this, "No Songs", Toast.LENGTH_SHORT).show();
            return;
        }

        //save songs
        allSongs.clear();
        allSongs.addAll(songs);

        //update the tool bar title
        String title = getResources().getString(R.string.app_name) + " - " + songs.size();
        Objects.requireNonNull(getSupportActionBar()).setTitle(title);

        //layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerview.setLayoutManager(layoutManager);

        //songs adapter
        songAdapter = new SongAdapter(this, songs, player);

        //set the adapter to recycler view
        //recyclerview.setAdapter(songAdapter);

        //recycler animators optional
        ScaleInAnimationAdapter scaleInAnimationAdapter = new ScaleInAnimationAdapter(songAdapter);
        scaleInAnimationAdapter.setDuration(1000);
        scaleInAnimationAdapter.setInterpolator(new OvershootInterpolator());
        scaleInAnimationAdapter.setFirstOnly(false);
        recyclerview.setAdapter(scaleInAnimationAdapter);
    }

    //setting the menu /search btn

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_btn,menu);

        //Search btn item
        MenuItem menuItem = menu.findItem(R.id.searchBtn);
        SearchView searchView = (SearchView) menuItem.getActionView();

        //search song method
        SearchSong(searchView);
        return super.onCreateOptionsMenu(menu);
    }

    private void SearchSong(SearchView searchView) {
        //search view listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                //filter songs
                filterSongs(newText.toLowerCase());
                return true;
            }
        });
    }

    private void filterSongs(String query) {
        List<Song> filteredList = new ArrayList<>();
        if(allSongs.size() > 0){
            for (Song song : allSongs){
                if(song.getTitle().toLowerCase().contains(query)){
                    filteredList.add(song);
                }
            }
            if(songAdapter != null){
                songAdapter.filterSongs(filteredList);
            }
        }
    }
}