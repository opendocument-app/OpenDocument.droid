package at.tomtasche.reader.background.service;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import at.tomtasche.reader.background.service.DocumentService.DocumentBinder;

public class DocumentServiceConnection implements ServiceConnection {

    Intent intent;

    DocumentBinder binder;

    Activity activity;
    
    DocumentServiceConnectionListener listener;
    
    boolean reconnect;


    public DocumentServiceConnection(Activity activity) {
	this.activity = activity;

	intent = new Intent(activity, DocumentService.class);
	
	bind();
    }


    private void bind() {
	reconnect = true;
	
	activity.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
	reconnect = false;
	
	if (!isConnected()) return;

	activity.unbindService(this);

	binder = null;
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
	binder = (DocumentBinder) service;
	
	if (listener != null) listener.onConnected();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
	if (reconnect && activity != null && !activity.isFinishing()) {
	    bind();
	}
    }


    public void setListener(DocumentServiceConnectionListener listener) {
	this.listener = listener;
    }
    
    public DocumentService getDocumentService() {
	return binder.getService();
    }

    public boolean isConnected() {
	return binder != null && binder.getService() != null;
    }


    public interface DocumentServiceConnectionListener {

	public void onConnected();

    }
}
