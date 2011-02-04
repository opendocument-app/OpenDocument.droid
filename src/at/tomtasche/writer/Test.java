package at.tomtasche.writer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.EditText;

public class Test extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final EditText edit = new EditText(this);
        edit.setText(Html.fromHtml("hallo <span style=\"color: red;\">reeed</span> tschau"));
        
        AlertDialog.Builder builder = new Builder(this);
        builder.setView(edit);
        builder.setNeutralButton("Output", new OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.e("smn", Html.toHtml(edit.getText()));
            }
        });
        builder.create().show();
    }
}
