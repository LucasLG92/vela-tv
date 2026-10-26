package tv.vela.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/** Vector modules, no bitmap or remote QR service. */
final class VelaQr extends View {
    private final QrCode qr;private final Paint paint=new Paint();
    VelaQr(Context c,String text){super(c);qr=QrCode.encodeText(text,QrCode.Ecc.MEDIUM);setContentDescription(L10n.text("Local pairing QR code"));}
    protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight()),unit=size/(qr.size+8);paint.setColor(Color.WHITE);c.drawRoundRect(0,0,size,size,12,12,paint);paint.setColor(Color.BLACK);for(int y=0;y<qr.size;y++)for(int x=0;x<qr.size;x++)if(qr.getModule(x,y))c.drawRect((x+4)*unit,(y+4)*unit,(x+5)*unit,(y+5)*unit,paint);}
}
