package com.zxzx74147.mediacoredemo.test;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.view.View;
import android.webkit.MimeTypeMap;

import com.afollestad.materialdialogs.MaterialDialog;
import com.zxzx74147.mediacore.components.util.FileSelectUtil;
import com.zxzx74147.mediacore.components.util.FileUtil;
import com.zxzx74147.mediacore.components.video.filter.FilterConfig;
import com.zxzx74147.mediacore.components.video.filter.base.gpuimage.GPUImage;
import com.zxzx74147.mediacore.components.video.filter.base.gpuimage.GPUImageFilter;
import com.zxzx74147.mediacore.components.video.filter.data.FilterData;
import com.zxzx74147.mediacore.components.video.filter.helper.MagicFilterType;
import com.zxzx74147.mediacore.components.video.filter.widget.FilterTypeHelper;
import com.zxzx74147.mediacore.editor.MediaEditor;
import com.zxzx74147.mediacore.recorder.IProcessListener;
import com.zxzx74147.mediacoredemo.R;
import com.zxzx74147.mediacoredemo.base.BaseActivity;
import com.zxzx74147.mediacoredemo.data.BaseItemData;
import com.zxzx74147.mediacoredemo.data.IntentData;
import com.zxzx74147.mediacoredemo.databinding.ActivityVideoEditBinding;
import com.zxzx74147.mediacoredemo.databinding.LayoutFilterThumbBinding;
import com.zxzx74147.mediacoredemo.widget.recyclerview.CommonDataConverter;
import com.zxzx74147.mediacoredemo.widget.recyclerview.CommonRecyclerViewAdapter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.zxzx74147.mediacore.components.video.filter.helper.MagicFilterFactory.initFilters;

public class VideoEditorTestActivity extends BaseActivity {
    private static final int MODE_UNKNOWN = -1;
    private static final int MODE_VIDEO = 0;
    private static final int MODE_IMAGE = 1;
    private ActivityVideoEditBinding mBinding = null;

    private MediaEditor mEditor = null;
    private FilterData mLastFilter = null;
    private Uri mUri = null;
    private MaterialDialog mProgressDialog = null;
    private int mMode = MODE_UNKNOWN;

    private GPUImage mGPUImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_video_edit);
        mBinding.setHandler(this);
        initFilter();


        IntentData intentData = getIntentData();
        if (intentData != null) {
            mUri = intentData.uri;
            mBinding.videoView.setVisibility(View.VISIBLE);
            mBinding.videoView.setVideoURI(intentData.uri);
            mBinding.videoView.start();
        }
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGPUImage = new GPUImage(VideoEditorTestActivity.this);
    }

    public void onDone(View v) {
        if (mUri == null) {
            return;
        }
        if (mMode == MODE_VIDEO) {
            mEditor = new MediaEditor();
            try {
                AssetFileDescriptor music = getAssets().openFd("music/301.mp3");
//                mEditor.setInputMixFileDescriptor(music);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mEditor.setInputMedia(mUri);
            mEditor.setFilter(mLastFilter.mType);
            mEditor.setOutputMedia(FileUtil.getFile("edit_" + System.currentTimeMillis() + ".mp4"));
            try {
                mEditor.prepare();
                mEditor.start();
                mEditor.setListener(mListener);
                mProgressDialog = new MaterialDialog.Builder(this)
                        .title("处理中")
                        .content("请稍等")
                        .progress(false, 100, true)
                        .canceledOnTouchOutside(false)
//                .cancelable(false)
                        .show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void onSelect(View v) {
        FileSelectUtil.selectFile(this, "video/*", new FileSelectUtil.IFileSelector() {
            @Override
            public void onFileSelect(int resultCode, Intent data) {
                if (resultCode == Activity.RESULT_OK) {
                    mUri = data.getData();
                    ContentResolver cR = VideoEditorTestActivity.this.getContentResolver();
                    MimeTypeMap mime = MimeTypeMap.getSingleton();
                    String type = mime.getExtensionFromMimeType(cR.getType(mUri));
                    mMode = MODE_VIDEO;
                    mBinding.videoView.setVisibility(View.VISIBLE);
                    mBinding.imageView.setVisibility(View.GONE);
                    mBinding.videoView.setVideoURI(mUri);
                    mBinding.videoView.start();
                    return;
                }
            }
        });
    }

    public IProcessListener mListener = new IProcessListener() {
        @Override
        public void onPreparedDone(int max) {
            if (mProgressDialog != null) {
                mProgressDialog.setMaxProgress(max);
            }
        }

        @Override
        public void onError(int error, String errorStr) {
            mProgressDialog.setContent(errorStr);
        }

        @Override
        public void onProgress(int progress) {
            if (mProgressDialog != null) {
                mProgressDialog.setProgress(progress);
            }
        }

        @Override
        public void onComplete(Uri uri) {
            mProgressDialog.dismiss();
            mProgressDialog = null;


        }
    };

    private void initFilter() {
        mBinding.filterRecyclerview.setLayoutManager(new LinearLayoutManager(VideoEditorTestActivity.this, LinearLayoutManager.HORIZONTAL, false));
        RecyclerView.ItemAnimator animator = mBinding.filterRecyclerview.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        List<FilterData> mFilterData = new LinkedList<>();
        for (MagicFilterType type : FilterConfig.FILTER_TYPE) {
            FilterData filter = new FilterData();
            filter.mType = type;
            filter.mResourceId = FilterTypeHelper.FilterType2Thumb(type);
            filter.mFilterName = getResources().getString(FilterTypeHelper.FilterType2Name(type));
            mFilterData.add(filter);

        }
        mLastFilter = mFilterData.get(0);
        mLastFilter.isSelected = true;
        List<BaseItemData> mList = new LinkedList<>();
        List<BaseItemData<FilterData>> filterData = CommonDataConverter.convertData(R.layout.layout_filter_thumb, mFilterData);

        PlayerRecyclerViewTable table = new PlayerRecyclerViewTable();
        final CommonRecyclerViewAdapter mAdapter = new CommonRecyclerViewAdapter(table, mList);
        mAdapter.addData(filterData);
        mBinding.filterRecyclerview.setAdapter(mAdapter);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutFilterThumbBinding binding = (LayoutFilterThumbBinding) v.getTag(R.id.tag_holder);
                FilterData data = (FilterData) v.getTag(R.id.tag_data);
                GPUImageFilter filter = initFilters(data.mType);
                data.isSelected = true;
                mLastFilter.isSelected = false;
                mLastFilter = data;
                binding.setItem(data);
                mAdapter.notifyDataSetChanged();
                mBinding.videoView.setFilter(data.mType);

            }
        });
    }


}
