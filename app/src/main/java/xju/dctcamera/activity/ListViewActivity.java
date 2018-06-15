package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;

/**
 *
 * 图片列表
 * Created by Belikovvv on 2017/5/4.
 */

public class ListViewActivity extends Activity {
    ListView listView;
    Button back_Button;
    public File[] allFiles;
    File folder = new File(Environment.getExternalStorageDirectory().getPath()+"/xju.digitalwatermark/DCTphoto");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listview_main);
        AtyContainer.getInstance().addActivity(this);

        this.setTitle("BaseAdapter for ListView");
        listView = (ListView) this.findViewById(R.id.MyListView);
        back_Button = (Button) findViewById(R.id.back_button);

        back_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ListViewActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        final Map<Integer,String> pathMap2=  showListView();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            /**
             *
             * @param parent
             * @param view
             * @param position
             * @param id
             */
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {

                final int positionf = position;

                //    通过AlertDialog.Builder这个类来实例化我们的一个AlertDialog的对象
                AlertDialog.Builder builder = new AlertDialog.Builder(ListViewActivity.this);
                //    设置Title的图标
                builder.setIcon(R.drawable.manage);
                //    设置Title的内容
                builder.setTitle(R.string.warning);
                //    设置Content来显示一个信息
                builder.setMessage("请选择操作");
                //    设置一个PositiveButton
                builder.setPositiveButton("取水印", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Log.d("listview " , pathMap2.get(positionf));
                        Intent intent = new Intent(ListViewActivity.this, PhotoDedctActivity.class);
                        //用Bundle携带数据
                        Bundle bundle = new Bundle();
                        //传递Path参数为图片路径
                        bundle.putString("Path", pathMap2.get(positionf));
                        intent.putExtras(bundle);
                        startActivity(intent);
                    }
                });
                //    设置一个NegativeButton
                builder.setNegativeButton("删除图片", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        try {
                            File file = new File(pathMap2.get(positionf));
                            file.delete();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ListViewActivity.this,"删除成功", Toast.LENGTH_LONG).show();
                                }
                            });
                            //刷新 页面
//                            Intent listView = new Intent(ListViewActivity.this, ListViewActivity.class);
//                            startActivity(listView);

                            showListView();

                        }catch (Exception e){
                            e.getMessage();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ListViewActivity.this,"删除失败", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
                //    设置一个NeutralButton
                builder.setNeutralButton("放弃操作", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ListViewActivity.this,"放弃操作", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
                //    显示出该对话框
                builder.show();
            }

        });


    }


    @Override
    public void onResume(){
        super.onResume();

    }
    @Override
    public void onPause(){
        super.onPause();
    }
    @Override
    public void onStop(){
        super.onStop();
    }

    public class ListViewAdapter extends BaseAdapter {
        View[] itemViews;

        /**
         * @param itemTitles
         * @param itemTexts
         * @param bitmaps
         */
        public ListViewAdapter(String[] itemTitles, String[] itemTexts,
                               Bitmap[] bitmaps) {
            itemViews = new View[bitmaps.length];
            /**
             * i++  ++ i 的区别注意！！！！！！！！！
             */
            for (int i = 0; i < itemViews.length; i++) {
                itemViews[i] = makeItemView(itemTitles[i], itemTexts[i],
                        bitmaps[i]);
                Log.d("itemViews", " | "+ i);
            }
        }


        public int getCount() {
            return itemViews.length;
        }

        public View getItem(int position) {
            return itemViews[position];
        }

        public long getItemId(int position) {
            return position;
        }

        private View makeItemView(String strTitle, String strText, Bitmap bitmap) {
            LayoutInflater inflater = (LayoutInflater) ListViewActivity.this
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            // 使用View的对象itemView与R.layout.item关联
            View itemView = inflater.inflate(R.layout.listview_item, null);

            // 通过findViewById()方法实例R.layout.item内各组件
            TextView title = (TextView) itemView.findViewById(R.id.itemTitle);
            title.setText(strTitle);
            TextView text = (TextView) itemView.findViewById(R.id.itemText);
            text.setText(strText);
            ImageView image = (ImageView) itemView.findViewById(R.id.itemImage);
            image.setImageBitmap(bitmap);
            return itemView;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                return itemViews[position];
            return convertView;
        }
    }

    /**
     * 封装图片列表方法
     */
    public Map<Integer,String> showListView(){
        allFiles = folder.listFiles();

        List<Bitmap> mybitmapList = new ArrayList<Bitmap>();

        for (File file : allFiles) {
            mybitmapList.add(BitmapFactory.decodeFile(file.getAbsolutePath()));

            Log.d("listview", file.getAbsolutePath());
        }
        Bitmap[] bitmaps = new Bitmap[mybitmapList.size()];
        String[] titles = new String[mybitmapList.size()];
        String[] texts = new String[mybitmapList.size()];

        Map<Integer,String> pathMap= new HashMap<>();

        for (int i = 0; i < mybitmapList.size(); i++) {
            bitmaps[i] = mybitmapList.get(i);
            titles[i] = "";
            texts[i] = "";
            pathMap.put(i,allFiles[i].getAbsolutePath());
        }

        listView.setAdapter(new ListViewAdapter(titles, texts, bitmaps));
        return pathMap;
    }
}
