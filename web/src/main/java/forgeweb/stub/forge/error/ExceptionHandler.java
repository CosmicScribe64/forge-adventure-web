package forgeweb.stub.forge.error;


import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;

/**
 * forge.error.ExceptionHandler tees stdout into lock-protected log files, which needs
 * FileChannel/FileLock. On web, errors just go to the browser console.
 */
public class ExceptionHandler implements UncaughtExceptionHandler {
    public static File getActiveLogFile() { return null; }
    public static boolean snapshotActiveLog(File dest) { return false; }
    public static void pruneForgeLogs(int maxFiles) { }
    public static void unregisterErrorHandling() { }

    public static void registerErrorHandling() {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
    }

    @Override
    public final void uncaughtException(Thread t, Throwable ex) {
        handle(ex);
    }

    public final void handle(Throwable ex) {
        System.out.println("Uncaught exception: " + ex);
        ex.printStackTrace();
    }
}
