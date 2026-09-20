package org.proxmarkdesk.android.bvd;

import java.util.ArrayList;
import java.util.List;

public class CardCompare {
    public static String compare(List<NtagProfile> cards){
        StringBuilder b=new StringBuilder();
        int[] pages={16,20,24,41};
        for(int page:pages){
            b.append("PAGE ").append(page).append("\n");
            for(NtagProfile c:cards){
                b.append(c.uid).append(" = ").append(c.getPage(page)).append("\n");
            }
        }
        return b.toString();
    }
}
