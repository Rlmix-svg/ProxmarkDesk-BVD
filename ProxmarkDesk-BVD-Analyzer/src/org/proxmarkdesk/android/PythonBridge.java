package org.proxmarkdesk.android;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import org.json.*;

/** One authenticated local request at a time, sharing the existing PM3 transport. */
public final class PythonBridge implements AutoCloseable {
    public interface Handler { JSONObject handle(JSONObject request)throws Exception; }
    private final ServerSocket server;private final Handler handler;private final Thread thread;
    public final String token;private volatile boolean closed;private volatile Socket active;
    public PythonBridge(Handler handler)throws IOException {
        this.handler=handler;byte[] key=new byte[32];new SecureRandom().nextBytes(key);token=Base64.getEncoder().encodeToString(key);
        server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),4);
        thread=new Thread(this::serve,"python-pm3-bridge");thread.setDaemon(true);thread.start();
    }
    public int port(){return server.getLocalPort();}
    static String readLine(InputStream in,int limit)throws IOException {ByteArrayOutputStream bytes=new ByteArrayOutputStream();int ch;while((ch=in.read())!=-1){if(ch=='\n')break;if(bytes.size()>=limit)throw new IOException("Запрос Python слишком длинный");bytes.write(ch);}if(ch==-1&&bytes.size()==0)throw new EOFException();return bytes.toString("UTF-8");}
    private void serve(){while(!closed){try(Socket socket=server.accept()){active=socket;socket.setSoTimeout(5000);JSONObject reply;
        try{JSONObject request=new JSONObject(readLine(socket.getInputStream(),16384));if(!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),request.optString("token").getBytes(StandardCharsets.UTF_8)))throw new IOException("Нет доступа к сеансу Python");if(closed)break;reply=handler.handle(request);reply.put("ok",true);}catch(Exception e){reply=new JSONObject();try{reply.put("ok",false).put("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}catch(Exception ignored){}}
        socket.getOutputStream().write((reply.toString()+"\n").getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();
    }catch(Exception ignored){if(closed)break;}finally{active=null;}}}
    public void close(){closed=true;try{server.close();}catch(IOException ignored){}Socket socket=active;if(socket!=null)try{socket.close();}catch(IOException ignored){}thread.interrupt();}
    public void awaitClosed()throws InterruptedException{if(Thread.currentThread()!=thread)thread.join(3000);}
}
