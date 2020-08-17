package com.example.fileencryptor;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.OkHttpClient;

public class ListVideoActivity extends AppCompatActivity {
    private File mFile;
    private File[] files;

    private PlayerView mPlayerView;
    private Button mEncryptButton;
    private ListView listView;
    private ArrayList<File> fileArrayList;
    private ArrayList<String> fileNames, filePaths;
    private SimpleExoPlayer player;
    private InputStream inputStream;
    private Boolean isYoutubeLink=false;
    private String KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_video);
        listView = findViewById(R.id.listView);
        mPlayerView = findViewById(R.id.player_view);
        KEY=getIntent().getStringExtra("key");

        fileArrayList = new ArrayList<>();
        fileNames = new ArrayList<>();
        filePaths = new ArrayList<>();
        File tfile=new File(getExternalFilesDir("").getAbsolutePath()+"/encrypted");
        files = tfile.listFiles();

        for (File file : files) {
            fileArrayList.add(file);
            filePaths.add(file.getAbsolutePath());
            fileNames.add(file.getName());
        }

        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (player != null)
                    player.release();
                mFile = new File(filePaths.get(i));
                initPlayer(mFile);

            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.release();
    }

    private void initPlayer(File file) {

        /*
        DataSource.Factory dataSourceFactory =
                new CryptedDefaultDataSourceFactory(
                        KEY,
                        this,
                        new OkHttpDataSourceFactory(new OkHttpClient(), Util.getUserAgent(this, "exoPlayerTest"))
                );

        MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.parse(file.getAbsolutePath()));

         */

        player=new SimpleExoPlayer.Builder(ListVideoActivity.this).build();
        MediaSource mediaSource=buildMediaSource(Uri.fromFile(file));
        mPlayerView.setPlayer(player);
        mPlayerView.setControllerShowTimeoutMs(0);
        mPlayerView.setControllerAutoShow(false);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }


    private MediaSource buildMediaSource(Uri uri) {
        DataSource.Factory dataSourceFactory = null;
        if (isYoutubeLink) {
            dataSourceFactory = new DefaultDataSourceFactory
                    (this, Util.getUserAgent(getApplicationContext(), "App"));
        } else {
            // file based media. Assume encryption enabled
            dataSourceFactory = new CryptedDefaultDataSourceFactory(
                    KEY,
                    this,
                    new DefaultBandwidthMeter.Builder(getApplicationContext()).build(),
                    new OkHttpDataSourceFactory(
                            new OkHttpClient(),
                            Util.getUserAgent(getApplicationContext(),
                                    "App")
                    ));
        }
        return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
    }
}


