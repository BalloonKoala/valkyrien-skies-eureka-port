package org.valkyrienskies.eureka

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import java.net.URI

/**
 * Greets every player when they join, with the credits underneath.
 */
object WelcomeMessage {
    private const val EUREKA_URL = "https://modrinth.com/mod/eureka"
    private const val DISCORD_URL = "https://discord.gg/nbZ9nH3aBb"

    private val CREDITS = listOf("vs", "eureka", "contributors", "unofficial")

    @JvmStatic
    fun onPlayerJoin(player: ServerPlayer) {
        val eureka = link("info.vs_eureka.welcome.eureka", EUREKA_URL)
        val discord = link("info.vs_eureka.welcome.discord", DISCORD_URL)

        player.sendSystemMessage(Component.translatable("info.vs_eureka.welcome.hello", eureka).withStyle(ChatFormatting.GOLD))
        player.sendSystemMessage(Component.translatable("info.vs_eureka.welcome.upgrades").withStyle(ChatFormatting.YELLOW))
        player.sendSystemMessage(Component.translatable("info.vs_eureka.welcome.chat", discord).withStyle(ChatFormatting.YELLOW))
        player.sendSystemMessage(Component.translatable("info.vs_eureka.credits.title").withStyle(ChatFormatting.GOLD))
        for (credit in CREDITS) {
            player.sendSystemMessage(Component.translatable("info.vs_eureka.credits.$credit").withStyle(ChatFormatting.GRAY))
        }
    }

    private fun link(key: String, url: String): Component = Component.translatable(key).withStyle(
        Style.EMPTY
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
            .withHoverEvent(HoverEvent.ShowText(Component.literal(url)))
    )
}
