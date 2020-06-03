package com.example.trading;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.IntentService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public class LocalService extends IntentService {

    // 메인 액티비티에서 서비스를 생성하면, 소켓을 열어주는 역할을 할 것이다.
    // 소켓을 연 다음엔 서버에서 보내는 데이터를 리시브하는 메소드를 쓰레드로 만들 것.

    // 채팅방 액티비티나, 채팅 리스트 프래그먼트와 서비스를 바인드 할 것이다.

    String TAG = "LocalService";

    // IntentService를 상속하면서 선언해줘야하는 생성자.. 이로써 클라이언트에서 어떤 인텐트에 대한 요청인지 알 수 있다고 한다.
    public LocalService(String name) {
        super(name);
    }

    Socket socket;

    //     클라이언트에게 주어질 바인더
    IBinder binder = new LocalBinder();

    boolean mBound = false;

    //클라이언트 바인더를 위해서 쓰이는 클래스.
    public class LocalBinder extends Binder {
        public LocalService getService(){
            Log.i(TAG, "LocalBinder class");
            // Return this instance of LocalService so clients can call public methods
            return LocalService.this;
        }
    }

    // 현재 최상위 액티비티 뭔지 확인하기 위한 매니져
    ActivityManager activityManager = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);


    @Override
    public void onCreate() {
        super.onCreate();

    }

    // IntentService를 상속하면 구현해야하는 메소드.
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.i(TAG, "onHandleIntent");

        // 서비스 단에서 실행할 코드들을 여기에 적으면 된다.
        // 먼저 TCP 소켓을 열어줄 것이다.

        Thread thread = new Thread(){
            @Override
            public void run() {

                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress("15.165.57.108", 5000));

                    Log.i(TAG, "연결완료 : "+socket.getRemoteSocketAddress());

                    // 소켓이 연결되면 바로 리시브 메소드를 실행시켜, 채팅서버에서 스트림으로 보내주는 데이터를 받는다.
                    receive();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void receive(){

        while (true){

            try{
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String data = bufferedReader.readLine();
                Log.d(TAG, "received data : "+data);

                // 데이터를 받으면.. 두가지 상황에 따라 처리하는 방법이 다르다.
                // 1. 화면이 채팅목록이나 채팅방에 있을 때 : 로컬 브로드캐스트를 사용해서 해당 액티비티에 데이터를 보내준다.
                // 2. 위의 경우가 아닐 때 : 알림을 만들어서 보내준다.

                // 최상위 액티비티를 확인! deprecated 됐지만 하위 호환을 위해서다. 사용하는데 문제는 없음.
                List<ActivityManager.RunningTaskInfo> infoList = activityManager.getRunningTasks(1);
                ActivityManager.RunningTaskInfo info = infoList.get(0);

                ComponentName componentName = info.topActivity;

                if(componentName.getClassName().contains(".Fragments.Chat")){
                    // 1번의 경우.. 로컬 브로드캐스트로 데이터 보내기

                }else{
                    // 2번의 경우.. 알림 띄우기

                }



            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "can't connect with SERVER");
                stopClient();
                break;
            }

        }
    }

    public void send(final String data){
        // 데이터를 보내는 메소드.. 값을 받아와서 쓰레드로 처리해줄 것이다.

        Thread thread = new Thread(){
            @Override
            public void run() {
                try {
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    bufferedWriter.write(data);
                    bufferedWriter.flush();
                    Log.d(TAG, "sended : "+data);

                } catch (IOException e) {
                    e.printStackTrace();
                    stopClient();
                }

            }
        };
        thread.start();
    }

    public void stopClient(){
        Log.i(TAG, "stopClient");
        // 소켓 연결을 끊는 메소드

        try {
            if(socket!=null && !socket.isClosed()){
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // 태스크가 종료되었을 때 콜밸되는 메소드.. 여기서 서비스의 종료를 선언해줄 수 있다.
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved");
        stopClient(); // 소켓 끊는 메소드
        stopSelf(); // 서비스 종료하는 메소드
        super.onTaskRemoved(rootIntent);
    }

    // 바인드 되었을 때 호출되는 함수
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        mBound = true;
        return binder;
    }

    // 언바인드 되었을 때 호출되는 함수
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        mBound = false;
        return super.onUnbind(intent);
    }
}