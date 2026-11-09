package com.reiko.signaltower.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.56.0: the advancement trigger {@code reikos_signal_tower:tower}. Something happened at a tower
 * ({@code event}: see {@link TowerAdvancements}), with a number ({@code value}: a transmitter's reach
 * for "on_air"). An advancement's criterion names the event, and can ask for a range of values:
 * <pre>{"trigger": "reikos_signal_tower:tower", "conditions": {"event": "on_air", "value": {"min": 500}}}</pre>
 */
public class TowerTrigger extends SimpleCriterionTrigger<TowerTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String event, int value) {
        trigger(player, instance -> instance.matches(event, value));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String event, MinMaxBounds.Ints value)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("value", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::value)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(String event, int value) {
            return this.event.equals(event) && this.value.matches(value);
        }
    }
}
