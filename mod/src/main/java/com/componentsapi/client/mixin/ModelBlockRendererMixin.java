package com.componentsapi.client.mixin;

import com.componentsapi.client.ClientState;
import com.componentsapi.client.block.BlockModelRegistry;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> CAPI$SWAPPED = ThreadLocal.withInitial(() -> false);

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void capi$overrideModel(BlockQuadOutput output, float x, float y, float z,
                                    BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    BlockStateModel model, long seed, CallbackInfo ci) {
        if (CAPI$SWAPPED.get() || pos == null) return;
        String modelId = ClientState.get().blockModel(pos);
        if (modelId == null) return;
        BlockStateModel replacement = BlockModelRegistry.get(modelId);
        if (replacement == null) return;
        CAPI$SWAPPED.set(true);
        try {
            ((ModelBlockRenderer) (Object) this).tesselateBlock(output, x, y, z, level, pos, state, replacement, seed);
        } finally { CAPI$SWAPPED.set(false); }
        ci.cancel();
    }
}
