package me.chill.commands

import me.chill.arguments.types.Prefix
import me.chill.database.TargetChannel
import me.chill.database.WelcomeState
import me.chill.database.preference.*
import me.chill.framework.CommandCategory
import me.chill.framework.commands
import me.chill.roles.createRole
import me.chill.settings.clap
import me.chill.settings.orange
import me.chill.settings.serve
import me.chill.utility.*
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.MessageChannel

// todo: allow users to customize how they want the default time element to be like
@CommandCategory
fun administrationCommands() = commands("Administration") {
	command("setlog") {
		execute {
			val channel = getChannel()
			val guild = getGuild()

			setChannel(TargetChannel.Logging, channel, guild)
		}
	}

	command("setjoin") {
		execute {
			val channel = getChannel()
			val guild = getGuild()

			setChannel(TargetChannel.Join, channel, guild)
		}
	}

	command("setsuggestion") {
		execute {
			val channel = getChannel()
			val guild = getGuild()

			setChannel(TargetChannel.Suggestion, channel, guild)
		}
	}

	command("setup") {
		execute {
			val guild = getGuild()
			setupMuted(guild)
			respond(
				successEmbed(
					"Set-Up Completed",
					"Bot has been set up for **${guild.name}**\n" +
						"Remember to move the `muted` role higher in order for it to take effect"
				)
			)
		}
	}

	command("setprefix") {
		expects(Prefix())
		execute {
			val guild = getGuild()
			val newPrefix = getArguments()[0] as String
			editPrefix(guild.id, newPrefix)
			respond(
				successEmbed(
					"${guild.name} Prefix Changed",
					"Prefix has been changed to **$newPrefix**"
				)
			)
		}
	}

	command("getprefix") {
		execute {
			val guild = getGuild()
			respond(
				simpleEmbed(
					"${guild.name} Prefix",
					"Current prefix is: **${getServerPrefix()}**",
					serve,
					orange
				)
			)
		}
	}
}

private fun setChannel(targetChannel: TargetChannel, channel: MessageChannel, guild: Guild) {
	val channelId = channel.id
	val serverId = guild.id

	editChannel(targetChannel, serverId, channelId)
	channel.send(
		successEmbed(
			"Channel Assigned",
			"${targetChannel.name} channel has been assigned to #${channel.name} in **${guild.name}**"))
}

private fun setupMuted(guild: Guild) {
	val mutedName = "muted"
	if (!guild.hasRole(mutedName)) createRole(guild, mutedName)

	val muted = guild.getRole(mutedName)

	guild.textChannels.forEach { channel ->
		val hasOverride = channel.rolePermissionOverrides.any {
			it.role.name.toLowerCase() == mutedName.toLowerCase()
		}

		if (!hasOverride) channel.createPermissionOverride(muted).setDeny(Permission.MESSAGE_WRITE).queue()
	}
}