package com.zjc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


public class MainActivity extends AppCompatActivity {

    private MediaPlayer mp=new MediaPlayer();

    private Button button1,button2;
    private TextView textView1,textView2,textView3;
    private Camera m_camera;
    private Switch m_switch;
    private Boolean isFirstStart = true;//判断是否为第一次启动
    private int requestCameraPermisson;

    private TextView batterLevel;
    private BroadcastReceiver batteryLevelRcvr;
    private IntentFilter batteryLevelFilter;


    public static String productKey;
    public static  String deviceName;
    public static String deviceSecret;
    public static String regionId;

    private static String pubTopic;
    private static String setTopic;

    private static final String payloadJson =
            "{"+
                    "\"id\": %s,"+
                    "\"params\": {"+
                    "\"LightSwitch\": %s,},"+
                    "\"method\": \"thing.event.property.post\"}";
    private static MqttClient mqttClient;

    Time time = new Time();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initView ——初始化控件
        initView();


        //第一次启动
        isFirstStart=true;
        //按钮2未连接=不可用
        button2.setEnabled(false);
        button2.setText("当前已与 IOT STUDIO 断开连接");
        //EventBus
        EventBus.getDefault().register(this);

        //确认权限
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            button1.setEnabled(false);
            button2.setEnabled(false);
            m_switch.setEnabled(false);
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},requestCameraPermisson);
        }

        //加载properties文件相关信息
        ResourceBundle resource=ResourceBundle.getBundle("assets/thing");
        productKey=resource.getString("productKey");
        deviceName=resource.getString("deviceName");
        deviceSecret=resource.getString("deviceSecret");
        regionId=resource.getString("regionId");
        pubTopic= "/sys/" + productKey + "/" + deviceName + "/thing/event/property/post";
        setTopic="/sys/" + productKey + "/" + deviceName + "/thing/service/property/set";
    }

    public void initView() {
        button1 = findViewById(R.id.bt1);
        button1.setOnClickListener(new myButton1());
        button2 = findViewById(R.id.bt2);
        button2.setOnClickListener(new myButton2());

        m_switch = findViewById(R.id.switch2);
        m_switch.setOnCheckedChangeListener(new mySwitch());

        textView2 = findViewById(R.id.tv10);
    }

    private void startAlarm() {
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.barcodebeep);
        mp.setLooping(false);
        try {
            mp.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mp.start();
        if (!m_switch.isChecked()){
            m_switch.setChecked(true);
        }
    }




    private class myButton1 implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            initAliyunIoTClient();
            getDeviceOrder();
            button1.setEnabled(false);
            button1.setText("已连接至IOT STUDIO");
            button2.setEnabled(true);
            button2.setText("断开与IOT STUDIO 的连接");
        }
    }

    private class myButton2 implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            try {
                if (mqttClient.isConnected()){
                    mqttClient.disconnect();
                    Toast.makeText(getApplicationContext(),"已断开连接",Toast.LENGTH_SHORT).show();
                    button1.setEnabled(true);
                    button1.setText("已连接至IOT STUDIO");
                    button2.setEnabled(false);
                    button2.setText("当前与IOT STUDIO 断开连接");

                    //断开连接，表示topic无法显示了
                    textView1.setText("消息内容将在此显示。");
                    textView2.setText("消息内容将在此显示。");
                }

            }catch (Exception e){
                e.printStackTrace();
                Toast.makeText(getApplicationContext(),"发生错误，断开连接失败。",Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class mySwitch implements CompoundButton.OnCheckedChangeListener {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            //防止初始化的时候触发监听
            if (!buttonView.isPressed()){
                return;
            }
            if (isChecked){
                return;
            }else {
                closeLight();
            }
        }
    }

    //摄像头权限判定与按钮启用
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode==requestCameraPermisson){
            if (grantResults[0]== PackageManager.PERMISSION_GRANTED){
                button1.setEnabled(true);
                m_switch.setEnabled(true);
            }else {
                Toast.makeText(MainActivity.this,"手机相机未授权，无法使用。",Toast.LENGTH_SHORT).show();
                button1.setEnabled(false);
                button2.setEnabled(false);
                m_switch.setEnabled(false);
            }
        }
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }

    //打开闪光灯
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void openLight(){
        try {
            //判断API是否大于24（安卓7.0系统对应的API）
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                CameraManager cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
                assert cameraManager != null;
                String[] ids = cameraManager.getCameraIdList();
                for (String id:ids){
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (flashAvailable !=null&&flashAvailable && lensFacing!= null &&lensFacing==CameraCharacteristics.LENS_FACING_BACK){
                        cameraManager.setTorchMode(id,true);
                    }
                }
            }else {
                m_camera = Camera.open();
                Camera.Parameters parameters;
                parameters = m_camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                m_camera.setParameters(parameters);
            }
            if (mqttClient.isConnected()){
                String payload = postDeviceProperties("1");
                time.setToNow();
                textView1.setText(time.format("%Y-%m-%d %H:%M:%S")+"\n"+payload);
                Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT).show();
            }
            if (!m_switch.isChecked()){
                m_switch.setChecked(true);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private Uri getSystemDefultRingtoneUri() {
        return RingtoneManager.getActualDefaultRingtoneUri(this,
                RingtoneManager.TYPE_RINGTONE);
    }
    //关闭闪光灯
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void  closeLight(){
        try {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                //获取CameraManager
                CameraManager cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
                //获取当前手机所有摄像头设备ID
                assert cameraManager != null;
                String[] ids = cameraManager.getCameraIdList();
                for (String id:ids){
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (flashAvailable !=null&&flashAvailable &&
                            lensFacing !=null &&lensFacing==CameraCharacteristics.LENS_FACING_BACK){
                        cameraManager.setTorchMode(id,false);
                    }
                }
            }else {
                //小于安卓7.0使用camera方法
                //使用setFlashMode中闪光灯关闭的方法来关闭手电筒
                Camera.Parameters parameters = m_camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                m_camera.setParameters(parameters);
                m_camera.release();
            }
            if (mqttClient.isConnected()){
                //设定要发送的闪光灯状态，从而实现云端显示闪光灯的状态
                String payload=postDeviceProperties("0");
                time.setToNow();
                textView1.setText(time.format("%Y-%m-%d %H:%M:%S")+"\n"+payload);
                //用户提示以及按钮属性设置
                Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT).show();
            }
            if (!m_switch.isChecked()){
                m_switch.setChecked(false);
            }

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    //初始化连接的配置
    private static void initAliyunIoTClient() {
        try {
            //连接所需要的信息：服务器地址，客户端id，用户名，密码
            String clientId = "java" + System.currentTimeMillis();
            Map<String, String> params = new HashMap<>(16);
            params.put("productKey", productKey);
            params.put("deviceName", deviceName);
            params.put("clientId", clientId);
            String timestamp = String.valueOf(System.currentTimeMillis());
            params.put("timestamp", timestamp);
            // 这里阿里云的服务器地区为cn-shanghai
            String targetServer = "tcp://" + productKey + ".iot-as-mqtt."+regionId+".aliyuncs.com:1883";
            String mqttclientId = clientId + "|securemode=3,signmethod=hmacsha1,timestamp=" + timestamp + "|";
            String mqttUsername = deviceName + "&" + productKey;
            String mqttPassword = sign(params, deviceSecret, "hmacsha1");//获得密码
            connectMqtt(targetServer, mqttclientId, mqttUsername, mqttPassword);
        } catch (Exception e) {
            System.out.println("initAliyunIoTClient error " + e.getMessage());
        }
    }

    //连接MQTT
    public  static void connectMqtt(String url,String clientId,String mqttUsername,String mqttPassword) throws Exception {
        MemoryPersistence persistence = new MemoryPersistence();
        mqttClient = new MqttClient(url,clientId,persistence);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setMqttVersion(4);
        connectOptions.setCleanSession(false);
        connectOptions.setAutomaticReconnect(false);
        connectOptions.setUserName(mqttUsername);
        connectOptions.setPassword(mqttPassword.toCharArray());
        connectOptions.setKeepAliveInterval(60);
        mqttClient.connect(connectOptions);
    }

//    //连接MQTT
//    public static void connectMqtt(String url, String clientId, String mqttUsername, String mqttPassword) throws Exception {
//        MemoryPersistence persistence = new MemoryPersistence();
//        mqttClient = new MqttClient(url, clientId, persistence);
//        //配置mqtt协议的版本、消息存储方式、断线重连功能以及其他的一些用户信息及可选设置
//        MqttConnectOptions connOpts = new MqttConnectOptions();
//        // MQTT 3.1.1对应MqttVersion(4)
//        connOpts.setMqttVersion(4);
//        connOpts.setAutomaticReconnect(false);
//        connOpts.setCleanSession(false);
//        //用户信息
//        connOpts.setUserName(mqttUsername);
//        connOpts.setPassword(mqttPassword.toCharArray());
//        connOpts.setKeepAliveInterval(60);
//        //开始连接
//        mqttClient.connect(connOpts);
//    }


    public static String sign(Map<String,String>params,String deviceSecret,String signMethod){
        String[] sortedKeys = params.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);
        StringBuilder canonicalizedQueryString = new StringBuilder();
        for (String key: sortedKeys){
            if ("sign".equalsIgnoreCase(key)){
                continue;
            }
            canonicalizedQueryString.append(key).append(params.get(key));
        }
        try {
            String key = deviceSecret;
            return encryptHMAC(signMethod,canonicalizedQueryString.toString(),key);
        }catch (Exception e){
            throw  new RuntimeException(e);
        }
    }

    public static String encryptHMAC(String signMethod,String content,String key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key.getBytes("utf-8"),signMethod);
        Mac mac = Mac.getInstance(secretKey.getAlgorithm());
        mac.init(secretKey);
        byte[] data = mac.doFinal(content.getBytes("utf-8"));
        return bytesToHexString(data);
    }

    //转16进制
    public static final String bytesToHexString(byte[] bArray){
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i=0;i<bArray.length;i++){
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length()<2){
                sb.append(0);
            }
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    //设备属性上报
    private static  String postDeviceProperties(String status){
        String payload= null ;
        try {
            payload = String.format(payloadJson,System.currentTimeMillis(),status);
            System.out.println("post:"+payload);
            MqttMessage message = new MqttMessage(payload.getBytes("utf-8"));
            message.setQos(1);
            /*
             * 注：其中发送时需要使用一个参数QOS，其全称为quality of service，
             * QOS=0的时候，表示至多一次的传输，这样会存在数据丢失的风险；
             * QOS=1的时候会保证消息至少到达一次，所以会有消息重复的风险；
             * 而QOS=2的时候会保证消息恰好到达一次，但比较费时。
             */
            mqttClient.publish(pubTopic,message);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return payload;
        }
    }

    //订阅消息
    private static void getDeviceOrder(){
        try{
            // 设置回调
            mqttClient.setCallback(new MqttCallback() {
                //失败的情况
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("LOG::连接丢失");
                    System.out.println("LOG::Reason="+cause);
                }
                //成功的情况
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String str=new String(message.getPayload());
                    System.out.println("LOG::收到信息="+str);
                    EventBus.getDefault().post(new MessageEvent(str));
                }
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("LOG::delivery complete");
                }
            });
            MqttTopic topic = mqttClient.getTopic(setTopic);
            int[] Qos = {1};
            String[] topic1 = {setTopic};
            mqttClient.subscribe(topic1,Qos);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //处理消息
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void handleMessage(MessageEvent messageEvent) {
        System.out.println("LOG::"+messageEvent.getMessage());
        time.setToNow();
        textView2.setText(time.format("%Y-%m-%d %H:%M:%S")+"\n"+messageEvent.getMessage());
        String jsonData=messageEvent.getMessage();
        try {
            JSONObject jsonObject=new JSONObject(jsonData);
            Object deviceOrder=jsonObject.getJSONObject("params").get("LightSwitch");
            //ord是云平台下发的设备状态 1=打开，0=关闭
            int ord=Integer.parseInt(deviceOrder.toString());
            if(ord==1){
                startAlarm();
            }
            else if(ord==0){
                closeLight();
            }
            else {
                System.out.println("LOG::"+ord);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isFirstStart){
            try {
                if (mqttClient!= null){
                    if (mqttClient.isConnected()){
                        button1.setEnabled(true);
                        button2.setEnabled(true);
                        Toast.makeText(getApplicationContext(),"仍在连接状态中，请继续操作。",Toast.LENGTH_SHORT).show();
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            isFirstStart =false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Toast.makeText(getApplicationContext(),"程序仍在后台运行",Toast.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        closeLight();
        try {
            if (mqttClient.isConnected()){
                mqttClient.close();
            }
            m_camera.release();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}