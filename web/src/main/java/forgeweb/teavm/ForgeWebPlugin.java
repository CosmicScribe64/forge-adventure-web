package forgeweb.teavm;

import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/** Registered via META-INF/services; runs inside the TeaVM compiler. */
public class ForgeWebPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add(new CallRedirector());
        host.add(new ReachReport());
        host.add(new Unsupported());
    }
}
