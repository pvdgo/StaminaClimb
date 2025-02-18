package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.color
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.config.StaminaConfig
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Tag
import org.bukkit.boss.BarColor
import org.bukkit.boss.BossBar
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

class StaminaTask : BukkitRunnable() {
    var lastTickNano = System.nanoTime()
    var timeSinceLastColorFlip = 0L
    var lastTime = System.currentTimeMillis()

    override fun run() {
        val currentNano = System.nanoTime()
        val lastTickNanoBackup = lastTickNano
        lastTickNano = currentNano


        val tickDuration = calculateTickDuration(currentNano, lastTickNanoBackup)

        StaminaBar.registeredBars.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: StaminaBar.registeredBars.remove(uuid).let { return@forEach }
            val bar = StaminaBar.registeredBars[uuid] ?: return@forEach
            val progress = bar.progress
            val onClimbable: Boolean = Tag.CLIMBABLE.isTagged(player.location.block.type)

            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
                StaminaBar.unregisterBar(uuid)
                player.stopClimbing()
                player.allowFlight = true
                return
            }

            bar.isVisible = progress + StaminaConfig.data.staminaRegen <= 1 //hide when full

            //regenerate stamina for BossBar
            if (!uuid.isClimbing)
                bar.progress = (bar.progress +
                        if (player.location.apply { y -= 0.0625 }.block.isSolid)
                            StaminaConfig.data.staminaRegen
                        else if (!onClimbable) StaminaConfig.data.staminaRegenInAir else 0.0
                        ).coerceAtMost(1.0)

            if (progress <= StaminaConfig.data.barRed) { //Changing bar colors and effects on player depending on its progress
                bar.color = BarColor.RED
                bar.setTitle("&c&lStamina".color()) //Make Stamina title red
                if (uuid.isClimbing) player.stopClimbing()

                uuid.canClimb =
                    false //If player reaches red zone, they can't climb until they get back in green zone
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.SLOW,
                        110,
                        2,
                        false,
                        false
                    )
                )
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.WEAKNESS,
                        110,
                        2,
                        false,
                        false
                    )
                )
            } else if (progress < 1 && !uuid.canClimb) {
                bar.color = BarColor.RED //Keep Stamina Bar red even in yellow zone while it's regenerating
            } else if ((uuid.isClimbing || onClimbable) && progress <= StaminaConfig.data.barBlink2) {
                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                if (timeSinceLastColorFlip < StaminaConfig.data.barBlinkSpeed2)
                    timeSinceLastColorFlip += deltaTime
                else {
                    flipColor(bar)
                    timeSinceLastColorFlip = 0
                }
            } else if ((uuid.isClimbing || onClimbable) && progress <= StaminaConfig.data.barBlink1) {
                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                if (timeSinceLastColorFlip < StaminaConfig.data.barBlinkSpeed1)
                    timeSinceLastColorFlip += deltaTime
                else {
                    flipColor(bar)
                    timeSinceLastColorFlip = 0
                }
            } else {
                bar.color = BarColor.GREEN
                bar.setTitle("&lStamina".color())
                uuid.canClimb = true
            }
        }

        ClimbBehaviour.isClimbing.entries.forEach { (uuid, isClimbing) ->
            val player = Bukkit.getPlayer(uuid) ?: ClimbBehaviour.isClimbing.remove(uuid).let { return }
            //if climbing in creative, stop climbing but keep flight
            if (player.gameMode == GameMode.CREATIVE) {
                player.stopClimbing()
                player.allowFlight = true
                player.isFlying = true
                return@forEach
            }

            //prevent player from climbing if they have fallen far enough or in a invalid state
            if (!player.isFlying && isClimbing || player.fallDistance > StaminaConfig.data.maxFallDist) {
                player.stopClimbing()
                return@forEach
            }
            val atWallMultiplier = player.wallDifficulty
            if (atWallMultiplier >= 0) {
                if (uuid.climbCooldownDone) uuid.restartCooldown()
                player.allowFlight = true
                player.isFlying = true
                ClimbBehaviour.isClimbing[uuid] = true
            } else {
                if (isClimbing) {
                    uuid.isClimbing = false
                    uuid.restartCooldown()
                    player.launchInDirection()
                    player.isFlying = false
                    player.allowFlight = false
                }
                //only prevent air jump after AIR_TIME ms
                else if (uuid.climbCooldown + StaminaConfig.data.airTime < 0) {
                    player.stopClimbing()
                    return@forEach
                }
            }

            if (isClimbing) uuid.removeProgress(tickDuration * StaminaConfig.data.staminaRemovePerTick * atWallMultiplier)

        }
    }

    private fun flipColor(bar: BossBar) {
        if (bar.color == BarColor.RED) {
            bar.color = BarColor.GREEN
            bar.setTitle("&lStamina".color())
        } else {
            bar.color = BarColor.RED
            bar.setTitle("&c&lStamina".color()) //Make Stamina title red
        }
    }

    private fun calculateTickDuration(currentNano: Long, lastTickNano: Long): Float {
        val nanoDiff = (currentNano - lastTickNano).toFloat()
        return nanoDiff / StaminaConfig.NANO_PER_TICK
    }
}
