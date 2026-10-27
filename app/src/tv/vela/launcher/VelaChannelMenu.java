package tv.vela.launcher;

import android.content.Context;
import android.widget.FrameLayout;
import java.util.*;

/** Same graphite action sheet in both editions; the host keeps the current guide alive. */
final class VelaChannelMenu {
    static VelaPlaybackPanel show(Context c,FrameLayout parent,String name,Runnable favorite,VelaShows.Airing airing,Runnable correction,Runnable dismissed){
        final VelaPlaybackPanel[] panel={null};
        panel[0]=new VelaPlaybackPanel(c,name,()->{
            List<VelaPlaybackPanel.Item> items=new ArrayList<>();
            items.add(new VelaPlaybackPanel.Item(L10n.text("Toggle favourite"),L10n.text("Add or remove this channel"),()->{panel[0].close();favorite.run();}));
            items.add(new VelaPlaybackPanel.Item(L10n.text("Correct programme guide"),L10n.text("Match EPG and adjust time offset"),()->{panel[0].close();correction.run();}));
            if(airing!=null&&c instanceof VelaHub.Owner)items.add(new VelaPlaybackPanel.Item(L10n.text("Programme details"),airing.title,()->{panel[0].close();((VelaHub.Owner)c).programmeDetails(airing);}));
            if(airing!=null)items.add(new VelaPlaybackPanel.Item(VelaWatchlist.contains(c,VelaWatchlist.airingId(airing))?L10n.text("Remove from Watch later"):L10n.text("Save for later"),airing.title,()->{try{VelaWatchlist.toggle(c,VelaWatchlist.airing(airing));panel[0].close();}catch(Exception bad){android.widget.Toast.makeText(c,L10n.text("Could not save this item."),0).show();}}));
            return items;
        },dismissed,L10n.text("Channel options"),L10n.text("Back · Return to guide"));parent.addView(panel[0],new FrameLayout.LayoutParams(-1,-1));return panel[0];
    }
}
