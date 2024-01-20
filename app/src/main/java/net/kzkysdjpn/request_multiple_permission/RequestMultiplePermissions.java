package net.kzkysdjpn.request_multiple_permission;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Context;

public class RequestMultiplePermissions{
	public interface PrintLogCallback{
		void errorPrintout(String arg);
		void debugPrintout(String arg);
	}

	public interface StatusCallback{
		void permissionAccessRequire(int permissionID);
		void permissionUnavailable(int permissionID);
		void permissionRequestDenied(int permissionID);
		void allPermissionGranted();
	}

	private class RequestPermissionEntry{
		private int                    mRequestPermissionID;
		private RequestPermissionEntry mNext;
		private String                 mPermissionOnManifest;
		private boolean                mIsGranted;

		public RequestPermissionEntry()
		{
			this.mNext                 = null;
			this.mRequestPermissionID  = -1;
			this.mPermissionOnManifest = null;
			this.mIsGranted            = false;
			return;
		}

		public void setRequestPermissionID(int arg)
		{
			this.mRequestPermissionID = arg;
			return;
		}

		public int requestPermissionID()
		{
			return this.mRequestPermissionID;
		}

		public void setNext(RequestPermissionEntry arg)
		{
			this.mNext = arg;
			return;
		}

		public RequestPermissionEntry next()
		{
			return this.mNext;
		}

		public void setPermissionOnManifest(String arg)
		{
			this.mPermissionOnManifest = arg;
			return;
		}

		public String permissionOnManifest()
		{
			return this.mPermissionOnManifest;
		}

		public void setIsGranted(boolean arg)
		{
			this.mIsGranted = arg;
			return;
		}

		public boolean isGranted()
		{
			return this.mIsGranted;
		}
	}

	private Activity                                                mActivity;

	private PrintLogCallback                                        mPrintLogCallback;

	private RequestPermissionEntry                                  mPermissionEntryHead;
	private RequestPermissionEntry                                  mPermissionEntryTail;

	private StatusCallback                                          mStatusCallback;

	private final ActivityCompat.OnRequestPermissionsResultCallback mOnRequestPermissionsResultCallback =
		new ActivityCompat.OnRequestPermissionsResultCallback() {
		@Override
		public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions,
			@NonNull int[] grantResults)
		{
			RequestPermissionEntry entry;

			entry = mPermissionEntryHead;
			while(entry != null){
				if(entry.requestPermissionID() == requestCode){
					break;
				}
			}
			if(entry == null){
				return;
			}
			if(grantResults.length <= 0){
				return;
			}
			if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
				mStatusCallback.permissionRequestDenied(requestCode);
				return;
			}
			entry.setIsGranted(true);
			entry = mPermissionEntryHead;
			while(entry != null){
				if(entry.isGranted() == false){
					break;
				}
				entry = entry.next();
			}
			if(entry != null){
				return;
			}
			mStatusCallback.allPermissionGranted();
			return;
		}
	};

	public RequestMultiplePermissions()
	{
		this.mPermissionEntryHead = null;
		this.mPermissionEntryTail = null;
		return;
	}

	public void setPrintLogCallback(PrintLogCallback arg)
	{
		this.mPrintLogCallback	= arg;
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

	public void setStatusCallback(StatusCallback arg)
	{
		this.mStatusCallback = arg;
		return;
	}

	public boolean allocatePermissionEntry(int requestPermissionID)
	{
		RequestPermissionEntry next;

		next = this.mPermissionEntryHead;
		while(next != null){
			if(next.requestPermissionID() == requestPermissionID){
				break;
			}
			next = next.next();
		}

		if(next != null){
			error("The permission ID : " + String.format("%08X", requestPermissionID) +
				" is already register in multiple permissions module.");
			return false;
		}

		if(this.mPermissionEntryHead == null){
			this.mPermissionEntryHead = new RequestPermissionEntry();
			if(this.mPermissionEntryHead == null){
				error("Failed to allocate new initial permission entry.");
				return false;
			}
			this.mPermissionEntryHead.setIsGranted(false);
			this.mPermissionEntryTail = this.mPermissionEntryHead;
		}else{
			next = new RequestPermissionEntry();
			if(next == null){
				error("Failed to allocate new permission entry.");
				return false;
			}
			next.setIsGranted(false);
			this.mPermissionEntryTail.setNext(next);
			this.mPermissionEntryTail = next;
		}
		this.mPermissionEntryTail.setRequestPermissionID(requestPermissionID);
		return true;
	}

	public void setActivity(Activity arg)
	{
		this.mActivity = arg;
		return;
	}

	public void setPermissionOnManifest(String arg)
	{
		if(this.mPermissionEntryTail == null){
			return;
		}
		this.mPermissionEntryTail.setPermissionOnManifest(arg);
		return;
	}

	public boolean publishRequestPermission()
	{
		RequestPermissionEntry entry;
		boolean                allPermissionGranted;

		entry = this.mPermissionEntryHead;

		allPermissionGranted = true;
		while(entry != null){
			if(entry.permissionOnManifest() == null){
				error("Manifest permission string not set at " +
					String.format("%08X", entry.requestPermissionID()));
				allPermissionGranted = false;
				continue;
			}
			if(ActivityCompat.checkSelfPermission(this.mActivity,
				entry.permissionOnManifest()) == PackageManager.PERMISSION_GRANTED){
				entry.setIsGranted(true);
				entry = entry.next();
				continue;
			}
			requestEntryPermission(entry);
			allPermissionGranted = false;
			entry = entry.next();
		}
		return allPermissionGranted;
	}

	private void requestEntryPermission(RequestPermissionEntry entry)
	{
		if(ActivityCompat.shouldShowRequestPermissionRationale(this.mActivity,
			entry.permissionOnManifest()) == true){
			if(this.mStatusCallback != null){
				this.mStatusCallback.permissionAccessRequire(entry.requestPermissionID());
			}
			return;
		}
		/* Request the permission. The result will be received in onRequestPermissionsResult(). */
		if(this.mStatusCallback != null){
			this.mStatusCallback.permissionUnavailable(entry.requestPermissionID());
		}
		return;
	}

	public boolean requestPermissionByID(int requestPermissionID)
	{
		RequestPermissionEntry entry;

		entry = this.mPermissionEntryHead;

		while(entry != null){
			if(entry.requestPermissionID() == requestPermissionID){
					break;
			}
			entry = entry.next();
		}

		if(entry == null){
			error("The request permission ID : " + String.format("%08X", requestPermissionID) +
				"not found in multiple permissions module.");
			return false;
		}

		ActivityCompat.requestPermissions(this.mActivity,
			new String[]{entry.permissionOnManifest()},
			entry.requestPermissionID());
		return true;
	}
}
