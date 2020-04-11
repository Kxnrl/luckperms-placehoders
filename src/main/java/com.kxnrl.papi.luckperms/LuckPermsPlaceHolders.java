package com.kxnrl.papi.luckperms;

import com.kxnrl.papi.luckperms.structures.IPlaceholderPlatform;
import me.rojo8399.placeholderapi.Placeholder;
import me.rojo8399.placeholderapi.PlaceholderService;
import me.rojo8399.placeholderapi.Source;
import me.rojo8399.placeholderapi.Token;
import net.luckperms.api.LuckPerms;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.ProviderRegistration;

import javax.annotation.Nullable;
import java.util.Optional;

@Plugin(id = "luckperms-placeholders",
        name = "PlaceHolderAPI-LuckPerms",
        version = "1.0.0.0",
        description = "Placeholders for Luck Perms",
        url = "https://www.kxnrl.com",
        authors = {
            "Luck",
            "Kyle"
        },
        dependencies = {
            @Dependency(id = "luckperms"),
            @Dependency(id = "placeholderapi")
        })
public class LuckPermsPlaceHolders implements IPlaceholderPlatform
{
    PAPIProvider provider;

    @Listener
    public void onServerStart(final GameStartedServerEvent event) {
        final Optional<ProviderRegistration<PlaceholderService>> PAPIProvider = (Optional<ProviderRegistration<PlaceholderService>>)Sponge.getServiceManager().getRegistration((Class)PlaceholderService.class);
        if (PAPIProvider.isPresent()) {
            final PlaceholderService api = (PlaceholderService)PAPIProvider.get().getProvider();
            api.loadAll((Object)this, (Object)this).forEach(builder -> {
                builder.author("Kyle");
                builder.version("1.0.0.0");
                try {
                    builder.buildAndRegister();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                return;
            });
        }
        final Optional<ProviderRegistration<LuckPerms>> LuckProvider = (Optional<ProviderRegistration<LuckPerms>>)Sponge.getServiceManager().getRegistration((Class)LuckPerms.class);
        if (LuckProvider.isPresent()) {
            final LuckPerms api2 = (LuckPerms)LuckProvider.get().getProvider();
            this.provider = new PAPIProvider(this, api2);
        }
    }
    
    @Placeholder(id = "luckperms")
    public String luckperms(@Token(fix = true) @Nullable final String token, @Nullable @Source final Player player) {
        if (player == null || this.provider == null) {
            return "";
        }
        final String result = this.provider.onPlaceholderRequest(player, token);
        return (result == null) ? "" : result;
    }
    
    @Override
    public String formatTime(int seconds) {
        if (seconds == 0) {
            return "0s";
        }
        long minute = seconds / 60;
        seconds %= 60;
        long hour = minute / 60L;
        minute %= 60L;
        final long day = hour / 24L;
        hour %= 24L;
        final StringBuilder time = new StringBuilder();
        if (day != 0L) {
            time.append(day).append("d ");
        }
        if (hour != 0L) {
            time.append(hour).append("h ");
        }
        if (minute != 0L) {
            time.append(minute).append("m ");
        }
        if (seconds != 0) {
            time.append(seconds).append("s");
        }
        return time.toString().trim();
    }
    
    @Override
    public String formatBoolean(final boolean b) {
        return b ? "yes" : "no";
    }
}
