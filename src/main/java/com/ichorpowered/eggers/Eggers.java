/*
 * This file is part of Eggers, licensed under the MIT License.
 *
 * Copyright (c) 2018 IchorPowered <https://github.com/IchorPowered>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.ichorpowered.eggers;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.util.SplittableRandom;

@Plugin(
        id = "eggers",
        name = "Eggers",
        version = "0.1.0",
        description = "Allows control over mob spawn egg drops.",
        url = "http://ichorpowered.com",
        authors = {
                "Meronat",
                "parlough"
        }
)
public final class Eggers {

    @Inject
    private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configLoader;

    private ConfigManager<Configuration> configManager;

    private final SplittableRandom random = new SplittableRandom();
    private boolean error;

    @Listener
    public void onGamePreInit(final GamePreInitializationEvent event) {
        try {
            this.configManager = new ConfigManager<>(TypeToken.of(Configuration.class), this.configLoader, Configuration::new);
        } catch (IOException | ObjectMappingException e) {
            this.error = true;
            e.printStackTrace();
        }
    }

    @Listener
    public void onGameInit(final GameInitializationEvent event) {
        if (this.error) {
            return;
        }

        this.configManager.getConfig().findErrors().forEach(e -> this.logger.warn(e));

        final CommandSpec set = CommandSpec.builder()
                .permission("eggers.set")
                .description(Text.of("Sets a mob's spawn egg drop chance, from 0% to 100%"))
                .extendedDescription(Text.of("Sets a mob's spawn egg drop chance, from 0% to 100%, or greater if you want there to be" +
                        " a chance of dropping more than one egg."))
                .arguments(GenericArguments.catalogedElement(Text.of("entity"), EntityType.class),
                        GenericArguments.doubleNum(Text.of("chance")))
                .executor((src, args) -> {
                    final EntityType type = args.<EntityType>getOne("entity").orElseThrow(() ->
                            new CommandException(Text.of(TextColors.RED, "You must specify an entity type!")));

                    if(this.configManager.getConfig().checkIfApplicableOnCommand()) {
                        final ItemStack stack = ItemStack.of(ItemTypes.SPAWN_EGG, 1);

                        if (!stack.offer(Keys.SPAWNABLE_ENTITY_TYPE, type).getType().equals(DataTransactionResult.Type.SUCCESS)) {
                            throw new CommandException(Text.of(TextColors.RED, "This entity type does not have a spawn egg!"));
                        }
                    }

                    final double chance = args.<Double>getOne("chance").orElseThrow(() ->
                            new CommandException(Text.of(TextColors.RED, "You must specify a drop chance!")));

                    if (this.configManager.getConfig().warnOnZeroOrBelow() && chance <= 0) {
                        throw new CommandException(Text.of(TextColors.RED, "You cannot set a drop chance as zero or below, consider removing instead."));
                    } else if (this.configManager.getConfig().warnOnAboveHundred() && chance > 100) {
                        throw new CommandException(Text.of(TextColors.RED, "You cannot set a drop chance as over 100."));
                    }

                    this.configManager.getConfig().setDropChance(type, chance);

                    try {
                        this.configManager.save();
                    } catch (IOException | ObjectMappingException e) {
                        throw new CommandException(Text.of(TextColors.RED, "Failed to save the mob chance changes!"));
                    }

                    src.sendMessage(Text.of(TextColors.LIGHT_PURPLE, "You have set the drop chance for ", TextColors.DARK_PURPLE, type.getId(),
                            TextColors.LIGHT_PURPLE, " to ", TextColors.DARK_PURPLE, chance, TextColors.LIGHT_PURPLE, "!"));

                    return CommandResult.success();
                })
                .build();

        final CommandSpec remove = CommandSpec.builder()
                .permission("eggers.remove")
                .arguments(GenericArguments.catalogedElement(Text.of("entity"), EntityType.class))
                .executor((src, args) -> {
                    final EntityType type = args.<EntityType>getOne("entity").orElseThrow(() ->
                            new CommandException(Text.of(TextColors.RED, "You must specify an entity type!")));

                    if (!this.configManager.getConfig().getDropChance(type).isPresent()) {
                        throw new CommandException(Text.of(TextColors.RED, "This entity type does not have a drop chance set!"));
                    }

                    this.configManager.getConfig().removeDropChance(type);

                    try {
                        this.configManager.save();
                    } catch (IOException | ObjectMappingException e) {
                        throw new CommandException(Text.of(TextColors.RED, "Failed to save the mob chance changes!"));
                    }

                    src.sendMessage(Text.of(TextColors.LIGHT_PURPLE, "You have removed the drop chance for: ", TextColors.DARK_PURPLE, type.getId()));

                    return CommandResult.success();
                })
                .build();

        final CommandSpec get = CommandSpec.builder()
                .permission("eggers.get")
                .arguments(GenericArguments.catalogedElement(Text.of("entity"), EntityType.class))
                .executor((src, args) -> {
                    final EntityType type = args.<EntityType>getOne("entity").orElseThrow(() ->
                            new CommandException(Text.of(TextColors.RED, "You must specify an entity type!")));

                    final double chance = this.configManager.getConfig().getDropChance(type).orElseThrow(() ->
                            new CommandException(Text.of(TextColors.RED, "This entity type does not have a drop chance set!")));

                    src.sendMessage(Text.of(TextColors.LIGHT_PURPLE, "The drop chance for ", TextColors.DARK_PURPLE, type.getId(),
                            TextColors.LIGHT_PURPLE, " is ", TextColors.DARK_PURPLE, chance, TextColors.LIGHT_PURPLE, "."));

                    return CommandResult.success();
                })
                .build();

        Sponge.getCommandManager().register(this,
                CommandSpec.builder()
                        .permission("eggers")
                        .executor((src, args) -> {
                            src.sendMessage(Text.of(TextColors.DARK_PURPLE, "Eggers", TextColors.LIGHT_PURPLE,
                                    " - A plugin which allows mobs to drop their spawn eggs."));
                            return CommandResult.success();
                        })
                        .child(set, "set")
                        .child(remove, "remove")
                        .child(get, "get")
                        .build(),
                "eggers");
    }

    @Listener
    public void onGameStart(final GameStartingServerEvent event) {
        if (this.error) {
            Sponge.getEventManager().unregisterListeners(this);
            this.logger.error("Eggers had errors and failed to start properly, so it is disabling itself.");
        }
    }

    @Listener
    public void onGameReload(final GameReloadEvent event) {
        try {
            this.configManager.load();
            this.configManager.save();
        } catch (IOException | ObjectMappingException e) {
            e.printStackTrace();
            this.logger.error("There was a problem reloading Eggers! Please report this.");
        }
    }

    @Listener
    public void onMobDeath(final DestructEntityEvent.Death event, @Getter("getTargetEntity") Entity entity) {
        final EntityType type = entity.getType();
        final ImmutableList.Builder<Entity> list = ImmutableList.builder();
        this.configManager.getConfig().getDropChance(type).ifPresent(d -> {
            while (d > 0) {
                final double chance = d % 101;

                if (this.random.nextDouble(0, 100) < chance) {
                    final ItemStack stack = ItemStack.of(ItemTypes.SPAWN_EGG, 1);

                    if (!stack.offer(Keys.SPAWNABLE_ENTITY_TYPE, type).getType().equals(DataTransactionResult.Type.SUCCESS)) {
                        if (this.configManager.getConfig().warnOnNoEgg()) {
                            this.logger.warn(type.getId() + " does not have a spawn egg, but you have it set in your config!");
                        }
                        return;
                    }

                    final Entity egg = entity.getWorld().createEntity(EntityTypes.ITEM, entity.getLocation().getPosition());
                    egg.offer(Keys.REPRESENTED_ITEM, stack.createSnapshot());

                    list.add(egg);
                }

                d -= 100;
            }
        });
        entity.getWorld().spawnEntities(list.build());
    }

}
