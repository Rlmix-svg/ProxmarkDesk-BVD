package org.proxmarkdesk.android;
import java.io.File;
import java.util.Arrays;

public class ConnectionTests {
    static int checks;
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    static UsbDiagnostics.Result parse(String s,boolean root,int count,int code){return UsbDiagnostics.parse(new UsbDiagnostics.Capture(code,false,s),root,count,"");}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("--child")){
            if(args[1].equals("sleep")){Thread.sleep(20000);return;}
            byte[] data=new byte[160000];Arrays.fill(data,(byte)'x');System.out.write(data);System.out.flush();return;
        }
        check(ClientInput.error("")!=null,"empty command rejected before session work");
        check(ClientInput.error(" \t ")!=null,"whitespace-only command rejected");
        check(ClientInput.error(null)!=null,"null command rejected");
        check(ClientInput.error("hw version")==null,"valid command accepted");
        check(ClientInput.error("hw version\nhf search")!=null,"multiline rejected");
        check(ClientInput.error("hw\0version")!=null,"NUL rejected");
        char[] letters=new char[125];Arrays.fill(letters,'я');check(ClientInput.error(new String(letters))==null,"250-byte UTF8 boundary");
        check(ClientInput.error(new String(letters)+"я")!=null,"UTF8 byte limit");
        String begin="PMD_BEGIN\nPMD_UID=0\nPMD_DEV_LIST_OK\nPMD_USB_LIST_OK\n";
        UsbDiagnostics.Result r=parse(begin+"PMD_END\n",true,0,0);
        check(r.complete&&r.root&&r.ports.isEmpty(),"complete root probe without devices");
        check(r.summary.contains("OTG"),"no devices produces physical connection guidance");
        r=parse("",true,0,1);check(!r.complete&&r.summary.contains("не подтверждено"),"empty su failure is not no-device result");
        r=parse("PMD_BEGIN\nPMD_UID=2000\nPMD_END\n",true,0,0);check(!r.root&&r.summary.contains("Root не подтверждён"),"non-root uid explained");
        r=parse(begin+"PMD_TTY=/dev/ttyACM0\nPMD_TTY=/dev/ttyUSB1\nPMD_TTY=/dev/ttyACM0\nPMD_END\n",true,1,0);
        check(r.ports.size()==2&&r.ports.get(0).equals("/dev/ttyACM0"),"multiple ports deduplicated and sorted");
        r=parse(begin+"PMD_TTY=/dev/ttyACM0;reboot\nPMD_TTY=/dev/../../etc\nPMD_END\n",true,0,0);check(r.ports.isEmpty(),"only strict tty paths accepted");
        r=parse(begin+"PMD_SYS_TTY=/sys/class/tty/ttyACM0\nPMD_END\n",true,0,0);check(r.summary.contains("sysfs"),"sysfs tty without dev node explained");
        r=parse(begin+"PMD_USB=9ac4:4b8f\nPMD_END\n",true,0,0);check(r.summary.contains("VID/PID"),"USB with no tty kept distinct");
        r=parse("PMD_BEGIN\nPMD_UID=0\nPermission denied\nPMD_END\n",true,0,0);check(r.summary.contains("не все системные пути"),"unreadable directories do not imply absent hardware");
        r=parse(begin,true,0,0);check(!r.complete,"truncated probe rejected");
        r=UsbDiagnostics.parse(new UsbDiagnostics.Capture(-1,true,begin),true,0,"");check(r.summary.contains("не завершилась"),"timeout distinguished");
        String java=new File(System.getProperty("java.home"),"bin/java"+(System.getProperty("os.name").startsWith("Windows")?".exe":"")).getPath();
        ProcessBuilder output=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),ConnectionTests.class.getName(),"--child","output");
        UsbDiagnostics.Capture c=UsbDiagnostics.capture(output,10);
        check(!c.timedOut&&c.exitCode==0&&c.output.length()==65536,"large child output drained without pipe deadlock");
        ProcessBuilder sleep=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),ConnectionTests.class.getName(),"--child","sleep");
        c=UsbDiagnostics.capture(sleep,1);check(c.timedOut,"hung probe terminated within bounded wait");
        System.out.println("TOTAL "+checks+" connection checks passed");
    }
}
