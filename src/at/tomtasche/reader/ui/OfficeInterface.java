package at.tomtasche.reader.ui;

public interface OfficeInterface extends DialogInterface {

    public void showDocument(String html);
    
    public void runOnUiThread(Runnable runnable);
    
    public void showToast(int resId);
    
}
