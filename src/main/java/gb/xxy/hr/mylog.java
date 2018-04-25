package gb.xxy.hr;

import android.util.Log;

import java.text.DateFormat;
import java.util.Date;

/**
 * Created by Emil on 11/10/2017.
 */

public class mylog {
    public static void d(String tag, String message)
    {
        Log.d(tag,message);
        try {
            message= DateFormat.getDateTimeInstance().format(new Date()) + " D - " + tag+": "+message+"\n\r";
            hu_act.outputStream.write(message.getBytes());
        }
        catch (Exception E)
        {

        }
    }
    public static void e (String tag,String message)
    {
        Log.e(tag,message);
        try {
            message= DateFormat.getDateTimeInstance().format(new Date()) + " E - " + tag+": "+message+"\n\r";
            hu_act.outputStream.write(message.getBytes());
        }
        catch (Exception E)
        {

        }
    }
}
