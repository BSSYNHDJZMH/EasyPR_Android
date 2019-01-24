package com.aiseminar.platerecognizer.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.aiseminar.EasyPR.PlateRecognizer;
import com.aiseminar.platerecognizer.R;
import com.aiseminar.util.BitmapUtil;
import com.aiseminar.util.FileUtil;
import com.aiseminar.util.LogUtil;
import com.otaliastudios.cameraview.AspectRatio;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Size;
import com.otaliastudios.cameraview.SizeSelector;
import com.otaliastudios.cameraview.SizeSelectors;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;

public class RealTimeRecongnitionActivity extends AppCompatActivity
{

	@Bind(R.id.cameraView)
	CameraView cameraView;

	@Bind(R.id.tvPlateResult)
	TextView mTvPlateResult;

	@Bind(R.id.ivPlateRect)
	ImageView mIvPlateRect;
	@Bind(R.id.btn_switch)
	Button btnSwitch;

	private PlateRecognizer mPlateRecognizer;
	private long oldTime;
	private Size size;
	private Boolean isFirst = true;
	private List<Size> list = new ArrayList<>();
    private String TAG = "RealTimeRecongnitionActivity";
    private Boolean isIdentifying;//判断是否正在识别的值
	private int processCount;
	private int synchronizedCount;
    private List<String> recognizeResultList = new ArrayList<>();
    private Set<String> recognizeResultSet = new HashSet<>();
    private String plate;

    @Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_real_time_recongnition);
		ButterKnife.bind(this);

		btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RealTimeRecongnitionActivity.this,CameraActivity.class));
            }
        });
		mPlateRecognizer = new PlateRecognizer(this);
		oldTime = System.currentTimeMillis();

		DisplayMetrics metric = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metric);
		int w = metric.widthPixels; // 屏幕宽度（像素）
		int h = metric.heightPixels;

		SizeSelector width = SizeSelectors.minWidth(w);
		SizeSelector height = SizeSelectors.minHeight(h);
		SizeSelector dimensions = SizeSelectors.and(width, height); // Matches
																	// sizes
																	// bigger
																	// than
																	// 1000x2000.
        Log.e(TAG, "onCreate: dimensions"+dimensions.toString());
		SizeSelector ratio = SizeSelectors.aspectRatio(AspectRatio.of(w, h), 0); // Matches
																					// 1:1
																					// sizes.
        Log.e(TAG, "onCreate: AspectRatio.of(w, h)"+AspectRatio.of(w, h).toString());
        Log.e(TAG, "onCreate: ratio"+ratio.toString());
		SizeSelector result = SizeSelectors.or(
				SizeSelectors.and(ratio, dimensions), // Try to match both
														// constraints
				ratio, // If none is found, at least try to match the aspect
						// ratio
				SizeSelectors.biggest() // If none is found, take the biggest
		);
		cameraView.setPictureSize(result);

		// cameraView.setPictureSize(new SizeSelector() {
		// @Override
		// public List<Size> select(List<Size> source) {
		// // Receives a list of available sizes.
		// // Must return a list of acceptable sizes.
		//
		// }
		// });
		//
		// Log.e("", "size: "+size);
		cameraView.addFrameProcessor(new FrameProcessor()
		{
			@Override
			public void process(@NonNull Frame frame)
			{
				// if(isFirst){
				// runOnUiThread(new Runnable() {
				// @Override
				// public void run() {
				// size= cameraView.getPreviewSize();
				// isFirst = false;
				// }
				// });
				// }
				cameraProcess(frame);

			}
		});

		cameraView.setLifecycleOwner(this);
		cameraView.start();

		// size= cameraView.getPreviewSize();
		// Log.e("", "size: "+size );
	}

	private void cameraProcess(Frame frame) {

		long nowTime = System.currentTimeMillis();
		if (nowTime - oldTime < 500)
		{
			return;
		}

//				 Log.d("frame",frame.getData().length+"size"+frame.getSize().getHeight());

		oldTime = nowTime;

		// Camera.Size size =cameraView.getPreviewSize(); //获取预览大小
		// final int w =size.getWidth(); //宽度
		// final int h =size.getHeight();

		DisplayMetrics metric = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metric);
		// int w = metric.widthPixels; // 屏幕宽度（像素）
		// int h = metric.heightPixels;

		int w = frame.getSize().getWidth();
		int h = frame.getSize().getHeight();

		Log.e("cropBitmapAndRecognize", frame.getSize().getWidth()
				+ "h---" + frame.getSize().getHeight());

		Log.e("cropBitmapAndRecognize",
				"process: width" + w + ",height" + h);
		final YuvImage image = new YuvImage(frame.getData(),
				ImageFormat.NV21, w, h, null);
		// 转Bitmap
		ByteArrayOutputStream os = new ByteArrayOutputStream(
				frame.getData().length);
		if (!image.compressToJpeg(new Rect(0, 0, w, h), 100, os))
		{
			return;
		}
		byte[] tmp = os.toByteArray();
		Bitmap bitmap = BitmapFactory.decodeByteArray(tmp, 0,
				tmp.length);
		Bitmap rotatedBitmap = BitmapUtil.createRotateBitmap(bitmap);

		cropBitmapAndRecognize(rotatedBitmap);
	}

	public void cropBitmapAndRecognize(Bitmap originalBitmap)
	{
		// 裁剪出关注区域
		DisplayMetrics metric = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metric);
		int width = metric.widthPixels; // 屏幕宽度（像素）
		int height = metric.heightPixels; // 屏幕高度（像素）
		Bitmap sizeBitmap = Bitmap.createScaledBitmap(originalBitmap, width,
				height, true);

		Log.e("cropBitmapAndRecognize",
				"cropBitmapAndRecognize: width" + width + ",height" + height);

		int rectWidth = (int) (mIvPlateRect.getWidth() * 1.5);
		int rectHight = (int) (mIvPlateRect.getHeight() * 1.5);

		int[] location = new int[2];
		mIvPlateRect.getLocationOnScreen(location);
		location[0] -= mIvPlateRect.getWidth() * 0.5 / 2;
		location[1] -= mIvPlateRect.getHeight() * 0.5 / 2;
