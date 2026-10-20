package com.arenarush.troops;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ConsoleMessage;

public class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        web=new WebView(this);web.setBackgroundColor(0xff8ecff4);web.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(false);settings.setDefaultTextEncodingName("UTF-8");
        web.setWebChromeClient(new WebChromeClient(){ @Override public boolean onConsoleMessage(ConsoleMessage msg){android.util.Log.i("ARENA_JS",msg.message());return true;}});
        web.setWebViewClient(new WebViewClient());setContentView(web);web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onPause(){super.onPause();if(web!=null)web.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("location.reload();",null);else super.onBackPressed();}
}
