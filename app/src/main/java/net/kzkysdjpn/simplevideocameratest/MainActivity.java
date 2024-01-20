package net.kzkysdjpn.simplevideocameratest;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;

import com.google.android.material.snackbar.Snackbar;

import net.kzkysdjpn.request_multiple_permission.RequestMultiplePermissions;

import net.kzkysdjpn.livereportermodules.video_component2.VideoCamera;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

	private void error(String arg)
	{
		Log.e("Live-Reporter[ERROR]", arg);
		return;
	}

	private void debug(String arg)
	{
		Log.d("Live-Reporter[DEBUG]", arg);
		return;
	}

	private SurfaceView                                          mSurfaceView;

	private TextureView                                          mTextureView;

	private Surface                                              mPreviewSurface;

	private View mLayout;

	private VideoCamera                                          mVideoCam;

	private final static int                                     PERMISSION_ID_CAMERA                     = 0;
	private final static int                                     PERMISSION_ID_MAX                        = 1;

	private final static String[]                                MANIFEST_PERMISSIONS = {
		Manifest.permission.CAMERA
	};

	private RequestMultiplePermissions                           mRequestPermissions;

	private final static int[][]                                 PERMISSION_STATUS_MESSAGES           = {
		{/* PERMISSION_ID_CAMERA */
			R.string.camera_access_required,
			R.string.camera_permission_unavailable,
			R.string.camera_permission_granted,
			R.string.camera_permission_denied
		},
		{/* PERMISSION_ID_INTERNET */
			R.string.network_access_required,
			R.string.network_permission_unavailable,
			R.string.network_permission_granted,
			R.string.network_permission_denied
		},
		{/* PERMISSION_ID_ACCESS_NETWORK_STATE */
			R.string.network_access_required,
			R.string.network_permission_unavailable,
			R.string.network_permission_granted,
			R.string.network_permission_denied
		},
		{/* PERMISSION_ID_ACCESS_FINE_LOCATION */
			R.string.location_access_required,
			R.string.location_permission_unavailable,
			R.string.location_permission_granted,
			R.string.location_permission_denied
		},
		{/* PERMISSION_ID_ACCESS_COARSE_LOCATION */
			R.string.location_access_required,
			R.string.location_permission_unavailable,
			R.string.location_permission_granted,
			R.string.location_permission_denied
		},
		{/* PERMISSION_ID_RECORD_AUDIO */
			R.string.audio_access_required,
			R.string.audio_permission_unavailable,
			R.string.audio_permission_granted,
			R.string.audio_permission_denied
		}
	};

	private final static int                                     PERMISSION_MESSAGE_ID_ACCESS_REQUIRED    = 0;
	private final static int                                     PERMISSION_MESSAGE_ID_UNAVAILABLE        = 1;
	private final static int                                     PERMISSION_MESSAGE_ID_GRANTED            = 2;
	private final static int                                     PERMISSION_MESSAGE_ID_DENIED             = 3;

	private final static int                                     PREVIEW_SURFACE_MODE                     = 0;
	private final static int                                     PREVIEW_TEXTURE_MODE                     = 1;

	private int                                                  mPreviewMode;

	private Activity                                             mActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		ActionBar actionBar;

		super.onCreate(savedInstanceState);

		this.mActivity = this;
		setContentView(R.layout.activity_main);

		this.mLayout      = (View) findViewById(R.id.main_layout);
		this.mSurfaceView = (SurfaceView) findViewById(R.id.preview_surface_view);
		this.mTextureView = (TextureView) findViewById(R.id.preview_texture_view);
		this.mPreviewMode = PREVIEW_TEXTURE_MODE;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			Objects.requireNonNull(getWindow().getInsetsController()).hide(WindowInsets.Type.statusBars());
		}
		actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.hide();
		}
		return;
	}

	private void setSurfaceCallback()
	{
		Size    previewSize;

		switch(this.mPreviewMode){
			case PREVIEW_SURFACE_MODE:
			default:
				this.mSurfaceView.getHolder().addCallback(this.mSurfaceHolderCallback);
				this.mTextureView.setVisibility(View.GONE);
				break;
			case PREVIEW_TEXTURE_MODE:
				this.mSurfaceView.setVisibility(View.GONE);
				if(this.mTextureView.isAvailable()){
					try {
						previewSize = getCameraResolutionForSurfaceTexture(new Size(this.mTextureView.getWidth(), this.mTextureView.getHeight()), mActivity, CameraCharacteristics.LENS_FACING_BACK);
					} catch (CameraAccessException e) {
						throw new RuntimeException(e);
					}
					mPreviewSurface = new Surface(mTextureView.getSurfaceTexture());
					mTextureView.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
					checkPermissionState();
					break;
				}
				this.mTextureView.setSurfaceTextureListener(this.mSurfaceTextureListener);
				break;
		}
		return;
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		setSurfaceCallback();
		return;
	}

	private static final Comparator<Size> mSizeComparator                                    = new Comparator<Size>() {
		@Override
		public int compare(Size lhs, Size rhs)
		{
			int result;

			result = rhs.getWidth() - lhs.getWidth();
			if(result == 0){
				result = rhs.getHeight() - lhs.getHeight();
			}
			return result;
		}
	};

	private static Size determineCaptureSize(@NonNull List<Size> supportedSizes, @NonNull Size requestResolution)
	{
		Size capSize;

		if(supportedSizes == null){
			return null;
		}
		if(supportedSizes.size() <= 0){
			return null;
		}
		capSize = null;
		for(Size supportedSize : supportedSizes){
			if((supportedSize.getWidth() == requestResolution.getWidth()) &&
				(supportedSize.getHeight() == requestResolution.getHeight())){
				capSize = supportedSize;
				break;
			}
			if((supportedSize.getWidth() < requestResolution.getWidth()) &&
				(supportedSize.getHeight() < requestResolution.getHeight())){
				capSize = supportedSize;
				break;
			}
		}
		return capSize;
	}

	private Size getCameraResolutionForSurfaceTexture(@NonNull Size requestResolution, @NonNull Activity activity, int lensFacing) throws CameraAccessException
	{
		CameraManager          camManager;
		Size                   resolution;
		CameraCharacteristics  characteristics;
		StreamConfigurationMap map;
		List<Size>             supportedSizes;
		String[]               logicalCameraIdsList;

		resolution      = null;
		characteristics = null;
		camManager = (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);

		logicalCameraIdsList = camManager.getCameraIdList();
		if(camManager.getCameraIdList() == null){
			return null;
		}
		characteristics = null;
		for(String logicalCameraId : logicalCameraIdsList){
			characteristics = camManager.getCameraCharacteristics(logicalCameraId);
			if(characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing){
				break;
			}
			characteristics = null;
		}
		if(characteristics == null){
			return null;
		}
		map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
		switch(this.mPreviewMode){
		case PREVIEW_SURFACE_MODE:
			supportedSizes = Arrays.asList(map.getOutputSizes(SurfaceHolder.class));
			break;
		case PREVIEW_TEXTURE_MODE:
		default:
			supportedSizes = Arrays.asList(map.getOutputSizes(SurfaceTexture.class));
			break;
		}

		Collections.sort(supportedSizes, mSizeComparator);
		resolution = determineCaptureSize(supportedSizes, requestResolution);
		return resolution;
	}

	private final TextureView.SurfaceTextureListener            mSurfaceTextureListener                  = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height)
		{
			Size previewSize;

			try {
				previewSize = getCameraResolutionForSurfaceTexture(new Size(width, height), mActivity, CameraCharacteristics.LENS_FACING_BACK);
			} catch (CameraAccessException e) {
				throw new RuntimeException(e);
			}
			mPreviewSurface = new Surface(surface);
			surface.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
			checkPermissionState();
			return;
		}

		@Override
		public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height)
		{
			return;
		}

		@Override
		public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface)
		{
			return false;
		}

		@Override
		public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface)
		{
			return;
		}
	};

	private final SurfaceHolder.Callback                         mSurfaceHolderCallback                   = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder)
		{
			Size previewSize;

			surfaceHolder.removeCallback(mSurfaceHolderCallback);

			try {
				previewSize = getCameraResolutionForSurfaceTexture(new Size(mSurfaceView.getWidth(), mSurfaceView.getHeight()), mActivity, CameraCharacteristics.LENS_FACING_BACK);
			} catch (CameraAccessException e) {
				throw new RuntimeException(e);
			}
			if(previewSize == null){
				throw new RuntimeException();
			}
			surfaceHolder.setFixedSize(previewSize.getWidth(), previewSize.getHeight());
			mPreviewSurface = mSurfaceView.getHolder().getSurface();
			checkPermissionState();
			return;
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2)
		{
			return;
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder)
		{
			return;
		}
	};

	private final VideoCamera.ViewStatusCallback             mCaptureViewStatusCallback             = new VideoCamera.ViewStatusCallback() {
		@Override
		public void viewClearCallback() {
			return;
		}

		@Override
		public void viewUpdateCallback() {
			return;
		}
	};

	/**
	 * Member parameter defined end.
	 */
	private final RequestMultiplePermissions.StatusCallback      mPermissionStatusCallback                = new RequestMultiplePermissions.StatusCallback(){
			@Override
			public void permissionAccessRequire(int permissionID)
			{
				CustomClickListener cclistener;

				cclistener = new CustomClickListener();
				cclistener.setOnClickExtArg(Integer.valueOf(permissionID));
				cclistener.setOnClickListener(new CustomClickListener.OnClickListener() {
					@Override
					public void onClick(View view, Object arg)
					{
						Integer permissionID;
						int     id;

						permissionID = (Integer)arg;
						id           = permissionID.intValue();
						mRequestPermissions.requestPermissionByID(id);
						return;
					}
				});
				/* Display any UI and then call requestPermissionByID(permissionID) */
				Snackbar.make(mLayout, PERMISSION_STATUS_MESSAGES[permissionID][PERMISSION_MESSAGE_ID_ACCESS_REQUIRED],
						Snackbar.LENGTH_INDEFINITE).setAction(R.string.help_dialog_confirm_message, cclistener).show();
				return;
			}

			@Override
			public void permissionUnavailable(int permissionID)
			{
				Snackbar.make(mLayout,
						PERMISSION_STATUS_MESSAGES[permissionID][PERMISSION_MESSAGE_ID_UNAVAILABLE],
						Snackbar.LENGTH_SHORT).show();
				mRequestPermissions.requestPermissionByID(permissionID);
				return;
			}

			@Override
			public void permissionRequestDenied(int permissionID)
			{
				Snackbar.make(mLayout,
						PERMISSION_STATUS_MESSAGES[permissionID][PERMISSION_MESSAGE_ID_DENIED],
						Snackbar.LENGTH_SHORT).show();
				return;
			}

			@Override
			public void allPermissionGranted()
			{
				setSurfaceCallback();
				return;
			}
	};

	private void checkPermissionState()
	{
		int         i;
		boolean     status;

		this.mRequestPermissions = new RequestMultiplePermissions();
		if(this.mRequestPermissions == null){
			return;
		}
		this.mRequestPermissions.setActivity(this);
		this.mRequestPermissions.setPrintLogCallback(new RequestMultiplePermissions.PrintLogCallback(){
			@Override
			public void errorPrintout(String arg)
			{
				error(arg);
				return;
			}

			@Override
			public void debugPrintout(String arg)
			{
				debug(arg);
				return;
			}
		});
		this.mRequestPermissions.setStatusCallback(this.mPermissionStatusCallback);
		for(i = 0; i < PERMISSION_ID_MAX; i++){
			status = false;
			switch(i){
			case PERMISSION_ID_CAMERA:
				status = this.mRequestPermissions.allocatePermissionEntry(i);
				if(status == false){
					break;
				}
				this.mRequestPermissions.setPermissionOnManifest(MANIFEST_PERMISSIONS[i]);
				break;
			default:
				break;
			}
			if(status == false){
				break;
			}
		}

		if(i < PERMISSION_ID_MAX){
			return;
		}
		if(this.mRequestPermissions.publishRequestPermission() == false){
			debug("OnRequestPermission process");
			return;
		}
		debug("All permission GRANTED.");
		startCameraPreview();
		return;
	}

	private void startCameraPreview()
	{
		this.mVideoCam = new VideoCamera();
		if(this.mVideoCam == null){
			return;
		}
		if(this.mPreviewSurface == null){
			throw new RuntimeException();
		}
		this.mVideoCam.setActivity(this);
		this.mVideoCam.setNewSurfacePropertiesEntry("SIMPLE_PREVIEW_SURFACE");
		this.mVideoCam.setSurfaceToSurfaceProperties(this.mPreviewSurface);
		this.mVideoCam.setFrameRate(30);
		this.mVideoCam.setViewStatusCallback(this.mCaptureViewStatusCallback);
		this.mVideoCam.setPrintLogCallback(new VideoCamera.PrintLogCallback() {
			@Override
			public void errorPrintout(String arg)
			{
				error(arg);
				return;
			}

			@Override
			public void debugPrintout(String arg)
			{
				debug(arg);
				return;
			}
		});

		if(this.mVideoCam.open() == false){
			error("Failed to open video camera module.");
			return;
		}
		return;
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		if(this.mVideoCam != null){
			this.mVideoCam.close();
		}
		return;
	}
}
