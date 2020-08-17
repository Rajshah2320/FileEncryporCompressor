package com.example.fileencryptor;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.google.android.exoplayer2.util.Util.toByteArray;

public class MainActivity extends AppCompatActivity {

    private Button encryptBtn, confirmBtn, deleteBtn, compressBtn, playBtn;
    private String key = "";
    private String quality = "MEDIUM";
    private Spinner spinner;
    private String width="640";
    private BroadcastReceiver mMessageReceiver;
    private TextView progressTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        encryptBtn = findViewById(R.id.encrypt_btn);
        deleteBtn = findViewById(R.id.delete_btn);
        confirmBtn = findViewById(R.id.confirm_btn);
         compressBtn = findViewById(R.id.compress_btn);
        playBtn = findViewById(R.id.play_btn);
        spinner=findViewById(R.id.width_types);
        progressTv=findViewById(R.id.progrssTv);

        ArrayAdapter<CharSequence> arrayAdapter=ArrayAdapter.createFromResource(this,R.array.width_values,android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
       spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
           @Override
           public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
               width=adapterView.getItemAtPosition(i).toString();
           }

           @Override
           public void onNothingSelected(AdapterView<?> adapterView) {
               width="640";
           }
       });

        try {
            key = loadKey();
        } catch (IOException e) {
            e.printStackTrace();
        }


        encryptBtn.setOnClickListener(view -> {

            String encDir = getExternalFilesDir("").getAbsolutePath() + "/encrypted";
            File file1 = new File(encDir);
            if (!file1.exists()) {
                file1.mkdir();
            }

            File file = new File(getExternalFilesDir("").getAbsolutePath() + "/compressed");
            if (file.isDirectory()) {
                for (File tfile : file.listFiles()) {

                    encryptFile(tfile, key);

                }
            }
        });

        playBtn.setOnClickListener(view -> {
            Intent intent=new Intent(MainActivity.this,ListVideoActivity.class);
            intent.putExtra("key",key);
            startActivity(intent);
        });

        deleteBtn.setOnClickListener(view -> {
            deleteBtn.setVisibility(View.INVISIBLE);
            confirmBtn.setVisibility(View.VISIBLE);

        });

        confirmBtn.setOnClickListener(view -> {
            confirmBtn.setVisibility(View.INVISIBLE);
            deleteBtn.setVisibility(View.VISIBLE);
            File file = new File(getExternalFilesDir("").getAbsolutePath() + "/normal");
            if (file.exists()) {
                for (File file1 : file.listFiles()) {
                    file1.delete();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                }
            }
        });

        compressBtn.setOnClickListener(view -> {

            String encDir = getExternalFilesDir("").getAbsolutePath() + "/compressed";
            File file1 = new File(encDir);
            if (!file1.exists()) {
                file1.mkdir();
            }

            File file = new File(getExternalFilesDir("").getAbsolutePath() + "/normal");

            for(File tfile:file.listFiles()) {
                Intent intent = new Intent(MainActivity.this, CompressService.class);
                intent.putExtra("inputfile", tfile.getAbsolutePath());
                intent.putExtra("outputfile", getExternalFilesDir("").getAbsolutePath() + "/compressed/" + tfile.getName());
                intent.putExtra("width", width);
                //1920 1600 1440 1400 1280 1024 960 800 640 576 480 320 160
                startService(intent);
            }
      });

         mMessageReceiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String message = intent.getStringExtra("message");
                Log.d("receiver", "Got message: " + message);
               //Toast.makeText(MainActivity.this,message,Toast.LENGTH_SHORT).show();
                progressTv.setText("Progress : "+message);
                if(message.equals("100")){
                    Toast.makeText(MainActivity.this,"Compressed successfully",Toast.LENGTH_SHORT).show();
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("progress"));
    }


    public String loadKey() throws IOException {

        String s = "";
        final File file = new File(getExternalFilesDir("").getAbsolutePath() + "/file.key");
        if (file.exists()) {
            s = readFile(file);
            Log.i("KEY length", "loadKey: " + s.length());
        }
        return s;
    }

    private String readFile(File file) {
        String myData = "";

        try {
            FileInputStream fis = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fis);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String strLine;
            while ((strLine = br.readLine()) != null) {
                myData = myData + strLine + "\n";
            }
            br.close();
            in.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("File ", "readFile: " + myData);
        return myData.trim();
    }

    private void encryptFile(File file, String key) {
        try {
            // Fully read the input stream.
            FileInputStream inputStream = new FileInputStream(file);

            String outPath = getExternalFilesDir("").getAbsolutePath() + "/encrypted/" + file.getName();
            File mFile = new File(outPath);


            byte[] inputStreamBytes = toByteArray(inputStream);
            inputStream.close();

            byte[] finalkey = Hex.decodeHex(key.toCharArray());


            // Create a sink that will encrypt and write to file.
            AesCipherDataSink encryptingDataSink = new AesCipherDataSink(
                    // Util.getUtf8Bytes(secretKey),
                    finalkey,
                    new DataSink() {
                        private FileOutputStream fileOutputStream;

                        @Override
                        public void open(DataSpec dataSpec) throws IOException {
                            fileOutputStream = new FileOutputStream(mFile);
                        }

                        @Override
                        public void write(byte[] buffer, int offset, int length) throws IOException {
                            fileOutputStream.write(buffer, offset, length);
                        }

                        @Override
                        public void close() throws IOException {
                            fileOutputStream.close();
                        }
                    });

            // Push the data through the sink, and close everything.
            encryptingDataSink.open(new DataSpec(Uri.fromFile(mFile)));
            encryptingDataSink.write(inputStreamBytes, 0, inputStreamBytes.length);
            encryptingDataSink.close();

            Toast.makeText(this, "File encrypted", Toast.LENGTH_SHORT).show();
        } catch (IOException | DecoderException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }
}
