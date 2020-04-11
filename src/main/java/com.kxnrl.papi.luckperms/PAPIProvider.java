package com.kxnrl.papi.luckperms;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.kxnrl.papi.luckperms.structures.IPlaceholderProvider;
import com.kxnrl.papi.luckperms.structures.IPlaceholderPlatform;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.metastacking.DuplicateRemovalFunction;
import net.luckperms.api.metastacking.MetaStackDefinition;
import net.luckperms.api.metastacking.MetaStackElement;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.track.Track;
import org.spongepowered.api.entity.living.player.Player;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PAPIProvider implements IPlaceholderProvider
{
    private final IPlaceholderPlatform platform;
    private final LuckPerms luckPerms;
    private final Map<String, Placeholder> placeholders;

    public PAPIProvider(final IPlaceholderPlatform platform, final LuckPerms luckPerms) {
        this.platform = platform;
        this.luckPerms = luckPerms;
        final PlaceholderBuilder builder = new PlaceholderBuilder();
        this.setup(builder);
        this.placeholders = builder.build();
    }

    private void setup(final PlaceholderBuilder builder) {
        builder.addDynamic("context", (player, user, userData, queryOptions, key) ->
                String.join(", ", this.luckPerms.getContextManager().getContext(player).getValues(key))
        );
        builder.addStatic("groups", (player, user, userData, queryOptions) ->
                user.getNodes()
                        .stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .map(InheritanceNode::getGroupName)
                        .map(this::convertGroupDisplayName)
                        .collect(Collectors.joining(", "))
        );
        builder.addStatic("primary_group_name", (player, user, userData, queryOptions) -> convertGroupDisplayName(user.getPrimaryGroup()));
        builder.addDynamic("has_permission", (player, user, userData, queryOptions, node) ->
                user.getNodes().stream()
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .anyMatch(n -> n.getKey().equals(node))
        );
        builder.addDynamic("inherits_permission", (player, user, userData, queryOptions, node) ->
                user.resolveInheritedNodes(queryOptions).stream()
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .anyMatch(n -> n.getKey().equals(node))
        );
        builder.addDynamic("check_permission", (player, user, userData, queryOptions, node) -> user.getCachedData().getPermissionData(queryOptions).checkPermission(node).asBoolean());
        builder.addDynamic("in_group", (player, user, userData, queryOptions, groupName) ->
                user.getNodes().stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .map(InheritanceNode::getGroupName)
                        .anyMatch(s -> s.equalsIgnoreCase(groupName))
        );
        builder.addDynamic("inherits_group", (player, user, userData, queryOptions, groupName) -> user.getCachedData().getPermissionData(queryOptions).checkPermission("group." + groupName).asBoolean());
        builder.addDynamic("on_track", (player, user, userData, queryOptions, trackName) ->
                Optional.ofNullable(this.luckPerms.getTrackManager().getTrack(trackName))
                        .map(t -> t.containsGroup(user.getPrimaryGroup()))
                        .orElse(false)
        );
        builder.addDynamic("has_groups_on_track", (player, user, userData, queryOptions, trackName) ->
                Optional.ofNullable(this.luckPerms.getTrackManager().getTrack(trackName))
                        .map(t -> user.getNodes().stream()
                                .filter(NodeType.INHERITANCE::matches)
                                .map(NodeType.INHERITANCE::cast)
                                .map(InheritanceNode::getGroupName)
                                .anyMatch(t::containsGroup)
                        )
                        .orElse(false)
        );
        builder.addStatic("highest_group_by_weight", (player, user, userData, queryOptions) ->
                user.getNodes().stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .map(InheritanceNode::getGroupName)
                        .map(n -> this.luckPerms.getGroupManager().getGroup(n))
                        .filter(Objects::nonNull)
                        .min((o1, o2) -> {
                            int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                            return ret == 1 ? 1 : -1;
                        })
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );
        builder.addStatic("lowest_group_by_weight", (player, user, userData, queryOptions) ->
                user.getNodes().stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .map(InheritanceNode::getGroupName)
                        .map(n -> this.luckPerms.getGroupManager().getGroup(n))
                        .filter(Objects::nonNull)
                        .min((o1, o2) -> {
                            int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                            return ret == 1 ? -1 : 1;
                        })
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );
        builder.addDynamic("first_group_on_tracks", (player, user, userData, queryOptions, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            CachedPermissionData permData = userData.getPermissionData(queryOptions);
            return tracks.stream()
                    .map(n -> this.luckPerms.getTrackManager().getTrack(n))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.checkPermission("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });
        builder.addDynamic("last_group_on_tracks", (player, user, userData, queryOptions, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            CachedPermissionData permData = userData.getPermissionData(queryOptions);
            return tracks.stream()
                    .map(n -> this.luckPerms.getTrackManager().getTrack(n))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(Lists::reverse)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.checkPermission("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });
        builder.addDynamic("expiry_time", (player, user, userData, queryOptions, node) -> {
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getNodes().stream()
                    .filter(Node::hasExpiry)
                    .filter(n -> !n.hasExpired())
                    .filter(n -> n.getKey().equals(node))
                    .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                    .map(Node::getExpiry)
                    .map(Instant::getEpochSecond)
                    .findFirst()
                    .map(e -> formatTime((int) (e - currentTime)))
                    .orElse("");
        });
        builder.addDynamic("inherited_expiry_time", (player, user, userData, queryOptions, node) -> {
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.resolveInheritedNodes(QueryOptions.nonContextual()).stream()
                    .filter(Node::hasExpiry)
                    .filter(n -> !n.hasExpired())
                    .filter(n -> n.getKey().equals(node))
                    .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                    .map(Node::getExpiry)
                    .map(Instant::getEpochSecond)
                    .findFirst()
                    .map(e -> formatTime((int) (e - currentTime)))
                    .orElse("");
        });
        builder.addDynamic("group_expiry_time", (player, user, userData, queryOptions, group) -> {
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getNodes().stream()
                    .filter(Node::hasExpiry)
                    .filter(n -> !n.hasExpired())
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .filter(n -> n.getGroupName().equalsIgnoreCase(group))
                    .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                    .map(Node::getExpiry)
                    .map(Instant::getEpochSecond)
                    .findFirst()
                    .map(e -> formatTime((int) (e - currentTime)))
                    .orElse("");
        });
        builder.addStatic("prefix", (player, user, userData, queryOptions) -> Strings.nullToEmpty(userData.getMetaData(this.luckPerms.getContextManager().getQueryOptions(player)).getPrefix()));
        builder.addStatic("suffix", (player, user, userData, queryOptions) -> Strings.nullToEmpty(userData.getMetaData(this.luckPerms.getContextManager().getQueryOptions(player)).getSuffix()));
        builder.addDynamic("meta", (player, user, userData, queryOptions, node) -> {
            List<String> values = userData.getMetaData(this.luckPerms.getContextManager().getQueryOptions(player)).getMeta().getOrDefault(node, ImmutableList.of());
            return values.isEmpty() ? "" : values.iterator().next();
        });
        builder.addDynamic("prefix_element", (player, user, userData, queryOptions, element) -> {
            MetaStackElement stackElement = this.luckPerms.getMetaStackFactory().fromString(element).orElse(null);
            if (stackElement == null) {
                return "ERROR: Invalid element!";
            }

            MetaStackDefinition stackDefinition = this.luckPerms.getMetaStackFactory().createDefinition(ImmutableList.of(stackElement), DuplicateRemovalFunction.RETAIN_ALL, "", "", "");
            QueryOptions newOptions = queryOptions.toBuilder()
                    .option(MetaStackDefinition.PREFIX_STACK_KEY, stackDefinition)
                    .option(MetaStackDefinition.SUFFIX_STACK_KEY, stackDefinition)
                    .build();
            return Strings.nullToEmpty(userData.getMetaData(newOptions).getPrefix());
        });
        builder.addDynamic("suffix_element", (player, user, userData, queryOptions, element) -> {
            MetaStackElement stackElement = this.luckPerms.getMetaStackFactory().fromString(element).orElse(null);
            if (stackElement == null) {
                return "ERROR: Invalid element!";
            }

            MetaStackDefinition stackDefinition = this.luckPerms.getMetaStackFactory().createDefinition(ImmutableList.of(stackElement), DuplicateRemovalFunction.RETAIN_ALL, "", "", "");
            QueryOptions newOptions = queryOptions.toBuilder()
                    .option(MetaStackDefinition.PREFIX_STACK_KEY, stackDefinition)
                    .option(MetaStackDefinition.SUFFIX_STACK_KEY, stackDefinition)
                    .build();
            return Strings.nullToEmpty(userData.getMetaData(newOptions).getSuffix());
        });
    }

    @Override
    public String onPlaceholderRequest(final Player player, final String placeholder) {
        // 因为Sponge版本的PlaceHolderAPI不提供UUID重载. 需要从luckPerms读取
        final User user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "";
        }
        final CachedDataManager data = user.getCachedData();
        final QueryOptions queryOptions = this.luckPerms.getContextManager().getQueryOptions(player);
        final String placeHolder = placeholder.toLowerCase();
        for (final Map.Entry<String, Placeholder> entry : this.placeholders.entrySet()) {
            final String id = entry.getKey();
            final Placeholder p = entry.getValue();
            boolean handled = false;
            Object result = null;
            if (p instanceof DynamicPlaceholder) {
                final DynamicPlaceholder dp = (DynamicPlaceholder)p;
                if (placeHolder.startsWith(id) && placeHolder.length() > id.length()) {
                    final String argument = placeHolder.substring(id.length());
                    result = dp.handle(player, user, data, queryOptions, argument);
                    handled = true;
                }
            }
            else if (p instanceof StaticPlaceholder) {
                final StaticPlaceholder sp = (StaticPlaceholder)p;
                if (placeHolder.equals(id)) {
                    result = sp.handle(player, user, data, queryOptions);
                    handled = true;
                }
            }
            if (!handled) {
                continue;
            }
            if (result instanceof Boolean) {
                result = this.formatBoolean((boolean)result);
            }
            return (result == null) ? null : result.toString();
        }
        return null;
    }

    private String formatTime(final int time) {
        return this.platform.formatTime(time);
    }

    private String formatBoolean(final boolean value) {
        return this.platform.formatBoolean(value);
    }

    private String convertGroupDisplayName(String groupName) {
        final Group group = this.luckPerms.getGroupManager().getGroup(groupName);
        if (group != null) {
            groupName = group.getFriendlyName();
        }
        return groupName;
    }

    private static final class PlaceholderBuilder
    {
        private final Map<String, Placeholder> placeholders;

        private PlaceholderBuilder() {
            this.placeholders = new HashMap<String, Placeholder>();
        }

        public void addDynamic(final String id, final DynamicPlaceholder placeholder) {
            this.placeholders.put(id + "_", placeholder);
        }

        public void addStatic(final String id, final StaticPlaceholder placeholder) {
            this.placeholders.put(id, placeholder);
        }

        public Map<String, Placeholder> build() {
            return (Map<String, Placeholder>)ImmutableMap.copyOf((Map)this.placeholders);
        }
    }

    @FunctionalInterface
    private interface StaticPlaceholder extends Placeholder
    {
        Object handle(final Object p0, final User p1, final CachedDataManager p2, final QueryOptions p3);
    }

    private interface Placeholder
    {
    }

    @FunctionalInterface
    private interface DynamicPlaceholder extends Placeholder
    {
        Object handle(final Object p0, final User p1, final CachedDataManager p2, final QueryOptions p3, final String p4);
    }
}
