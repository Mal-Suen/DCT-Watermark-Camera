package xju.dctcamera.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import xju.dctcamera.AtyContainer;
import xju.dctcamera.R;
import xju.dctcamera.manager.FolderManager;

/**
 * 图片列表 Activity
 * <p>
 * 显示已拍摄图片的网格列表，支持查看水印、删除图片等操作。
 * </p>
 *
 * @author Belikovvv
 * @since 2017/5/4
 */
public class ListViewActivity extends AppCompatActivity {

    private static final String TAG = "ListViewActivity";

    private RecyclerView photoRecyclerView;
    private PhotoAdapter photoAdapter;
    private List<PhotoItem> photoList;
    private View emptyStateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listview_main);

        AtyContainer.getInstance().addActivity(this);

        initViews();
        setupRecyclerView();
        loadPhotos();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        photoRecyclerView = findViewById(R.id.photo_recycler_view);
        emptyStateView = findViewById(R.id.empty_state);

        findViewById(R.id.refresh_button).setOnClickListener(v -> loadPhotos());
        findViewById(R.id.back_button).setOnClickListener(v -> {
            Intent intent = new Intent(ListViewActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        photoList = new ArrayList<>();
        photoAdapter = new PhotoAdapter(photoList);
        photoRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        photoRecyclerView.setAdapter(photoAdapter);
    }

    /**
     * 加载照片列表
     */
    private void loadPhotos() {
        photoList.clear();

        // 使用 FolderManager 获取照片目录
        File photoFolder = FolderManager.getInstance().getPhotoDctFolder();
        if (photoFolder == null || !photoFolder.exists()) {
            showEmptyState();
            return;
        }

        File[] files = photoFolder.listFiles();
        if (files == null || files.length == 0) {
            showEmptyState();
            return;
        }

        // 按修改时间倒序排列
        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        for (File file : files) {
            if (file.isFile() && isImageFile(file.getName())) {
                PhotoItem item = new PhotoItem(
                        file.getAbsolutePath(),
                        file.getName(),
                        file.length(),
                        file.lastModified()
                );
                photoList.add(item);
            }
        }

        if (photoList.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
            photoAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String fileName) {
        String lowerCase = fileName.toLowerCase(Locale.getDefault());
        return lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg") ||
                lowerCase.endsWith(".png") || lowerCase.endsWith(".bmp");
    }

    private void showEmptyState() {
        emptyStateView.setVisibility(View.VISIBLE);
        photoRecyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyStateView.setVisibility(View.GONE);
        photoRecyclerView.setVisibility(View.VISIBLE);
    }

    /**
     * 照片数据类
     */
    private static class PhotoItem {
        final String filePath;
        final String fileName;
        final long fileSize;
        final long lastModified;

        PhotoItem(String filePath, String fileName, long fileSize, long lastModified) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
        }
    }

    /**
     * RecyclerView 适配器
     */
    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

        private final List<PhotoItem> photos;

        PhotoAdapter(List<PhotoItem> photos) {
            this.photos = photos;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            PhotoItem item = photos.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }

        /**
         * ViewHolder 类
         */
        class PhotoViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView cardView;
            private final ImageView thumbnail;
            private final TextView nameText;
            private final TextView sizeText;
            private final TextView dateText;
            private final ImageView moreIcon;

            PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                thumbnail = itemView.findViewById(R.id.photo_thumbnail);
                nameText = itemView.findViewById(R.id.photo_name);
                sizeText = itemView.findViewById(R.id.photo_size);
                dateText = itemView.findViewById(R.id.photo_date);
                moreIcon = itemView.findViewById(R.id.photo_more);
            }

            void bind(PhotoItem item) {
                // 设置文件名
                nameText.setText(item.fileName);

                // 设置文件大小
                sizeText.setText(formatFileSize(item.fileSize));

                // 设置修改时间
                dateText.setText(formatDate(item.lastModified));

                // 加载缩略图（异步加载避免卡顿）
                loadThumbnail(thumbnail, item.filePath);

                // 设置点击事件
                cardView.setOnClickListener(v -> {
                    Intent intent = new Intent(ListViewActivity.this, PhotoDedctActivity.class);
                    intent.putExtra("Path", item.filePath);
                    startActivity(intent);
                });

                // 设置更多操作按钮
                moreIcon.setOnClickListener(v -> showPhotoOptions(item));
            }

            /**
             * 加载缩略图
             */
            private void loadThumbnail(ImageView imageView, String filePath) {
                // 在后台线程加载图片以避免阻塞 UI
                new Thread(() -> {
                    try {
                        // 计算缩放比例
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(filePath, options);

                        options.inSampleSize = calculateInSampleSize(options, 200, 200);
                        options.inJustDecodeBounds = false;

                        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
                        if (bitmap != null) {
                            runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                        } else {
                            runOnUiThread(() -> imageView.setImageResource(android.R.drawable.ic_menu_gallery));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "加载缩略图失败: " + filePath, e);
                        runOnUiThread(() -> imageView.setImageResource(android.R.drawable.ic_menu_gallery));
                    }
                }).start();
            }

            /**
             * 计算图片采样率
             */
            private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
                final int height = options.outHeight;
                final int width = options.outWidth;
                int inSampleSize = 1;

                if (height > reqHeight || width > reqWidth) {
                    final int halfHeight = height / 2;
                    final int halfWidth = width / 2;

                    while ((halfHeight / inSampleSize) >= reqHeight &&
                            (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                return inSampleSize;
            }

            /**
             * 显示照片操作选项
             */
            private void showPhotoOptions(PhotoItem item) {
                String[] options = {"提取水印", "删除照片", "取消"};

                AlertDialog.Builder builder = new AlertDialog.Builder(ListViewActivity.this);
                builder.setTitle("操作选项")
                        .setItems(options, (dialog, which) -> {
                            switch (which) {
                                case 0: // 提取水印
                                    extractWatermark(item.filePath);
                                    break;
                                case 1: // 删除照片
                                    deletePhoto(item);
                                    break;
                                case 2: // 取消
                                    dialog.dismiss();
                                    break;
                            }
                        })
                        .show();
            }

            /**
             * 提取水印
             */
            private void extractWatermark(String filePath) {
                Intent intent = new Intent(ListViewActivity.this, PhotoDedctActivity.class);
                intent.putExtra("Path", filePath);
                startActivity(intent);
            }

            /**
             * 删除照片
             */
            private void deletePhoto(PhotoItem item) {
                new AlertDialog.Builder(ListViewActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除这张照片吗？此操作不可撤销。")
                        .setPositiveButton("删除", (dialog, which) -> {
                            File file = new File(item.filePath);
                            if (file.exists() && file.delete()) {
                                // 从列表中移除
                                int position = photos.indexOf(item);
                                if (position != -1) {
                                    photos.remove(position);
                                    notifyItemRemoved(position);
                                    Toast.makeText(ListViewActivity.this, "照片已删除", Toast.LENGTH_SHORT).show();

                                    // 检查是否为空
                                    if (photos.isEmpty()) {
                                        showEmptyState();
                                    }
                                }
                            } else {
                                Toast.makeText(ListViewActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 格式化日期
     */
    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPhotos(); // 每次返回时刷新列表
    }
}