package com.componentsapi.client.mixin;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * 暴露 PackRepository 私有的 sources 集合，用于把内存解密资源包
 * （capi_packs/*.dat）动态注册为内置资源包源。
 */
@Mixin(PackRepository.class)
public interface PackRepositoryAccessor {

    @Accessor("sources")
    Set<RepositorySource> capi$getSources();
}
