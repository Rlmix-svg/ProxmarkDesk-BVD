import javax.tools.*;
import java.util.*;
public class CompileApp {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager files=compiler.getStandardFileManager(null,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8);
    Iterable<? extends JavaFileObject> sources=files.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(2,args.length));
    boolean success=compiler.getTask(null,files,null,Arrays.asList("-source","8","-target","8","-encoding","UTF-8","-classpath",args[0],"-d",args[1]),null,sources).call();
    // The short-lived compiler process owns the file manager until JVM exit.
    // Avoid a JDK 17 ZipFS close-time getRealPath failure in Windows sandboxes.
    if(!success)System.exit(1);
    System.out.println("Java compilation: PASS");
  }
}
