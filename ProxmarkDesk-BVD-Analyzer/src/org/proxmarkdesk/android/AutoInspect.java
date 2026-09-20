// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import java.util.*;

public final class AutoInspect {
    public interface Runner {String run(String command)throws Exception;}
    public static boolean isSearch(String command){return Arrays.asList("auto","hf search","lf search").contains(command.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT));}
    public static TagInfo collect(String searchOutput,Runner runner)throws Exception {
        TagInfo tag=TagInfo.parse(searchOutput);
        Set<String> executed=new HashSet<>();
        for(int step=0;step<2;step++){
            String cmd=tag.automaticInfo();if(cmd.isEmpty()||!executed.add(cmd))break;
            String output;
            try{output=runner.run(cmd);}catch(Exception e){tag.notice="Метка обнаружена, но дополнительное чтение прервано: "+e.getMessage();break;}
            TagInfo next=TagInfo.parse(output);
            if(next.ambiguous||(!next.id.isEmpty()&&!next.id.equals(tag.id))){tag.notice="При уточнении изменился идентификатор или обнаружены разные метки. Данные не объединены. Повторите поиск с одной меткой.";break;}
            if(!next.detected||next.id.isEmpty()){
                tag.notice="Метка обнаружена, но дополнительные сведения не подтверждены. Возможно, метка убрана или часть данных закрыта; подробности в журнале.";
                break;
            }
            tag.details+="\n--- "+cmd+" ---\n"+next.details;
            tag.fields.putAll(next.fields);
            if(next.family!=13)tag.family=next.family;
            if(!next.memory.isEmpty())tag.memory=next.memory;
            if(next.classicSize>=0)tag.classicSize=next.classicSize;
        }
        if(tag.detected&&tag.notice.isEmpty())tag.notice="Получены доступные сведения. Закрытая память требует известного ключа/пароля; полнота дампа проверяется отдельно.";
        return tag;
    }
}
