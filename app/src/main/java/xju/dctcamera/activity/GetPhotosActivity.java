package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.io.File;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;

/**
 * 单张图片浏览
 * Created by Belikovvv on 2017/5/3.
 */

public class GetPhotosActivity extends Activity implements MediaScannerConnectionClient {
    public File[] allFiles;
    private String SCAN_PATH ;
    private static final String FILE_TYPE="image/*";

    private MediaScannerConnection conn;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_get);
        AtyContainer.getInstance().addActivity(this);

        File folder = new File(Environment.getExternalStorageDirectory().getPath()+"/xju.digitalwatermark/DCTphoto");
        allFiles = folder.listFiles();
        //   uriAllFiles= new Uri[allFiles.length];
        for(int i=0;i<allFiles.length;i++)
        {
            Log.d("all file path "+ i, allFiles[i].getAbsolutePath());
        }
        //  Uri uri= Uri.fromFile(new File(Environment.getExternalStorageDirectory().toString()+"/yourfoldername/"+allFiles[0]));


        Button scanBtn = (Button)findViewById(R.id.get_photo_button);
        scanBtn.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
                startScan();
            }});
    }


    /**
     * 链接文件
     */
    private void startScan()
    {
        Log.d("Connected","success"+conn);
        if(conn!=null)
        {
            conn.disconnect();
        }
        conn = new MediaScannerConnection(this,this);
        conn.connect();
    }
    @Override
    public void onMediaScannerConnected() {
        Log.d("onMediaScannerConnected","success"+conn);
        conn.scanFile(allFiles[0].getAbsolutePath(), FILE_TYPE);
    }
    @Override
    public void onScanCompleted(String path, Uri uri) {
        try {
            Log.d("onScanCompleted",uri + "success"+conn);
            if (uri != null)
            {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(uri);
                startActivity(intent);
            }
        } finally
        {
            conn.disconnect();
            conn = null;
        }
    }
}
