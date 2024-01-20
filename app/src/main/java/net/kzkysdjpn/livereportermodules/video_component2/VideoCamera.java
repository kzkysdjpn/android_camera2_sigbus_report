package net.kzkysdjpn.livereportermodules.video_component2;

import android.app.Activity;
import android.content.Context;

import android.graphics.Rect;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;

import android.os.Build;
import android.os.HandlerThread;
import android.os.Handler;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/*
 * Created by Kazuki Yoshida on 2023/08/27
 */
public class VideoCamera{
	public interface PrintLogCallback{
		void errorPrintout(String arg);
		void debugPrintout(String arg);
	}

	public interface ViewStatusCallback{
		void  viewClearCallback();
		void  viewUpdateCallback();
	}

	private PrintLogCallback                           mPrintLogCallback;

	private boolean                                    mIsStartCapture;

	private ViewStatusCallback                         mViewStatusCallback;

	private Activity                                   mActivity;
	private HandlerThread                              mBackgroundThread;

	private int                                        mFrameRate;

	private CameraDevice                               mCameraDevice;

	private CameraManager                              mCameraManager;
	private HandlerThread                              mOpenHandlerThread;
	private Handler                                    mOpenHandler;


	private CaptureRequest.Builder                     mPreviewBuilder;
	private HandlerThread                              mPreviewHandlerThread;
	private Handler                                    mPreviewHandler;

	private CameraCaptureSession                       mPreviewSession;

	/*
	 * This is a linked list entry class for camera device information.
	 * 2023/11/28 Kazuki Yoshida.
	 */
	static private class CameraDeviceInformation{
		private String                   mLogicalCameraId;
		private CameraCharacteristics    mCharacteristics;

		private CameraDeviceInformation  mNext;

		public CameraDeviceInformation()
		{
			this.mNext         = null;
			return;
		}

		public CameraDeviceInformation next()
		{
			return this.mNext;
		}

		public void setNext(CameraDeviceInformation arg)
		{
			this.mNext = arg;
			return;
		}

		public String logicalCameraId()
		{
			return this.mLogicalCameraId;
		}

		public void setLogicalCameraId(String arg)
		{
			this.mLogicalCameraId = arg;
			return;
		}

		public CameraCharacteristics characteristics()
		{
			return this.mCharacteristics;
		}

		public void setCharacteristics(CameraCharacteristics arg)
		{
			this.mCharacteristics = arg;
			return;
		}

		public int numberOfPhysicalCameraIds()
		{
			return this.mCharacteristics.getPhysicalCameraIds().size();
		}

		public int cameraFacing()
		{
			Integer lensFace;

			lensFace = this.mCharacteristics.get(CameraCharacteristics.LENS_FACING);
			if(lensFace == null){
				return CameraCharacteristics.LENS_FACING_BACK;
			}
			return lensFace;
		}
	}

	private CameraDeviceInformation                    mCameraDevInfoHead;
	private CameraDeviceInformation                    mCameraDevInfoTail;
	private CameraDeviceInformation                    mCameraDevInfoEntry;

	public int numberOfPhysicalCameraIds()
	{
		if(this.mCameraDevInfoEntry == null){
			return 0;
		}
		return this.mCameraDevInfoEntry.numberOfPhysicalCameraIds();
	}

	/*
	 * Definition of the entry class for the input capture surface.
	 * 2023/11/28 Kazuki Yoshida.
	 */
	static private class CaptureSurfaceProperties{
		private Surface mSurface;
		private int     mPhysicalCameraIndex;
		private String  mSurfaceStringId;

		private CaptureSurfaceProperties mNext;

		public CaptureSurfaceProperties()
		{
			return;
		}

		public CaptureSurfaceProperties next()
		{
			return this.mNext;
		}

		public void setNext(CaptureSurfaceProperties arg)
		{
			this.mNext = arg;
			return;
		}

		public void setSurface(Surface arg)
		{
			this.mSurface = arg;
			return;
		}

		public Surface surface()
		{
			return this.mSurface;
		}

