package com.shkmishra.lyrically;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class LyricsService extends Service {

static Element element;
    String title, lyrics;
    boolean offlineMusic = false;
    File[] lyricsFiles;

    String track = "", artist = "", artistU, trackU;
    TextView titleTV, lyricsTV;
    NestedScrollView scrollView;
    ImageView refresh;
    ProgressBar progressBar;
    int notifID = 26181317;
    ArrayList<Song> songArrayList = new ArrayList<>();

    SharedPreferences sharedPreferences;
    WindowManager.LayoutParams triggerParams, lyricsPanelParams;
    DisplayMetrics displayMetrics;
    View bottomLayout, trigger;
    LinearLayout container;
    Vibrator vibrator;
    private WindowManager windowManager;
    private BroadcastReceiver musicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            boolean isPlaying = extras.getBoolean(extras.containsKey("playstate") ? "playstate" : "playing", false);
            try {

                if (isPlaying && !((artist.equalsIgnoreCase(intent.getStringExtra("artist")) && (track.equalsIgnoreCase(intent.getStringExtra("track")))))) {

                    title = "";
                    lyrics = "";
                    progressBar.setVisibility(View.VISIBLE);
                    titleTV.setText("");
                    lyricsTV.setText("");
                    lyricsTV.setVisibility(View.INVISIBLE);
                    refresh.setVisibility(View.GONE);
                    artist = intent.getStringExtra("artist");
                    track = intent.getStringExtra("track");
                    for (Song song : songArrayList) {
                        if ((song.getArtist().equalsIgnoreCase(artist)) && (song.getTrack().equalsIgnoreCase(track))) {
                            lyrics = getLyrics(song);
                            if (!lyrics.equals("")) {
                                title = artist + " - " + track;
                                lyricsTV.setText(lyrics);
                                lyricsTV.setVisibility(View.VISIBLE);
                                scrollView.fullScroll(ScrollView.FOCUS_UP);
                                refresh.setVisibility(View.GONE);
                                titleTV.setText(title);
                                progressBar.setVisibility(View.GONE);
                            } else {
                                lyricsTV.setText("");
                                lyricsTV.setVisibility(View.INVISIBLE);
                                artistU = artist.replaceAll(" ", "+");
                                trackU = track.replaceAll(" ", "+");
                                new FetchLyrics().execute();
                                break;
                            }
                            offlineMusic = true;
                        }
                    }
                    if (!offlineMusic) {
                        artistU = artist.replaceAll(" ", "+");
                        trackU = track.replaceAll(" ", "+");
                        new FetchLyrics().execute();
                    }

                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        displayMetrics = new DisplayMetrics();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        int width = (sharedPreferences.getInt("triggerWidth", 10)) * 2;
        int height = (sharedPreferences.getInt("triggerHeight", 10)) * 2;

        getSongsList();
        String path = Environment.getExternalStorageDirectory() + File.separator + "Lyrically/";
        File directory = new File(path);
        lyricsFiles = directory.listFiles();

        triggerParams = new WindowManager.LayoutParams(
                width, height,

                WindowManager.LayoutParams.TYPE_PHONE,

                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

                ,
                PixelFormat.TRANSLUCENT);


        int panelHeight = (sharedPreferences.getInt("panelHeight", 60)) * displayMetrics.heightPixels / 100;

        lyricsPanelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                panelHeight,

                WindowManager.LayoutParams.TYPE_PHONE,

                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

                ,
                PixelFormat.TRANSLUCENT);


        lyricsPanelParams.gravity = Gravity.BOTTOM;
        lyricsPanelParams.x = 0;
        lyricsPanelParams.y = 0;


        int triggerPosition = Integer.parseInt(sharedPreferences.getString("triggerPos", "1"));
        double offset = (double) (sharedPreferences.getInt("triggerOffset", 10)) / 100;

        switch (triggerPosition) {
            case 1:
                triggerParams.gravity = Gravity.TOP | Gravity.START;
                break;
            case 2:
                triggerParams.gravity = Gravity.TOP | Gravity.END;
                break;
        }
        triggerParams.x = 0;
        triggerParams.y = (int) (displayMetrics.heightPixels - (displayMetrics.heightPixels * offset));


        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        trigger = new View(this);


        bottomLayout = layoutInflater.inflate(R.layout.lyrics_sheet, null);
        bottomLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        container = new LinearLayout(this);


        scrollView = (NestedScrollView) bottomLayout.findViewById(R.id.lyricsScrollView);
        titleTV = (TextView) bottomLayout.findViewById(R.id.title);
        lyricsTV = (TextView) bottomLayout.findViewById(R.id.lyrics);
        progressBar = (ProgressBar) bottomLayout.findViewById(R.id.progressbar);
        progressBar.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorAccent), PorterDuff.Mode.SRC_IN);
        refresh = (ImageView) bottomLayout.findViewById(R.id.refresh);


        bottomLayout.setOnTouchListener(new SwipeDismissTouchListener(bottomLayout, null, new SwipeDismissTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss(Object token) {
                return true;
            }

            @Override
            public void onDismiss(View view, Object token) {
                container.removeView(bottomLayout);
                windowManager.removeView(container);

            }
        }));


        final int swipeDirection = Integer.parseInt(sharedPreferences.getString("swipeDirection", "1"));
        trigger.setOnTouchListener(new OnSwipeTouchListener(this) {
                                       @Override
                                       public void onSwipeUp() {
                                           super.onSwipeUp();
                                           if (swipeDirection == 1) {
                                               vibrate();
                                               windowManager.addView(container, lyricsPanelParams);
                                               container.addView(bottomLayout);
                                               Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
                                               bottomLayout.startAnimation(animation);
                                           }
                                       }

                                       @Override
                                       public void onSwipeRight() {
                                           super.onSwipeRight();
                                           if (swipeDirection == 4) {
                                               vibrate();
                                               windowManager.addView(container, lyricsPanelParams);
                                               container.addView(bottomLayout);
                                               Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
                                               bottomLayout.startAnimation(animation);
                                           }
                                       }

                                       @Override
                                       public void onSwipeLeft() {
                                           super.onSwipeLeft();
                                           if (swipeDirection == 3) {
                                               vibrate();
                                               windowManager.addView(container, lyricsPanelParams);
                                               container.addView(bottomLayout);
                                               Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
                                               bottomLayout.startAnimation(animation);
                                           }
                                       }

                                       @Override
                                       public void onSwipeDown() {
                                           super.onSwipeDown();
                                           if (swipeDirection == 2) {
                                               vibrate();
                                               windowManager.addView(container, lyricsPanelParams);
                                               container.addView(bottomLayout);
                                               Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
                                               bottomLayout.startAnimation(animation);
                                           }
                                       }
                                   }
        );

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                try {
                    windowManager.addView(container, lyricsPanelParams);
                    container.addView(bottomLayout);
                    Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
                    bottomLayout.startAnimation(animation);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        };
        trigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowManager.removeView(trigger);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        windowManager.addView(trigger, triggerParams);
                    }
                }, 5000);
            }
        });


        windowManager.addView(trigger, triggerParams);


        NotificationManager mNotifyManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);

        Intent showLyrics = new Intent(this, ShowLyrics.class);
        showLyrics.putExtra("messenger", new Messenger(handler));
        PendingIntent pendingIntent = PendingIntent.getService(this, 1, showLyrics, PendingIntent.FLAG_UPDATE_CURRENT);


        mBuilder.setContentTitle("Lyrically")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(R.mipmap.ic_launcher);
        mNotifyManager.notify(
                notifID,
                mBuilder.build());


        IntentFilter iF = new IntentFilter();
        iF.addAction("com.spotify.music.metadatachanged");
        iF.addAction("com.spotify.music.playbackstatechanged");
        iF.addAction("com.android.music.metachanged");
        iF.addAction("com.android.music.playstatechanged");
        registerReceiver(musicReceiver, iF);


        return Service.START_STICKY;
    }

    private void vibrate() {
        boolean vibrate = sharedPreferences.getBoolean("triggerVibration", true);
        if (vibrate) vibrator.vibrate(125);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down);
            bottomLayout.startAnimation(animation);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    container.removeView(bottomLayout);
                    windowManager.removeView(container);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            windowManager.removeView(trigger);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        unregisterReceiver(musicReceiver);

    }


    private void getSongsList() {


        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION
        };
        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);
        while (cursor.moveToNext()) {
            String artist = cursor.getString(1);
            String title = cursor.getString(2);
            long songID = Long.parseLong(cursor.getString(0));
            long duration = Long.parseLong(cursor.getString(3));
            if ((duration / 1000) > 40) {
                songArrayList.add(new Song(title, artist, songID));
            }
        }
        cursor.close();


    }

    private String getLyrics(Song song) {

        StringBuilder stringBuilder = new StringBuilder();

        for (File file : lyricsFiles) {
            if (file.getName().equals(song.getId() + ".txt")) {

                try {
                    String line;
                    BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append("\n");
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return stringBuilder.toString();
            }
        }
        return "";

    }

    private void saveLyricsOffline(String lyrics) {
        for (Song song : songArrayList) {
            if ((song.getArtist().equalsIgnoreCase(artist)) && (song.getTrack().equalsIgnoreCase(track))) {
                File path = new File(Environment.getExternalStorageDirectory() + File.separator + "Lyrically/");
                File lyricsFile = new File(path, song.getId() + ".txt");
                try {
                    FileWriter fileWriter = new FileWriter(lyricsFile);
                    fileWriter.write(lyrics);
                    fileWriter.flush();
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    class FetchLyrics extends AsyncTask {

        boolean found = true;

        String url = "s", lyricURL = "s", page, temp=null;


        @Override
        protected Object doInBackground(Object[] params) {


             try {
            url = "https://www.google.com/search?q=" + URLEncoder.encode(artistU + " " + trackU + " lyrics", "UTF-8");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            url = "https://www.google.com/search?q=lyrics+" + artistU + "+" + trackU;
        }
        // Getting url and connecting
        try {
   
            Document document = Jsoup.connect(url).userAgent(Net.USER_AGENT).timeout(10000).get();
            Elements results = document.select("h3.r > a");
           
            results = results.select("a");
            //.substring(7, results.attr("href").indexOf("&"));
          

//Check the procider now
            for (Element element : results.subList(0, 4)) {
                
                lyricURL = element.attr("href");
                Log.d("thedatahrefurl", lyricURL);
                if (lyricURL.contains("azlyrics.com/lyrics")) {
                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();

                    String page = document.toString();

                    page = page.substring(page.indexOf("that. -->") + 9);
                    page = page.substring(0, page.indexOf("</div>"));
                    temp = page;
                    
                    break;

                } else if (lyricURL.contains("genius")) {

                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();
                    track = document.select("meta[property=og:title]").first().attr("content");

                    Elements selector = document.select("div.h2");

                    for (Element e : selector) {
                        e.remove();
                    }

                    element = document.select("div[class=song_body-lyrics]").first();
                    temp = element.toString().substring(0, element.toString().indexOf("</lyrics>"));
               
                    break;
                } else if (lyricURL.contains("lyrics.wikia")) {

                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();
                    track = document.select("meta[property=og:title]").first().attr("content");
                    //track = track.replace(":", " - ");

                    element = document.select("div[class=lyricbox]").first();
                    temp = element.toString();
                   
                    break;
                } else if (lyricURL.contains("glamsham.com")) {

                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();
                    track = document.select("meta[property=og:title]").first().attr("content");
                    //track = track.replace(":", " - ");

                    Element e = document.select("div[class=col-sm-6]").first();
                    temp = e.select("font").text();
                   
                    break;

                } else if (lyricURL.contains("lyricsmint.com")) {
                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();
                    track = document.select("meta[property=og:title]").first().attr("content");
                    //track = track.replace(":", " - ");

                    Element e = document.select("div#lyric").first();
                    temp = e.text();
               
                    break;
                } else if (lyricURL.contains(("lyricskatta.com"))) {
                    String blank = "";
                    document = Jsoup.connect(lyricURL).userAgent(Net.USER_AGENT).get();
                    //track
                    Element e = document.select("div[class=entry-inner]").first();
                    Elements e2 = e.getElementsByTag("p");
                    for (Element e3 : e2) {
                        blank = blank + "\n" + e3.text();

                    }
                    temp = blank;
                   
                    break;

                }
                
                ///ADD more search Providers in the else statements
            }
               


            if (temp == null)
                return new Lyrics(Lyrics.NO_RESULT);

            temp = Utilities.br2nl(temp);

            temp = temp.replaceAll("(?i)<br[^>]*>", "br2n");
            temp = temp.replaceAll("]", "]shk");
            temp = temp.replaceAll("\\[", "shk[");
            temp = temp.replaceAll("br2n", "\n");


            lyrics = Jsoup.parse(temp).text();
            lyrics = lyrics.replaceAll("br2n", "\n");
            lyrics = lyrics.replaceAll("]shk", "]\n");
            lyrics = lyrics.replaceAll("shk\\[", "\n [");

          

           
            return lyrics;
        } catch (Exception e) {
         
            e.printStackTrace();
            return null
        }

    }


}


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected void onPostExecute(Object o) {
            if (!found || !(lyrics.length() > 0)) {
                lyricsTV.setText("");
                lyricsTV.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.GONE);
                titleTV.setText(getResources().getString(R.string.noLyricsFound));
                refresh.setVisibility(View.VISIBLE);
                refresh.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        new FetchLyrics().execute();
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
                return;
            }


            refresh.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            scrollView.fullScroll(ScrollView.FOCUS_UP);
            titleTV.setText(title);
            lyricsTV.setText(lyrics);
            if (lyricsTV.getVisibility() != View.VISIBLE)
                lyricsTV.setVisibility(View.VISIBLE);

            saveLyricsOffline(lyrics);

            super.onPostExecute(o);
        }
    }


}
