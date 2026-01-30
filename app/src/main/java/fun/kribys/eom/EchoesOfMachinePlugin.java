package fun.kribys.eom;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class EchoesOfMachinePlugin extends JavaPlugin {
    private static EchoesOfMachinePlugin instance;

    public EchoesOfMachinePlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public void setup() {
    }


    public static EchoesOfMachinePlugin get() {
        return instance;
    }

    public static HytaleLogger logger() {
        return instance.getLogger();
    }
}
