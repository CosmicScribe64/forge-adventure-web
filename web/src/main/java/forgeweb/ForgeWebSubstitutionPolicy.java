package forgeweb;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Tells TeaVM which classes to take from this project instead of the original jars.
 *
 * - JDK XML APIs missing from TeaVM's classlib: org.w3c.dom.Element becomes
 *   forgeweb.shim.org.w3c.dom.TElement, and so on.
 * - Whole-class web stand-ins: io.sentry.Sentry becomes forgeweb.stub.io.sentry.Sentry.
 *
 * Classes substituted this way have their own names mapped back to the original, so code
 * inside forgeweb.shim and forgeweb.stub must only call other shims or stubs, never plain
 * forgeweb.* helpers that take shim types (see NOTES.md).
 */
public class ForgeWebSubstitutionPolicy extends SimpleSubstitutionPolicy {
    @Override
    public void contribute(SubstitutionSink sink) {
        sink.selectClasses(inPackage("org.w3c.dom", true)
                        .or(inPackage("org.xml.sax", true))
                        .or(inPackage("javax.xml", true))
                        .or(inPackage("javax.swing", true)))
                .packagePrefix("forgeweb.shim.")
                .simpleNamePrefix("T");

        sink.selectClasses(named("io.sentry.Sentry")
                        .or(named("org.tinylog.Logger"))
                        .or(named("org.tinylog.TaggedLogger"))
                        .or(named("sun.misc.Unsafe"))
                        .or(named("forge.error.ExceptionHandler"))
                        .or(named("forge.assets.AssetsDownloader"))
                        .or(named("forge.gamemodes.net.server.FServerManager"))
                        .or(named("com.google.common.eventbus.EventBus")))
                .packagePrefix("forgeweb.stub.");
    }
}
