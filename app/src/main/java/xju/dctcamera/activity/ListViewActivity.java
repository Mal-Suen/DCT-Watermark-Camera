package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
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

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;

/**
 * 图片列表 Activity
 * <p>
 * 显示已拍摄图片的列表，支持查看水印、删除图片等操作。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/4
 */
public class ListViewActivity extends Activity {

    private static final String TAG = "ListViewActivity";

    /**
     * 旧版图片目录路径
     */
    private static final String LEGACY_PHOTO_PATH = "/xju.digitalwatermark/DCTphoto";

    /**
     * 列表视图
     */
    private ListView listView;

    /**
     * 返回按钮
     */
    private Button backButton;

    /**
     * 所有文件列表
     */
    private File[] allFiles;

    /**
     * 图片目录
     */
    private File photoFolder;

    /**
     * 图片路径映射表（位置 -> 路径）
     */
    private Map<Integer, String> pathMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listview_main);
        AtyContainer.getInstance().addActivity(this);

        initPhotoFolder();
        initViews();
        setupListView();
        setupBackButton();
    }

    /**
     * 初始化图片目录
     */
    private void initPhotoFolder() {
        photoFolder = new File(Environment.getExternalStorageDirectory().getPath() + LEGACY_PHOTO_PATH);
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        setTitle("BaseAdapter for ListView");
        listView = findViewById(R.id.MyListView);
        backButton = findViewById(R.id.back_button);
    }

    /**
     * 设置列表视图的点击事件
     */
    private void setupListView() {
        listView.setOnItemClickListener((parent, view, position, id) -> showOperationDialog(position));
    }

    /**
     * 设置返回按钮
     */
    private void setupBackButton() {
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ListViewActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }

    /**
     * 显示操作 对话框
     */
    private void showOperationDialog(int position) {
        if (pathMap == null || !pathMap.containsKey(position)) {
            return;
        }

        String filePath = pathMap.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.manage);
        builder.setTitle(R.string.warning);
        builder.setMessage("请选择操作");

        builder.setPositiveButton("取水印", (dialog, which) -> extractWatermark(filePath));
        builder.setNegativeButton("删除图片", (dialog, which) -> deleteImage(filePath));
        builder.setNeutralButton("放弃操作", null);

        builder.show();
    }

    /**
     * 提取水印
     */
    private void extractWatermark(String filePath) {
        Log.d(TAG, "Extracting watermark from: " + filePath);
        Intent intent = new Intent(this, PhotoDedctActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("Path", filePath);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * 删除图片
     */
    private void deleteImage(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    showToast("删除成功");
                    refreshListView();
                } else {
                    showToast("删除失败");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete file: " + filePath, e);
            showToast("删除失败");
        }
    }

    /**
     * 刷新列表
     */
    private void refreshListView() {
        pathMap = populateListView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pathMap = populateListView();
    }

    /**
     * 填充列表并返回路径映射
     *
     * @return 位置到文件路径的映射
     */
    private Map<Integer, String> populateListView() {
        allFiles = photoFolder.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            showToast("没有找到图片文件");
            return new HashMap<>();
        }

        List<Bitmap> bitmapList = new ArrayList<>();
        Map<Integer, String> pathMapping = new HashMap<>();

        for (int i = 0; i < allFiles.length; i++) {
            File file = allFiles[i];
            Log.d(TAG, "Loading: " + file.getAbsolutePath());

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                bitmapList.add(bitmap);
            }
            pathMapping.put(i, file.getAbsolutePath());
        }

        Bitmap[] bitmaps = bitmapList.toArray(new Bitmap[0]);
        String[] titles = new String[bitmaps.length];
        String[] texts = new String[bitmaps.length];

        listView.setAdapter(new ListViewAdapter(titles, texts, bitmaps));
        return pathMapping;
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * 列表适配器
     */
    public class ListViewAdapter extends BaseAdapter {

        private final View[] itemViews;

        /**
         * 构造函数
         *
         * @param itemTitles 标题数组
         * @param itemTexts  文本数组
         * @param bitmaps    图片数组
         */
        public ListViewAdapter(String[] itemTitles, String[] itemTexts, Bitmap[] bitmaps) {
            itemViews = new View[bitmaps.length];
            for (int i = 0; i < itemViews.length; i++) {
                itemViews[i] = createItemView(itemTitles[i], itemTexts[i], bitmaps[i]);
            }
        }

        @Override
        public int getCount() {
            return itemViews.length;
        }

        @Override
        public View getItem(int position) {
            return itemViews[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * 创建单个列表项视图
         */
        private View createItemView(String title, String text, Bitmap bitmap) {
            LayoutInflater inflater = (LayoutInflater) ListViewActivity.this
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View itemView = inflater.inflate(R.layout.listview_item, null);

            TextView titleView = itemView.findViewById(R.id.itemTitle);
            titleView.setText(title);

            TextView textView = itemView.findViewById(R.id.itemText);
            textView.setText(text);

            ImageView imageView = itemView.findViewById(R.id.itemImage);
            imageView.setImageBitmap(bitmap);

            return itemView;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                return itemViews[position];
            }
            return convertView;
        }
    }
}
