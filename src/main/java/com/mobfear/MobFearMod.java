package com.mobfear;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobFearMod implements ModInitializer {
    public static final String MOD_ID = "mob-fear";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int HESITATE_THRESHOLD = 5;
    private static final int FLEE_THRESHOLD = 10;
    private static final float LOW_HEALTH_PERCENT = 0.3f;
    private static final double FEAR_RANGE = 16.0;

    private static final Identifier FEAR_SLOWNESS_ID = Identifier.of(MOD_ID, "fear_slowness");
    private static final Identifier AGGRESSION_SPEED_ID = Identifier.of(MOD_ID, "aggression_speed");
    private static final Identifier AGGRESSION_DAMAGE_ID = Identifier.of(MOD_ID, "aggression_damage");

    // Player UUID -> (Mob Type -> Kill Count)
    private static final Map<UUID, Map<EntityType<?>, Integer>> killCounts = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Mob Fear mod initialized! Mobs will learn to fear dominant players.");

        // Track kills when entities die
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof MobEntity && damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                EntityType<?> mobType = entity.getType();
                UUID playerId = player.getUuid();

                killCounts.computeIfAbsent(playerId, k -> new HashMap<>());
                Map<EntityType<?>, Integer> playerKills = killCounts.get(playerId);
                int newCount = playerKills.getOrDefault(mobType, 0) + 1;
                playerKills.put(mobType, newCount);

                String mobName = EntityType.getId(mobType).getPath();
                if (newCount == HESITATE_THRESHOLD) {
                    player.sendMessage(net.minecraft.text.Text.literal("The " + mobName + "s are starting to fear you..."), true);
                } else if (newCount == FLEE_THRESHOLD) {
                    player.sendMessage(net.minecraft.text.Text.literal("The " + mobName + "s now flee in terror from you!"), true);
                }

                LOGGER.debug("Player {} killed {} (total: {})", player.getName().getString(), mobName, newCount);
            }
        });

        // Reset kills on player death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID playerId = player.getUuid();
                if (killCounts.containsKey(playerId)) {
                    killCounts.remove(playerId);
                    player.sendMessage(net.minecraft.text.Text.literal("Your dominance fades... mobs no longer fear you."), false);
                    LOGGER.debug("Reset kill counts for player {}", player.getName().getString());
                }
            }
        });

        // Clear player data on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            killCounts.remove(handler.player.getUuid());
        });

        // Server tick - apply fear effects to mobs
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    applyFearEffects(player, world);
                }
            }
        });
    }

    private void applyFearEffects(ServerPlayerEntity player, ServerWorld world) {
        UUID playerId = player.getUuid();
        Map<EntityType<?>, Integer> playerKills = killCounts.get(playerId);

        boolean playerLowHealth = player.getHealth() / player.getMaxHealth() < LOW_HEALTH_PERCENT;

        Box searchBox = new Box(
            player.getX() - FEAR_RANGE, player.getY() - FEAR_RANGE, player.getZ() - FEAR_RANGE,
            player.getX() + FEAR_RANGE, player.getY() + FEAR_RANGE, player.getZ() + FEAR_RANGE
        );

        List<MobEntity> nearbyMobs = world.getEntitiesByClass(MobEntity.class, searchBox,
            mob -> mob instanceof HostileEntity && mob.isAlive());

        for (MobEntity mob : nearbyMobs) {
            EntityType<?> mobType = mob.getType();
            int kills = playerKills != null ? playerKills.getOrDefault(mobType, 0) : 0;

            // Player low health - mobs become aggressive
            if (playerLowHealth) {
                applyAggressionBoost(mob);
                // Red particle effect for aggressive mobs
                if (world.getTime() % 10 == 0) {
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                        mob.getX(), mob.getY() + mob.getHeight() + 0.5, mob.getZ(),
                        1, 0.2, 0.2, 0.2, 0);
                }
            } else {
                removeAggressionBoost(mob);

                // Fear effects based on kill count
                if (kills >= FLEE_THRESHOLD) {
                    // Flee behavior - mob moves away from player
                    applyFleeEffect(mob, player, world);
                } else if (kills >= HESITATE_THRESHOLD) {
                    // Hesitate behavior - slower movement and attack
                    applyHesitateEffect(mob, world);
                } else {
                    // Remove fear effects
                    removeFearEffects(mob);
                }
            }
        }
    }

    private void applyHesitateEffect(MobEntity mob, ServerWorld world) {
        // Apply slowness effect for hesitation
        if (!mob.hasStatusEffect(StatusEffects.SLOWNESS)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false, false));
        }

        // Apply weakness for reduced attack
        if (!mob.hasStatusEffect(StatusEffects.WEAKNESS)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, false));
        }

        // Subtle smoke particle effect for hesitating mobs
        if (world.getTime() % 20 == 0) {
            world.spawnParticles(ParticleTypes.SMOKE,
                mob.getX(), mob.getY() + mob.getHeight() * 0.5, mob.getZ(),
                2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    private void applyFleeEffect(MobEntity mob, ServerPlayerEntity player, ServerWorld world) {
        // Strong slowness when near player (frozen in fear)
        double distance = mob.squaredDistanceTo(player);

        if (distance < 36) { // Within 6 blocks - frozen in fear
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4, false, false, false));
        } else {
            // Make mob move away from player
            Vec3d awayDir = mob.getPos().subtract(player.getPos()).normalize();
            Vec3d newVelocity = awayDir.multiply(0.3);
            mob.setVelocity(mob.getVelocity().add(newVelocity.x, 0, newVelocity.z));
            mob.velocityModified = true;

            // Speed boost to run away
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, false, false, false));
        }

        // Clear their target if it's the player they fear
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }

        // Soul particle effect for terrified mobs
        if (world.getTime() % 15 == 0) {
            world.spawnParticles(ParticleTypes.SOUL,
                mob.getX(), mob.getY() + mob.getHeight() + 0.3, mob.getZ(),
                3, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private void applyAggressionBoost(MobEntity mob) {
        EntityAttributeInstance speedAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeInstance damageAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        if (speedAttr != null && speedAttr.getModifier(AGGRESSION_SPEED_ID) == null) {
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                AGGRESSION_SPEED_ID, 0.3, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (damageAttr != null && damageAttr.getModifier(AGGRESSION_DAMAGE_ID) == null) {
            damageAttr.addTemporaryModifier(new EntityAttributeModifier(
                AGGRESSION_DAMAGE_ID, 0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeAggressionBoost(MobEntity mob) {
        EntityAttributeInstance speedAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeInstance damageAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        if (speedAttr != null) {
            speedAttr.removeModifier(AGGRESSION_SPEED_ID);
        }
        if (damageAttr != null) {
            damageAttr.removeModifier(AGGRESSION_DAMAGE_ID);
        }
    }

    private void removeFearEffects(MobEntity mob) {
        // The status effects will naturally expire
        // Just ensure aggression is also removed
        removeAggressionBoost(mob);
    }

    // Public API for other mods or commands
    public static int getKillCount(UUID playerId, EntityType<?> mobType) {
        Map<EntityType<?>, Integer> playerKills = killCounts.get(playerId);
        return playerKills != null ? playerKills.getOrDefault(mobType, 0) : 0;
    }

    public static Map<EntityType<?>, Integer> getAllKills(UUID playerId) {
        return killCounts.getOrDefault(playerId, new HashMap<>());
    }

    public static void resetKills(UUID playerId) {
        killCounts.remove(playerId);
    }
}
