package cn.beisong.planet;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PHOTO=101, CAMERA=102, EXPORT=103, AUDIO=104;
    private WebView web;
    private SpeechRecognizer speech;
    private TextToSpeech tts;
    private TextRecognizer ocr;
    private Uri cameraUri;
    private String exportContent="";
    private boolean ttsReady=false, recognizing=false, ocrBusy=false, destroyed=false;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null){String uri=state.getString("cameraUri");if(uri!=null)cameraUri=Uri.parse(uri);exportContent=state.getString("exportContent","");}
        web=new WebView(this);
        web.setBackgroundColor(0xfff3f6fc);
        setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if("https".equals(request.getUrl().getScheme())&&"appassets.androidplatform.net".equals(request.getUrl().getHost())){
                    WebResourceResponse response=loader.shouldInterceptRequest(request.getUrl());
                    if(response!=null)return response;
                }
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
        });
        web.addJavascriptInterface(new Bridge(),"Native");
        ocr=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS&&tts!=null){
                int result=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                ttsReady=result!=TextToSpeech.LANG_MISSING_DATA&&result!=TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){js("nativeTtsEnd()");}
                    @Override public void onError(String id){js("nativeTtsError()");}
                });
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    private void js(String code){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript(code,null);});}
    private void notice(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    private void ocrStatus(String message){js("nativeOcrStatus("+JSONObject.quote(message)+")");}
    private void cancelSpeech(){recognizing=false;if(speech!=null){speech.cancel();speech.destroy();speech=null;}}

    public class Bridge {
        @JavascriptInterface public void choosePhoto(){runOnUiThread(()->{
            if(ocrBusy){notice("正在识别，请稍等");return;}
            new AlertDialog.Builder(MainActivity.this).setTitle("导入课文").setItems(new String[]{"拍照","从相册选择"},(dialog,which)->{
                if(which==0)takePhoto();else{
                    Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
                    try{startActivityForResult(intent,PHOTO);}catch(Exception e){notice("没有可用的图片选择器");}
                }
            }).setNegativeButton("取消",null).show();
        });}
        @JavascriptInterface public void startSpeech(){runOnUiThread(()->{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);else beginSpeech();
        });}
        @JavascriptInterface public void stopSpeech(){runOnUiThread(()->cancelSpeech());}
        @JavascriptInterface public void speak(String text,double rate){runOnUiThread(()->{
            if(!ttsReady){js("nativeTtsError()");return;}
            tts.setSpeechRate((float)Math.max(.5,Math.min(1.5,rate)));
            // System TTS has an input length limit; never silently truncate a lesson.
            if(text.length()>TextToSpeech.getMaxSpeechInputLength()){js("nativeTtsError()");notice("全文过长，请分段朗读");return;}
            if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"lesson")==TextToSpeech.ERROR)js("nativeTtsError()");
        });}
        @JavascriptInterface public void stopTts(){runOnUiThread(()->{if(tts!=null)tts.stop();});}
        @JavascriptInterface public void exportText(String filename,String text){runOnUiThread(()->{
            exportContent=text;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,filename.replaceAll("[/\\\\]","_"));
            try{startActivityForResult(intent,EXPORT);}catch(Exception e){notice("无法打开文件保存窗口");}
        });}
    }
    private void takePhoto(){
        try{
            File dir=new File(getCacheDir(),"camera");dir.mkdirs();
            File photo=File.createTempFile("lesson-",".jpg",dir);
            cameraUri=FileProvider.getUriForFile(this,getPackageName()+".files",photo);
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("photo",cameraUri));
            startActivityForResult(intent,CAMERA);
        }catch(Exception e){notice("无法打开相机，请尝试从相册选择");}
    }
    private void beginSpeech(){
        cancelSpeech();
        if(!SpeechRecognizer.isRecognitionAvailable(this)){js("nativeSpeechError('service-unavailable')");return;}
        speech=SpeechRecognizer.createSpeechRecognizer(this);
        recognizing=true;
        speech.setRecognitionListener(new RecognitionListener(){
            @Override public void onReadyForSpeech(Bundle b){}
            @Override public void onBeginningOfSpeech(){}
            @Override public void onRmsChanged(float v){}
            @Override public void onBufferReceived(byte[] b){}
            @Override public void onEndOfSpeech(){}
            @Override public void onEvent(int t,Bundle b){}
            @Override public void onError(int error){if(!recognizing)return;recognizing=false;js("nativeSpeechError("+JSONObject.quote(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"not-allowed":"recognition-error")+")");}
            @Override public void onPartialResults(Bundle b){sendResult(b,false);}
            @Override public void onResults(Bundle b){sendResult(b,true);recognizing=false;}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        try{speech.startListening(intent);}catch(Exception e){cancelSpeech();js("nativeSpeechError('recognition-error')");}
    }
    private void sendResult(Bundle data,boolean isFinal){
        if(!recognizing)return;
        ArrayList<String> values=data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(values!=null&&!values.isEmpty())js("nativeSpeech("+JSONObject.quote(values.get(0))+","+isFinal+")");
        else if(isFinal)js("nativeSpeechEnd()");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==AUDIO){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)beginSpeech();else js("nativeSpeechError('not-allowed')");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK)return;
        if(request==PHOTO||request==CAMERA){
            Uri uri=request==CAMERA?cameraUri:data==null?null:data.getData();
            if(uri!=null)recognizeImage(uri);
        }else if(request==EXPORT&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();String content=exportContent;
            executor.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(content.getBytes(StandardCharsets.UTF_8));notice("文字已保存");}catch(Exception e){notice("保存失败，请重试");}});
        }
    }
    private void recognizeImage(Uri uri){
        ocrBusy=true;ocrStatus("正在识别照片中的文字…");
        executor.execute(()->{
            try{
                InputImage image=InputImage.fromFilePath(this,uri);
                ocr.process(image).addOnSuccessListener(result->{ocrBusy=false;js("nativeOcrResult("+JSONObject.quote(result.getText())+")");}).addOnFailureListener(error->{ocrBusy=false;ocrStatus("识别失败，请使用清晰、正面的照片重试。");});
            }catch(Exception|OutOfMemoryError error){runOnUiThread(()->ocrBusy=false);ocrStatus("无法读取照片，请选择较小且清晰的图片。");}
        });
    }
    @Override protected void onSaveInstanceState(Bundle out){if(cameraUri!=null)out.putString("cameraUri",cameraUri.toString());out.putString("exportContent",exportContent);super.onSaveInstanceState(out);}
    @Override protected void onStop(){cancelSpeech();if(tts!=null)tts.stop();js("window.nativePause && nativePause()");super.onStop();}
    @Override protected void onDestroy(){destroyed=true;cancelSpeech();if(tts!=null)tts.shutdown();if(ocr!=null)ocr.close();executor.shutdown();if(web!=null){web.removeJavascriptInterface("Native");web.destroy();web=null;}super.onDestroy();}
}
