import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import java.nio.file.*;

/** Syntax-only check with the real Kotlin PSI parser. Not an Android type check/build. */
public class ParseKotlin {
  public static void main(String[] args) throws Exception {
    var disposable = Disposer.newDisposable();
    int count = 0, errors = 0;
    try {
      var env = KotlinCoreEnvironment.createForProduction(disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
      var factory = new KtPsiFactory(env.getProject(), false);
      try(var paths = Files.walk(Path.of(args[0]))) {
        for(var path : paths.filter(p -> p.toString().endsWith(".kt") || p.toString().endsWith(".kts")).toList()) {
          var source = Files.readString(path);
          var file = factory.createFile(path.getFileName().toString(), source);
          for(var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
            int line = (int)source.substring(0, error.getTextOffset()).chars().filter(c -> c == '\n').count() + 1;
            System.out.println(path + ":" + line + ": " + error.getErrorDescription()); errors++;
          }
          count++;
        }
      }
      System.out.println("Kotlin PSI: " + count + " files parsed; " + errors + " syntax errors. Type resolution and Android build NOT performed.");
      if (errors > 0) System.exit(1);
    } finally { Disposer.dispose(disposable); }
  }
}
