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
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@ConfigSerializable
public final class Configuration {

    @Setting
    private Map<EntityType, Double> dropChances = new HashMap<>();

    @Setting
    private boolean warnOnZeroOrBelow = true;

    @Setting
    private boolean warnOnAboveHundred = false;

    @Setting
    private boolean warnOnNoEgg = true;

    @Setting
    private boolean checkIfApplicableOnCommand = true;

    public OptionalDouble getDropChance(final EntityType type) {
        if (type == null || !this.dropChances.containsKey(type)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(this.dropChances.get(type));
    }

    public void setDropChance(final EntityType type, final double chance) {
        this.dropChances.put(type, chance);
    }

    public void removeDropChance(final EntityType type) {
        this.dropChances.remove(type);
    }

    public List<String> findErrors() {
        if (this.warnOnZeroOrBelow || this.warnOnAboveHundred) {
            final ImmutableList.Builder<String> list = ImmutableList.builder();
            this.dropChances.forEach((t, c) -> {
                if (this.warnOnZeroOrBelow && c < 0) {
                    list.add(t.getId() + "'s drop chance is 0 or below, which means it will never drop an egg.");
                } else if (this.warnOnAboveHundred && c > 100) {
                    list.add(t.getId() + "'s drop chance is set to higher than 100, which means more than one egg could drop.");
                }
            });
            return list.build();
        }
        return ImmutableList.of();
    }

    public boolean warnOnNoEgg() {
        return this.warnOnNoEgg;
    }

    public boolean warnOnZeroOrBelow() {
        return this.warnOnZeroOrBelow;
    }

    public boolean warnOnAboveHundred() {
        return this.warnOnAboveHundred;
    }

    public boolean checkIfApplicableOnCommand() {
        return this.checkIfApplicableOnCommand;
    }

}