//		location[1] -= mIvPlateRect.getHeight();

		Log.e("cropBitmapAndRecognize", "cropBitmapAndRecognize: rectWidth"
				+ rectWidth + ",rectHight" + rectHight);

		Bitmap normalBitmap = Bitmap.createBitmap(sizeBitmap, location[0],
				location[1], rectWidth, rectHight);

		// 保存图片并进行车牌识别
		File pictureFile = FileUtil
				.getOutputMediaFile(FileUtil.FILE_TYPE_PLATE);
		if (pictureFile == null)
		{
			Log.d("", "Error creating media file, check storage permissions: ");
			return;
		}

		try
		{

			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					mTvPlateResult.setText("正在识别...");
				}
			});

			FileOutputStream fos = new FileOutputStream(pictureFile);
			normalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
//			originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
			fos.close();

			// 最后通知图库更新
			RealTimeRecongnitionActivity.this.sendBroadcast(new Intent(
					Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
					Uri.parse("file://" + pictureFile.getAbsolutePath())));

//			recognize(pictureFile.getAbsolutePath());
			recognizeOptimization(pictureFile.getAbsolutePath());

		}
		catch (FileNotFoundException e)
		{
			Log.d("", "File not found: " + e.getMessage());
		}
		catch (IOException e)
		{
			Log.d("", "Error accessing file: " + e.getMessage());
		}
    }

    private void recognize(String absolutePath) {
        // 进行车牌识别

        final String plate = mPlateRecognizer
                .recognize(absolutePath);
        if (null != plate && !plate.equalsIgnoreCase("0"))
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mTvPlateResult.setText(plate);
                }
            });

        }
        else
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mTvPlateResult.setText("调整角度.");
                }
            });
        }
    }

    /**
     * 识别优化
     * @param absolutePath
     */
    private void recognizeOptimization(String absolutePath) {
        // 进行车牌识别

        plate = mPlateRecognizer
                .recognize(absolutePath);
        if (null != plate && !plate.equalsIgnoreCase("0"))
        {
            //将识别的结果加到一个list里
            recognizeResultList.add(plate);
            LogUtil.info("recognizeOptimization: 本次识别结果："+plate);
            /**
             * 这里为了判断返回的结果里面三个值相等，说明有三次识别是一样的，那么就认定为正确的
             * 而三个值相等的前提是这个list至少有三个值，也就是至少识别了三次
             */
            if(recognizeResultList.size() > 2){
//                for (int i = 0; i < recognizeResultList.size(); i++) {
//                    recognizeResultSet.add(recognizeResultList.get(i));
//                }
                //list转成set
                recognizeResultSet = new HashSet<>(recognizeResultList);
                //因为list可重复，set不可重复，转成set以后二者size相减，剩余的即是重复的个数减-1，比如有3个重复，set里要留一个，剩余就是2个
                if(recognizeResultList.size()-recognizeResultSet.size() == 2){
                    //获取list中最后一个值，即是重复了3次的值
                    plate = recognizeResultList.get(recognizeResultList.size()-1);
                    LogUtil.info("recognizeOptimization: 三次识别相同的结果："+plate );
                    //获取完就清空
                    recognizeResultList.clear();
                    recognizeResultSet.clear();
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							mTvPlateResult.setText(plate);
						}
					});
                }
            }

        }
        else
        {
			Log.e(TAG, "recognizeOptimization: 本次识别失败");
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mTvPlateResult.setText("调整角度.");
                }
            });
        }
    }
}
