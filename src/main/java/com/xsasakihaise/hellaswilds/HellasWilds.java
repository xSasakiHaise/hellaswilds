package com.xsasakihaise.hellaswilds;

import com.xsasakihaise.hellascontrol.api.sidemods.HellasAPIHellasWilds;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(HellasWilds.MOD_ID)
public final class HellasWilds {
    public static final String MOD_ID = "hellaswilds";

    public HellasWilds() {
        HellasAPIHellasWilds.verify();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        // Additional setup can be added here once the licence check has passed.
    }
}
