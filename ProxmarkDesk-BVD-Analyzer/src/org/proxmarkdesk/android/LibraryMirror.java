package org.proxmarkdesk.android;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.provider.DocumentsContract;
import java.io.*;import java.util.*;import org.json.*;

/** Copies durable internal files to a user-granted document tree, never deletes originals. */
public final class LibraryMirror {
    static final String[] FOLDERS={"Дампы","Трассы","Сигналы","Профили","Пароли","Журналы","Отчёты","Сценарии","Резервные копии"};
    public static String folder(String relative){String s=relative.toLowerCase(Locale.ROOT);if(s.startsWith("backups/"))return "Резервные копии";if(s.startsWith("password")||s.startsWith("keys/")||s.contains("classic-keys"))return "Пароли";if(s.startsWith("cache/")||s.startsWith("operations/"))return "Журналы";if(s.endsWith(".trace")||s.endsWith(".trace.meta.json"))return "Трассы";if(s.endsWith(".pm3"))return "Сигналы";if(s.startsWith("bvd-profiles/")||s.startsWith("cards/"))return "Профили";if(s.startsWith("scripts/"))return "Сценарии";if(s.startsWith("dumps/"))return "Дампы";return "Отчёты";}
    public static String profile(String name){String s=name.replaceAll("[^\\p{L}\\p{N}._ -]","_").trim();return (s.isEmpty()||s.equals(".")||s.equals(".."))?"Proxmark3-Easy":s.substring(0,Math.min(s.length(),60));}
    static Uri find(ContentResolver r,Uri parent,String name)throws Exception {Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(parent,DocumentsContract.getDocumentId(parent));try(Cursor c=r.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c==null)throw new IOException("Не удалось прочитать выбранную папку");while(c.moveToNext())if(name.equals(c.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(parent,c.getString(0));}return null;}
    static Uri dir(ContentResolver r,Uri parent,String name)throws Exception {Uri u=find(r,parent,name);if(u==null)u=DocumentsContract.createDocument(r,parent,DocumentsContract.Document.MIME_TYPE_DIR,name);if(u==null)throw new IOException("Не удалось создать папку "+name);return u;}
    static void files(File dir,List<File> result){File[] fs=dir.listFiles();if(fs!=null)for(File f:fs){if(f.isDirectory())files(f,result);else if(f.isFile()&&!f.getName().endsWith(".tmp"))result.add(f);}}
    public static String sync(Context context,File root,String profile,boolean includePasswords)throws Exception {
        String uri=context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("mirrorUri","");if(uri.isEmpty())return "Папка копирования не выбрана";
        ContentResolver resolver=context.getContentResolver();Uri tree=Uri.parse(uri);Uri base=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri device=dir(resolver,dir(resolver,base,"ProxmarkDesk"),profile(profile));Map<String,Uri> folders=new HashMap<>();for(String f:FOLDERS)folders.put(f,dir(resolver,device,f));
        File index=new File(context.getFilesDir(),"mirror-index.json");JSONObject stamp=index.isFile()?ResultCache.read(index):new JSONObject();String target=uri+"/"+profile;if(!target.equals(stamp.optString("target")))stamp=new JSONObject().put("target",target);
        List<File> all=new ArrayList<>();files(root,all);int copied=0;String devicePrefix="devices/"+profile(profile)+"/";
        for(File file:all){String rel=root.toPath().relativize(file.toPath()).toString().replace('\\','/');if(rel.startsWith("emulation/"))continue;if(rel.startsWith("devices/")&&!rel.startsWith(devicePrefix))continue;String category=folder(rel);if(!includePasswords&&category.equals("Пароли"))continue;
            long length=file.length(),modified=file.lastModified();String fingerprint=length+":"+modified;if(fingerprint.equals(stamp.optString(rel)))continue;
            Uri parent=folders.get(category);String[] components=rel.split("/");for(int i=0;i<components.length-1;i++)parent=dir(resolver,parent,components[i]);Uri dest=find(resolver,parent,file.getName());if(dest==null)dest=DocumentsContract.createDocument(resolver,parent,"application/octet-stream",file.getName());if(dest==null)throw new IOException("Не удалось создать "+file.getName());
            try(InputStream in=new FileInputStream(file);OutputStream out=resolver.openOutputStream(dest,"wt")){if(out==null)throw new IOException("Нет доступа для записи");byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            if(length==file.length()&&modified==file.lastModified())stamp.put(rel,fingerprint);copied++;
        }ResultCache.atomic(index,stamp.toString());return "Копирование завершено: "+copied+" файлов · "+profile(profile);
    }
}