		public void setPhysicalCameraIndex(int arg)
		{
			this.mPhysicalCameraIndex = arg;
			return;
		}

		public int physicalCameraIndex()
		{
			return this.mPhysicalCameraIndex;
		}

		public void setSurfaceStringId(String arg)
		{
			this.mSurfaceStringId = arg;
			return;
		}

		public String surfaceStringId()
		{
			return this.mSurfaceStringId;
		}
	}

	private CaptureSurfaceProperties                   mCapSurfacePropHead;
	private CaptureSurfaceProperties                   mCapSurfacePropTail;
	private CaptureSurfaceProperties                   mCapSurfacePropEntry;

	private boolean allocCaptureSurfaceProp()
	{
		CaptureSurfaceProperties newEntry;

		newEntry = new CaptureSurfaceProperties();

		if(newEntry == null){
			error("New surface properties entry.");
			return false;
		}
		if(this.mCapSurfacePropTail == null){
			this.mCapSurfacePropTail = newEntry;
			this.mCapSurfacePropHead = this.mCapSurfacePropTail;
		}else{
			this.mCapSurfacePropTail.setNext(newEntry);
			this.mCapSurfacePropTail = newEntry;
		}
		this.mCapSurfacePropEntry = newEntry;
		return true;
	}

	public boolean setNewSurfacePropertiesEntry(String arg)
	{
		if(allocCaptureSurfaceProp() == false){
			error("Failed to allocate new capture surface properties entry.");
			return false;
		}
		this.mCapSurfacePropEntry.setSurfaceStringId(arg);
		return true;
	}

	public int physicalCameraIdxFromSurfaceProperties()
	{
		return this.mCapSurfacePropEntry.physicalCameraIndex();
	}

	public void setPhysicalCamIdxToSurfaceProperties(int arg)
	{
		this.mCapSurfacePropEntry.setPhysicalCameraIndex(arg);
		return;
	}

	public void setSurfaceToSurfaceProperties(Surface arg)
	{
		this.mCapSurfacePropEntry.setSurface(arg);
		return;
	}

	public boolean searchCapSurfacePropByStringId(String arg)
	{
		CaptureSurfaceProperties entry;

		entry = this.mCapSurfacePropHead;
		while(entry != null){
			if(entry.surfaceStringId().equals(arg) == true){
				break;
			}
			entry = entry.next();
		}
		if(entry == null){
			return false;
		}
		this.mCapSurfacePropEntry = entry;
		return true;
	}

	private void cleanupCaptureSurfaceProp()
	{
		CaptureSurfaceProperties entry;

		while(this.mCapSurfacePropHead != null){
			entry = this.mCapSurfacePropHead.next();
			this.mCapSurfacePropHead.setNext(null);
			this.mCapSurfacePropHead = entry;
		}
		this.mCapSurfacePropTail = this.mCapSurfacePropHead;
		return;
	}

	/**
	 * Zoom Control Parameters
	 */
	private float                                      mMaxZoomLevel;
	private int                                        mCurZoomLevel;
	private Rect                                       mActiveArraySize;

	private boolean                                    mIsZoomRestoreRequired;

	private boolean                                    mIsTorchLED;

	public final static int                            ZOOM_STATUS_NORMAL          = 0;
	public final static int                            ZOOM_STATUS_MIN             = 1;
	public final static int                            ZOOM_STATUS_MAX             = 2;

	private final CameraDevice.StateCallback     mCameraDeviceStateCallback        = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(@NonNull CameraDevice cameraDevice)
		{
			/*
			 * Save android.hardware.camera2.CameraDevice in a member variable for later use in processing.
			 * 2023/12/08 Kazuki Yoshida.
			 */
			mCameraDevice = cameraDevice;
			try {
				createCameraPreviewSession();
			}catch(CameraAccessException e){
				e.printStackTrace();
				return;
			}catch (Exception e){
				e.printStackTrace();
			}
			return;
		}

