
package com.example.fileencryptor;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.yyl.ffmpeg.FFmpegCallBack;
import com.yyl.ffmpeg.FFmpegUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompressService extends Service {

    private String TAG = CompressService.class.getName();

    String dlPath;
    String inputPath;
    String outputPath;
    String width;
    ProgressDialog progressDialog;

    static int i;
    ArrayList<Integer> startIds = new ArrayList<>();
    boolean bFileDelete;
    FFmpegUtils fFmpegUtils = null;

   public CompressService(){}

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public int onStartCommand(Intent intent, int flags, int startID) {

        resetBasePath();
        Log.i(TAG, "onStartCommand");

        //get i/o paths
        inputPath = intent.getStringExtra("inputfile");
        outputPath = intent.getStringExtra("outputfile");
        width = intent.getStringExtra("width");
        startIds.add(startID);
        //  bFileDelete = intent.getBooleanExtra(Constants.DEL_AFTER_COMPRESS,false);
        bFileDelete = false;

        //ffmpegs
        fFmpegUtils = FFmpegUtils.getInstance();
        fFmpegUtils.isShowLogcat(false);
        fFmpegUtils.setDebugMode(false);

        //String cmd="ffmpeg -y -i "+inputPath+" -s 640x480 -vcodec libx264 -acodec copy -b:v 350k " + outputPath;
        new BgThreadHelper(inputPath, outputPath).execute();
        return START_STICKY;
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    public class BgThreadHelper extends AsyncTask<Void, Integer, Void> {

        String inputFile;
        String outputFile;
        long duration = 0;
        int PROGRESS_MAX = 100;
        int PROGRESS_CURRENT = 0;
        NotificationManager notificationManager;
        NotificationCompat.Builder builder;
        Handler handler;


        BgThreadHelper(String inputFile, String outputFile) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            handler=new Handler(Looper.getMainLooper());


            //notification stuffs
            // notificationManager = NotificationManagerCompat.from(CompressService.this);
            notificationManager = (NotificationManager) getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
            builder = new NotificationCompat.Builder(CompressService.this);

            builder.setContentTitle("Sharing File")
                    .setContentText("Processing")
                    .setSmallIcon(R.drawable.circle)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            // Issue the initial notification with zero progress
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            notificationManager.notify(0, builder.build());
        }

        @Override
        protected Void doInBackground(Void... strings) {
            //probe the input file for duration
            //ffprobe -v error -select_streams v:0 -show_entries stream=duration -of default=noprint_wrappers=1:nokey=1 input.mp4
            List<String> cmdList = new ArrayList<>();
            cmdList.add("ffprobe");
            cmdList.add("-v");
            cmdList.add("quiet");
            cmdList.add("-print_format");
            cmdList.add("json");
            cmdList.add("-show_format");
            cmdList.add("-i");
            //cmdList.add("-select_streams");
            //cmdList.add("v:0");
            //cmdList.add("-show_entries");
            //cmdList.add("-stream=duration");
            //cmdList.add("-of");
            //cmdList.add("-default=noprint_wrappers=1:nokey=1");
            cmdList.add(inputFile);

            //String cmd = "ffprobe -v quiet -print_format json -show_format -i " + inputFile;

            String cmd[] = cmdList.toArray(new String[cmdList.size()]);
            Log.i(TAG, "ffprobe with cmd: " + cmd);

            if (fFmpegUtils != null) {
                String json = fFmpegUtils.execffprobe(cmd);
                Log.i(TAG, "json: " + json);

                // this deviates from the steps mentioned in the chinese library
                // the json structured mentioned is different from what appears in dev bed phone
                if (!json.isEmpty()) {
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        JSONObject jsonDuration = (JSONObject) jsonObject.get("format");
                        String strDuration = jsonDuration.getString("duration");
                        float aFloat = Float.valueOf(strDuration);
                        duration = (long) (aFloat * 1000);
                        Log.i(TAG, "duration: " + duration);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.i(TAG, "json is null or empty");
                }
            }


            //probing done, now execute main command
            cmdList.clear();
            cmdList.add("ffmpeg");
            cmdList.add("-y");
            cmdList.add("-i");
            cmdList.add(inputFile);
            // cmdList.add("-s");
            cmdList.add("-vf");
            cmdList.add("scale=" + width + ":-1");
            //  cmdList.add(scale.trim());
            //cmdList.add("640*480");
            cmdList.add("-vcodec");
            cmdList.add("libx264");
            cmdList.add("-acodec");
            cmdList.add("copy");
            cmdList.add("-b:v");
            cmdList.add("350k");
            cmdList.add("-c:a");
            cmdList.add("aac");
            cmdList.add("-b:a");
            cmdList.add("128k");
            cmdList.add(outputFile);

            String ffcmd[] = cmdList.toArray(new String[cmdList.size()]);

            if (fFmpegUtils != null) {
                Log.i(TAG, "ffmpeg with cmd: " + ffcmd);
                fFmpegUtils.execffmpeg(ffcmd, new FFmpegCallBack() {
                    @Override
                    public void onStart() {
                        Log.i(TAG, "onStart");
                    }

                    @Override
                    public void onCallBackLog(String log) {
                        Log.i(TAG, "onCallBacklog: " + log);
                    }

                    @Override
                    public void onCallBackPrint(String msg) {
                        Log.i(TAG, "onCallBackPrint: " + msg);
                    }

                    @Override
                    public void onProgress(int frame_number, int milli_second) {
                        //Log.i(TAG,"onProgress: "+frame_number+", duration: "+duration);
                        if (duration > 0 && milli_second > 0) {

                            int progress = (int) ((double) milli_second / duration * 100d);
                            Log.i(TAG, "progress: " + progress);

                            Intent intent = new Intent("progress");
                            // You can also include some extra data.
                            intent.putExtra("message", String.valueOf(progress));
                            LocalBroadcastManager.getInstance(CompressService.this).sendBroadcast(intent);


                            publishProgress(progress);
                            //builder.setProgress(PROGRESS_MAX, progress, false);
                        }
                    }

                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "onSuccess");

                        // delete the input file for space, or not
                        if (bFileDelete) {
                            Log.i(TAG, "deleting file");
                            new File(inputFile).delete();
                        } else {
                            Log.i(TAG, "retaining file");
                        }
                    }

                    @Override
                    public void onFailure(int result) {
                        Log.i(TAG, "onFailure: " + result);
                    }
                });
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int value = values[0];
            builder.setProgress(PROGRESS_MAX, value, false);
            builder.setSubText(Integer.toString(value) + "%");
            notificationManager.notify(0, builder.build());
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            builder.setContentText("Processing complete.")
                    .setProgress(0, 0, false);
            notificationManager.notify(0, builder.build());
            notificationManager.cancel(0);
            stopFfmpeg();
        }
    }

    private boolean isSDCardAvailable() {
        File[] storages = this.getExternalFilesDirs(null);
        if (storages.length > 1 && storages[0] != null && storages[1] != null) {
            return true;
        } else {
            return false;
        }
    }

    private void resetBasePath() {
        if (isSDCardAvailable()) {
            File file[] = this.getExternalFilesDirs(null);
            dlPath = file[file.length - 1].getAbsolutePath();
        } else {
            File file = this.getExternalFilesDir(null);
            dlPath = file.getAbsolutePath();
        }
        Log.i(TAG, "dlPath: " + dlPath);
    }

    private void stopFfmpeg() {
        if (fFmpegUtils != null) {
            fFmpegUtils.exitffmpeg();
        }
        int id = startIds.get(i);
        i++;
        stopSelf(id);


        Log.i(TAG, "stopFfmpeg: service stopped " + id);
    }
}


