package com.kxnrl.papi.luckperms.structures;

import org.spongepowered.api.entity.living.player.Player;

@FunctionalInterface
public interface IPlaceholderProvider
{
    String onPlaceholderRequest(final Player player, final String placeHolder);
}