		@Override
		public void onDisconnected(CameraDevice cameraDevice)
		{
			cameraDevice.close();
			mCameraDevice = null;
			cleanupCameraDevInfo();
			return;
		}

		@Override
		public void onError(CameraDevice cameraDevice, int i)
		{
			cameraDevice.close();
			mCameraDevice = null;
			return;
		}
	};

	private final CameraCaptureSession.StateCallback   mCameraCaptureStateCallback = new CameraCaptureSession.StateCallback() {
		@Override
		public void onConfigured(CameraCaptureSession cameraCaptureSession)
		{
			mPreviewSession = cameraCaptureSession;
			updatePreview();
			return;
		}

		@Override
		public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
		{
			return;
		}
	};

	private final CameraCaptureSession.CaptureCallback mCaptureListener            = new CameraCaptureSession.CaptureCallback() {
		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
		{
			super.onCaptureCompleted(session, request, result);
			if(mIsZoomRestoreRequired == true){
				restoreZoomStatus();
				mIsZoomRestoreRequired = false;
			}
			return;
		}
	};

	public void setPrintLogCallback(PrintLogCallback arg)
	{
		this.mPrintLogCallback = arg;
		return;
	}

	public void setViewStatusCallback(ViewStatusCallback arg)
	{
		this.mViewStatusCallback = arg;
		return;
	}

	public void setActivity(Activity arg)
	{
		this.mActivity = arg;
		return;
	}

	public void setFrameRate(int arg)
	{
		this.mFrameRate = arg;
		return;
	}

	private void error(String arg)
	{
		if(this.mPrintLogCallback == null){
			return;
		}
		this.mPrintLogCallback.errorPrintout("[" +
			new Throwable().getStackTrace()[1].getFileName() +
			":" +
			new Throwable().getStackTrace()[1].getLineNumber() +
			"] : " + arg);
		return;
	}

	private void debug(String arg)
	{
		if(this.mPrintLogCallback == null){
			return;
		}
		this.mPrintLogCallback.debugPrintout("[" +
			new Throwable().getStackTrace()[1].getFileName() +
			":" +
			new Throwable().getStackTrace()[1].getLineNumber() +
			"] : " + arg);
		return;
	}

	public VideoCamera()
	{
		this.mIsStartCapture   = false;
		return;
	}

	public boolean open()
	{
		this.mCameraDevInfoHead = null;
		this.mCameraDevInfoTail = null;

		if(this.mActivity == null){
			error("Activity not set.");
			return false;
		}

		if(setupOpenHandlerThread() == false){
			error("Failed to setupOpenHandlerThread().");
			return false;
		}

		if(setupPreviewHandlerThread() == false){
			error("Failed to process at setupPreviewHandlerThread().");
			return false;
		}

		try {
			prepareCameraView();
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}

		this.mIsZoomRestoreRequired = false;
		return true;
	}

	public void close()
	{
		cleanupCameraDevInfo();
		cleanupCaptureSurfaceProp();
		cleanupOpenHandlerThread();
		cleanupPreviewHandlerThread();
		closeCamera();
		return;
	}

	private void closeCamera()
	{
		if(this.mPreviewSession != null){
			if(this.mIsStartCapture == true) {
				try {
					this.mPreviewSession.stopRepeating();
					this.mIsStartCapture = false;
				} catch (Exception e) {
					e.printStackTrace();
					error("Failed to close repeating request.");
				}
			}
			this.mPreviewSession.close();
		}
		if(this.mCameraDevice != null) {
			this.mCameraDevice.close();
		}
		return;
	}

	private void stopCamera()
	{
		if(this.mPreviewSession == null) {
			return;
		}
		if(this.mIsStartCapture == false) {
			return;
		}
		try {
			this.mPreviewSession.stopRepeating();
			this.mIsStartCapture = false;
		} catch (Exception e) {
			e.printStackTrace();
			error("Failed to close repeating request.");
		}
		if(this.mIsTorchLED == false){
			return;
		}
		switchLight(true, true);
		return;
	}

	private boolean allocCameraDevInfo(String logicalCameraId, CameraCharacteristics characteristics)
	{
		CameraDeviceInformation newEntry;

		newEntry = new CameraDeviceInformation();
		if(newEntry == null){
			error("New camera device information entry.");
			return false;
		}

		if(this.mCameraDevInfoTail == null){
			this.mCameraDevInfoTail = newEntry;
			this.mCameraDevInfoHead = this.mCameraDevInfoTail;
		}else{
			this.mCameraDevInfoTail.setNext(newEntry);
			this.mCameraDevInfoTail = newEntry;
		}
		newEntry.setLogicalCameraId(logicalCameraId);
		newEntry.setCharacteristics(characteristics);
		return true;
	}

	private String searchLogicalCameraIdDevInfoByFacing(int cameraFacing)
	{
		CameraDeviceInformation entry;
		CameraCharacteristics   characteristics;
		if(this.mCameraDevInfoHead == null){
			return null;
		}

		entry = this.mCameraDevInfoHead;

		while(entry != null){
			characteristics = entry.characteristics();
			if(characteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing){
				break;
			}
		}
		if(entry == null){
			return null;
		}
		this.mCameraDevInfoEntry = entry;
		return entry.logicalCameraId();
	}

	public int currentCameraFacing()
	{
		return this.mCameraDevInfoEntry.cameraFacing();
	}

	private void nextEntryCameraDeviceInformation()
	{
		CameraDeviceInformation entry;

		entry = this.mCameraDevInfoEntry.next();
		if(entry == null){
			entry = this.mCameraDevInfoHead;
		}
		this.mCameraDevInfoEntry = entry;
		return;
	}

	private void cleanupCameraDevInfo()
	{
		CameraDeviceInformation entry;

		while(this.mCameraDevInfoHead != null){
			entry = this.mCameraDevInfoHead.next();
			this.mCameraDevInfoHead.setNext(null);
			this.mCameraDevInfoHead = entry;
		}
		this.mCameraDevInfoTail = this.mCameraDevInfoHead;
		return;
	}

	private void prepareCameraView() throws CameraAccessException
	{
		CameraCharacteristics    characteristics;
		CaptureSurfaceProperties entry;

		/*
		 * Save android.hardware.camera2.CameraManager in a member variable for later use in processing.
		 * 2023/12/08 Kazuki Yoshida.
		 */
		this.mCameraManager = (CameraManager) this.mActivity.getSystemService(Context.CAMERA_SERVICE);

		characteristics = null;
		if(this.mCameraManager.getCameraIdList() == null){
			error("Unable to get array of logical camera IDs.");
			return;
		}
		for (String logicalCameraId : this.mCameraManager.getCameraIdList()){
			characteristics = this.mCameraManager.getCameraCharacteristics(logicalCameraId);
			if (characteristics == null) {
				continue;
			}
			if(allocCameraDevInfo(logicalCameraId, characteristics) == false){
				error("Failed to allocate camera device information entry.");
			}
		}

		if(openCamera(CameraMetadata.LENS_FACING_BACK) == false){
			error("openCamera() processing failed at " + this.mCameraDevInfoEntry.logicalCameraId());
			return;
		}
		return;
	}

	private boolean setupOpenHandlerThread()
	{
		this.mOpenHandlerThread = new HandlerThread("CameraOpen");
		if(this.mOpenHandlerThread == null){
			error("Failed to setup HandlerThread(CameraOpen)");
			return false;
		}
		this.mOpenHandlerThread.start();
		this.mOpenHandler       = new Handler(this.mOpenHandlerThread.getLooper());
		if(this.mOpenHandler == null){
			error("Failed to setup Handler(HandlerThread.getLooper())");
			return false;
		}
		return true;
	}

	private void cleanupOpenHandlerThread()
	{
		if(this.mOpenHandler != null){
			this.mOpenHandlerThread.quitSafely();
			try {
				this.mOpenHandlerThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.mOpenHandlerThread = null;
		this.mOpenHandler       = null;
		return;
	}

	private boolean openCamera(int cameraFacing)
	{
		if(this.mCameraManager == null){
			error("android.hardware.camera2.CameraManager was not saved in a member variable.");
			return false;
		}
		if(searchLogicalCameraIdDevInfoByFacing(cameraFacing) == null){
			error("Back camera device not found.");
			return false;
		}

		try {
			this.mCameraManager.openCamera(
				this.mCameraDevInfoEntry.logicalCameraId(),
				this.mCameraDeviceStateCallback,
				this.mOpenHandler);
		} catch (CameraAccessException cae) {
			cae.printStackTrace();
			return false;
		} catch (SecurityException se){
			se.printStackTrace();
			return false;
		}
		if(setupZoomControl(cameraFacing) == false){
			error("Failed to setup camera zoom parameter.");
		}

		return true;
	}

	private void createCameraPreviewSession() throws CameraAccessException
	{
		Surface                   surface;
		SessionConfiguration      sessionConfig;
		List<OutputConfiguration> outputConfigs;
		OutputConfiguration       outputConfig;
		CaptureSurfaceProperties  entry;
		int                       numberOfPhysicalCameraId;
		CameraCharacteristics     characteristics;
		String[]                  physicalCameraIds;

		if(this.mCameraDevice == null){
			error("Cannot save android.hardware.camera2.CameraDevice to a member variable.");
			return;
		}
		outputConfigs = new ArrayList<>();
		if(outputConfigs == null){
			error("Failed to allocate OutputConfig array list.");
			return;
		}

		this.mPreviewBuilder = this.mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

		entry = this.mCapSurfacePropHead;
		while(entry != null){
			surface = entry.surface();
			if(surface == null){
				break;
			}
			outputConfig = new OutputConfiguration(surface);
			if(outputConfig == null){
				break;
			}
			numberOfPhysicalCameraId = this.mCameraDevInfoEntry.numberOfPhysicalCameraIds();
			if(numberOfPhysicalCameraId <= 1){
				outputConfigs.add(outputConfig);
				this.mPreviewBuilder.addTarget(surface);
				entry = entry.next();
				continue;
			}
			if(entry.physicalCameraIndex() >= numberOfPhysicalCameraId){
				outputConfigs.add(outputConfig);
				this.mPreviewBuilder.addTarget(surface);
				entry = entry.next();
				continue;
			}
			/*
			 * Is the physical camera ID array specified correctly ?
			 * 2023/12/19 Kazuki Yoshida.
			 */
			characteristics = this.mCameraDevInfoEntry.characteristics();
			physicalCameraIds = Arrays.copyOf(characteristics.getPhysicalCameraIds().toArray(),
					characteristics.getPhysicalCameraIds().size(), String[].class);
			if(physicalCameraIds == null){
				outputConfigs.add(outputConfig);
				this.mPreviewBuilder.addTarget(surface);
				entry = entry.next();
				continue;
			}
			if(physicalCameraIds.length == 0){
				outputConfigs.add(outputConfig);
				this.mPreviewBuilder.addTarget(surface);
				entry = entry.next();
				continue;
			}
			outputConfig.setPhysicalCameraId(physicalCameraIds[entry.physicalCameraIndex()]);
			outputConfigs.add(outputConfig);
			this.mPreviewBuilder.addTarget(surface);
			entry = entry.next();
		}
		if(entry != null){
			error("Failed to set the requested camera capture information.");
			return;
		}

		sessionConfig = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigs,
			Executors.newCachedThreadPool(), this.mCameraCaptureStateCallback);
		/* TODO : Check cause exception at createCaptureSession() */
		this.mCameraDevice.createCaptureSession(sessionConfig);
		return;
	}

	public boolean isStartCapture()
	{
		if(this.mPreviewSession == null){
			return false;
		}
		if(this.mPreviewBuilder == null){
			return false;
		}
		return this.mIsStartCapture;
	}

	public void pauseCamera()
	{
		stopCamera();
		if(this.mViewStatusCallback != null){
			this.mViewStatusCallback.viewClearCallback();
		}
		return;
	}

	public void resumeCamera()
	{
		CameraCharacteristics characteristics;

		this.mIsZoomRestoreRequired = true;
		characteristics = this.mCameraDevInfoEntry.characteristics();
		if(openCamera(characteristics.get(CameraCharacteristics.LENS_FACING)) == false){
			error("openCamera() processing failed.");
			return;
		}
		this.mIsStartCapture = true;
		if(this.mViewStatusCallback != null){
			this.mViewStatusCallback.viewUpdateCallback();
		}
		return;
	}

	public boolean switchCamera()
	{
		boolean status;

		status = false;
		closeCamera();
		nextEntryCameraDeviceInformation();
		status = openCamera(this.mCameraDevInfoEntry.cameraFacing());
		if(status == false){
			error("openCamera() processing failed.");
			return false;
		}
		return true;
	}

	private boolean setupPreviewHandlerThread()
	{
		this.mPreviewHandlerThread = new HandlerThread("CameraPreview");
		if(this.mPreviewHandlerThread == null){
			error("Failed to setup HandlerThread(CameraPreview)");
			return false;
		}
		this.mPreviewHandlerThread.start();
		this.mPreviewHandler       = new Handler(this.mPreviewHandlerThread.getLooper());
		if(this.mPreviewHandler == null){
			error("Failed to setup Handler(HandlerThread.getLooper())");
			return false;
		}
		return true;
	}

	private void cleanupPreviewHandlerThread()
	{
		if(this.mPreviewHandler != null){
			this.mPreviewHandlerThread.quitSafely();
			try {
				this.mPreviewHandlerThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.mPreviewHandlerThread = null;
		this.mPreviewHandler       = null;
		return;
	}

	protected void updatePreview()
	{
		if(this.mCameraDevice == null){
			error("Cannot save android.hardware.camera2.CameraDevice to a member variable.");
			return;
		}
		this.mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
		this.mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(this.mFrameRate, this.mFrameRate));


		try {
//			this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mBackgroundHandler);
			this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mPreviewHandler);
		}catch(IllegalArgumentException iae){
			iae.printStackTrace();
		}catch(IllegalStateException ise){
			ise.printStackTrace();
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		this.mIsStartCapture = true;
		return;
	}

	/**
	 * LED Control
	 */
	public void setCameraFlashON()
	{
		/*
		if(this.mIsStartCapture == false){
			return;
		}
		 */
		setCamera2FlashControl(true);
		return;
	}

	public void setCameraFlashOFF()
	{
		/*
		if(this.mIsStartCapture == false){
			return;
		}
		 */
		setCamera2FlashControl(false);
		return;
	}

	private void resumeSession()
	{
		if(this.mIsStartCapture == true) {
			try {
//				this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mBackgroundHandler);
				this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mPreviewHandler);
			} catch (CameraAccessException e) {
				e.printStackTrace();
			}
			return;
		}
		try {
			this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mPreviewHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		return;
	}

	@RequiresApi(Build.VERSION_CODES.M)
	private void switchLight(boolean arg, boolean isResume)
	{
		this.mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
		if(arg == true){
			this.mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
		}else {
			this.mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
		}
		if(isResume == true){
			return;
		}
		resumeSession();
		return;
	}

	private void setCamera2FlashControl(boolean arg)
	{
		if(this.mCameraManager == null){
			error("android.hardware.camera2.CameraManager was not saved in a member variable.");
			return;
		}
		if(this.mIsTorchLED == arg){
			return;
		}
		switch(this.mCameraDevInfoEntry.cameraFacing()){
		case CameraCharacteristics.LENS_FACING_BACK:
			switchLight(arg, false);
			this.mIsTorchLED = arg;
			break;
		case CameraCharacteristics.LENS_FACING_FRONT:
			switchLight(false, false);
			this.mIsTorchLED = false;
			break;
		default:
			break;
		}
		return;
	}

	private boolean setupZoomControl(int cameraFacing)
	{
		CameraCharacteristics characteristics;
		String                searchCameraDevInfo;

		this.mMaxZoomLevel = -1;
		if(this.mIsZoomRestoreRequired == false) {
			this.mCurZoomLevel = 1;
		}
		this.mActiveArraySize = null;

		searchCameraDevInfo = searchLogicalCameraIdDevInfoByFacing(cameraFacing);
		if(searchCameraDevInfo == null){
			return false;
		}

		try {
			characteristics = this.mCameraManager.getCameraCharacteristics(searchLogicalCameraIdDevInfoByFacing(cameraFacing));
		} catch (CameraAccessException e) {
			e.printStackTrace();
			return false;
		}catch (NullPointerException npe){
			npe.printStackTrace();
			return false;
		}
		if(characteristics == null){
			return false;
		}
		this.mMaxZoomLevel    = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
		this.mActiveArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		return true;
	}

	private void restoreZoomStatus()
	{
		if(this.mMaxZoomLevel == -1){
			return;
		}
		if(this.mCurZoomLevel < 0){
			this.mCurZoomLevel = 1;
		}
		if(this.mCurZoomLevel > (int)this.mMaxZoomLevel){
			this.mCurZoomLevel = (int)this.mMaxZoomLevel;
		}
		updateZoom();
		return;
	}

	private boolean updateZoom()
	{
		int  cx;
		int  cy;
		int  hw;
		int  hh;
		Rect cropRegion;

		if(this.mActiveArraySize == null){
			return false;
		}

		cropRegion = null;
		switch(this.mCurZoomLevel){
		case 1:
			cropRegion = this.mActiveArraySize;
			break;
		default:
			cx = this.mActiveArraySize.centerX();
			cy = this.mActiveArraySize.centerY();
			hw = (this.mActiveArraySize.width() >> 1) / this.mCurZoomLevel;
			hh = (this.mActiveArraySize.height() >> 1) / this.mCurZoomLevel;
			cropRegion = new Rect(cx - hw, cy - hh, cx + hw, cy + hh);
			break;
		}
		if(cropRegion == null){
			return false;
		}
		try {
			this.mPreviewSession.stopRepeating();
			this.mIsStartCapture = false;
		}catch(IllegalStateException ise){
			ise.printStackTrace();
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		this.mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
		this.mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
		//this.mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(this.mFrameRate, this.mFrameRate));
		if(this.mIsTorchLED == true){
			this.mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
			this.mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
		}
		try {
//			this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mBackgroundHandler);
			this.mPreviewSession.setRepeatingRequest(this.mPreviewBuilder.build(), this.mCaptureListener, this.mPreviewHandler);
			this.mIsStartCapture = true;
		} catch (CameraAccessException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public int zoomStatus()
	{
		if(this. mCurZoomLevel <= 1){
			return ZOOM_STATUS_MIN;
		}
		if((int)this. mMaxZoomLevel <= this. mCurZoomLevel){
			return ZOOM_STATUS_MAX;
		}
		return ZOOM_STATUS_NORMAL;
	}

	public boolean zoomTele()
	{
		if(isAvailableZoom() == false){
			return false;
		}
		this.mCurZoomLevel--;
		if(this.mCurZoomLevel <= 0){
			this.mCurZoomLevel = 1;
			return false;
		}
		return updateZoom();
	}

	public boolean zoomWide()
	{
		if(isAvailableZoom() == false){
			return false;
		}
		this.mCurZoomLevel++;
		if((int)this.mMaxZoomLevel < this.mCurZoomLevel){
			this.mCurZoomLevel = (int)this.mMaxZoomLevel;
			return false;
		}
		return updateZoom();
	}

	private boolean isAvailableZoom()
	{
		if(this.mPreviewBuilder == null){
			return false;
		}

		if(this.mPreviewSession == null){
			return false;
		}
		if(this.mCameraDevice == null){
			return false;
		}
		if(this.mCameraManager == null){
			return false;
		}
		if(this.mIsStartCapture == false){
			return false;
		}
		if(this.mCurZoomLevel < 0){
			return false;
		}
		return true;
	}
}
