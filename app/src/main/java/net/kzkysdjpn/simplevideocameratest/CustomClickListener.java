package net.kzkysdjpn.simplevideocameratest;

import android.view.View;

public class CustomClickListener implements View.OnClickListener{
	public interface OnClickListener{
		void onClick(View v, Object arg);
	}

	private Object          mOnClickExtArg;
	private OnClickListener mOnClickListener;

	public CustomClickListener()
	{
		return;
	}

	public void setOnClickListener(OnClickListener arg)
	{
		this.mOnClickListener = arg;
		return;
	}

	public void setOnClickExtArg(Object arg)
	{
		this.mOnClickExtArg = arg;
		return;
	}

	@Override
	public void onClick(View v)
	{
		if(this.mOnClickListener == null){
			return;
		}
		this.mOnClickListener.onClick(v, this.mOnClickExtArg);
		return;
	}
}
