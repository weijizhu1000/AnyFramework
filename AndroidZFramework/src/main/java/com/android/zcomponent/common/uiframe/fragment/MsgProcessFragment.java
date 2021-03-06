
package com.android.zcomponent.common.uiframe.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.View;

import com.android.zcomponent.common.uiframe.FramewrokApplication;
import com.android.zcomponent.common.uiframe.MsgProcessActivity;
import com.android.zcomponent.common.uiframe.WaitingMsgDialog;
import com.android.zcomponent.common.uiframe.WaitingMsgDialog.IShowMsg;
import com.android.zcomponent.heartbeat.IHeartBeat.HeartState;
import com.android.zcomponent.heartbeat.impl.HeartBeat;
import com.android.zcomponent.heartbeat.impl.HeartBeat.OnHeartRateChangeListener;
import com.android.zcomponent.heartbeat.impl.HeartNotify;
import com.android.zcomponent.http.HttpDataLoader;
import com.android.zcomponent.http.api.model.MessageData;
import com.android.zcomponent.http.constant.ErrorCode;
import com.android.zcomponent.util.LogEx;

/**
 * @ClassName:MsgDisposalActivity
 * @Description: 返回消息处理类
 * @author: WEI
 * @date: 2012-2-15
 * 
 */
public abstract class MsgProcessFragment extends Fragment implements
		OnHeartRateChangeListener, IShowMsg
{

	private static final String TAG = "MsgProcessFragment";

	protected boolean mbIsCancelQueryData = false;

	/** 关闭弹出框计算器，表示本次弹出框在N次请求后关闭 */
	protected int miReqCount;

	/** 数据请求等待框 */
	private WaitingMsgDialog m_showWaitingDialog;

	private HttpDataLoader mHttpDataLoader;

	private HeartNotify mHeartNotify;

	private HeartBeat mHeartBeat;

	@Override
	public void onResume()
	{
		super.onResume();
		if (null != mHeartNotify)
		{
			mHeartNotify.setActivityCreated(true);
		}
	}

	@Override
	public void onPause()
	{
		if (null != mHeartNotify)
		{
			mHeartNotify.setActivityCreated(false);
			mHeartNotify.dismiss();
		}
		if (null != mHeartBeat)
		{
			mHeartBeat.pause();
		}
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		if (null != mHeartBeat)
		{
			mHeartBeat.unregisterHeartRateChangeListener(this);
		}
		super.onDestroy();
	}

	@Override
	public void onHeartRateChange(HeartState state)
	{
		if (null != mHeartNotify)
		{
			mHeartNotify.showHeartState(state, getTitleView());
		}
		if (state != HeartState.SLOW && state != HeartState.STOPED)
		{
			LogEx.d(TAG, "网络恢复了 重新加载失败的请求！");
			if (null != mHttpDataLoader)
			{
				mHttpDataLoader.reloadFailRequest();
			}
			onNetRecovery();
		}
	}

	public void onNetRecovery()
	{

	}

	public HeartNotify getHeartNotify()
	{
		return mHeartNotify;
	}

	/** 消息传递handler，MsgTransferManager回调 */
	private Handler m_handlerMsg = new Handler()
	{

		@Override
		public void handleMessage(Message msg)
		{
			MessageData msgData = (MessageData) msg.obj;
			if (null == msgData)
			{
				LogEx.w(TAG, "msgData is null!");
				return;
			}
			LogEx.d(TAG, "msgcode" + msg.what);
			LogEx.d(TAG, "mbIsCancelQueryData = " + mbIsCancelQueryData);
			if (!mbIsCancelQueryData)
			{
				// 处理消息
				try
				{
					// 登录Cookie失效
					if (null != getActivity())
					{
						if (msgData.getReturnCode() == ErrorCode.INT_NET_CONNECT_UNAUTHORIZED)
						{
							((FramewrokApplication) getActivity()
									.getApplication()).onUnauthorized();
						}
						else if (msgData.getReturnCode() == ErrorCode.INT_NET_SYSTEM_MAINTENANCE)
						{
							((FramewrokApplication) getActivity()
									.getApplication())
									.onSystemMaintance(msgData.getContext()
											.status().message());
						}
					}
					onRecvMsg(msgData, msg.what);

					if (miReqCount > 0)
					{
						miReqCount--;
					}

					if (miReqCount == 0)
					{
						dismissWaitDialog();
					}

					if (msgData.getReturnCode() != ErrorCode.INT_QUERY_DATA_SUCCESS
							&& msgData.getReturnCode() != ErrorCode.INT_NET_CONNECT_BADREQUEST
							&& msgData.getReturnCode() != ErrorCode.INT_NET_CONNECT_UNAUTHORIZED)
					{
						if (null != mHeartBeat)
						{
							mHeartBeat.pulse();
						}
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			mbIsCancelQueryData = false;
		}
	};

	public HttpDataLoader getHttpDataLoader()
	{
		if (null == mHttpDataLoader)
		{
			mHttpDataLoader = new HttpDataLoader(getHandler());
		}
		return mHttpDataLoader;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		m_showWaitingDialog = new WaitingMsgDialog(getActivity());
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		mHeartBeat = HeartBeat.getInstance();
		if (null != getActivity()
				&& getActivity() instanceof MsgProcessActivity)
		{
			mHeartNotify =
					((MsgProcessActivity) getActivity()).getHeartNotify();
		}
		mHeartBeat.registerHeartRateChangeListener(this);
	}

	/** 等待框显示时，按返回键取消等待框，是否关闭当前页面标记 */
	protected boolean mbIsCloseActivity = true;

	/** 等待框是否可以关闭 */
	protected boolean mbIsDialogCancelable = true;

	/**
	 * <p>
	 * Description: 显示等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @param bIsCloseActivity
	 *            是否关闭当前页面
	 */
	protected void showWaitDialog(int reqCount, boolean bIsCloseActivity)
	{
		miReqCount = reqCount;
		mbIsCancelQueryData = false;
		mbIsCloseActivity = bIsCloseActivity;
		m_showWaitingDialog.showWaitDialog();
	}

	/**
	 * <p>
	 * Description: 显示等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @param bIsCloseActivity
	 *            是否关闭当前页面
	 * @param strMsg
	 *            等待框提示内容
	 */
	protected void showWaitDialog(int reqCount, boolean bIsCloseActivity,
			String strMsg)
	{
		miReqCount = reqCount;
		mbIsCancelQueryData = false;
		mbIsCloseActivity = bIsCloseActivity;
		m_showWaitingDialog.showWaitDialog(strMsg);
	}

	/**
	 * <p>
	 * Description: 显示等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @param bIsCloseActivity
	 *            是否关闭当前页面
	 * @param strMsg
	 *            等待框提示内容
	 */
	public void showWaitDialog(int reqCount, boolean bIsCloseActivity,
			int strMsg)
	{
		miReqCount = reqCount;
		mbIsCancelQueryData = false;
		mbIsCloseActivity = bIsCloseActivity;
		m_showWaitingDialog.showWaitDialog(strMsg);
	}

	/**
	 * <p>
	 * Description: 显示等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @param bIsCloseActivity
	 *            是否关闭当前页面
	 * @param strMsg
	 *            等待框提示内容
	 * @param isCancelable
	 *            按返回键是否可以关闭等待框
	 */
	protected void showWaitDialog(int reqCount, boolean bIsCloseActivity,
			String strMsg, boolean isCancelable)
	{
		miReqCount = reqCount;
		mbIsCancelQueryData = false;
		mbIsCloseActivity = bIsCloseActivity;
		mbIsDialogCancelable = isCancelable;
		m_showWaitingDialog.showWaitDialog(strMsg, isCancelable);
	}

	/**
	 * <p>
	 * Description: 显示等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @param bIsCloseActivity
	 *            是否关闭当前页面
	 * @param strMsg
	 *            等待框提示内容
	 * @param isCancelable
	 *            按返回键是否可以关闭等待框
	 */
	protected void showWaitDialog(int reqCount, boolean bIsCloseActivity,
			int strMsg, boolean isCancelable)
	{
		miReqCount = reqCount;
		mbIsCancelQueryData = false;
		mbIsCloseActivity = bIsCloseActivity;
		mbIsDialogCancelable = isCancelable;
		m_showWaitingDialog.showWaitDialog(strMsg, isCancelable);
	}

	/**
	 * <p>
	 * Description: 关闭等待框
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 */
	public void dismissWaitDialog()
	{
		m_showWaitingDialog.dismissWaitDialog();
	}

	/**
	 * <p>
	 * Description: 判断等待框是否显示
	 * <p>
	 * 
	 * @date 2014-3-31
	 * @author WEI
	 * @return
	 */
	protected boolean isDialogShowing()
	{
		return m_showWaitingDialog.isDialogShow();
	}

	@Override
	public boolean onShowMsgKey(int keyCode, KeyEvent event)
	{
		LogEx.d("BaseFragment", "onShowMsgKey");
		if (mbIsDialogCancelable)
		{
			m_showWaitingDialog.dismissWaitDialog();
			mbIsCancelQueryData = true;
			if (!mbIsCloseActivity)
			{
				mbIsCloseActivity = false;
			}
		}
		return false;
	}

	/**
	 * 
	 * 返回键被点击
	 * <p>
	 * Description: 返回键被点击的响应函数
	 * <p>
	 * 
	 * @date 2012-3-19
	 * @return
	 */
	public boolean onKeyBack(int iKeyCode, KeyEvent event)
	{
		return true;
	}

	/**
	 * <p>
	 * Description: 消息处理方法。需要每个继承的activity实现。
	 * <p>
	 * 
	 * @date 2012-2-15
	 * @param msg
	 * 
	 */
	public void onRecvMsg(MessageData msg, int msgCode) throws Exception
	{
	}

	/**
	 * <p>
	 * Description: 从父类中获取handler
	 * <p>
	 * 
	 * @date 2012-2-15
	 * @return 父类中的handler对象
	 */
	public Handler getHandler()
	{
		LogEx.d(TAG, "getHandler");
		return m_handlerMsg;
	}

	public abstract View getTitleView();
}
