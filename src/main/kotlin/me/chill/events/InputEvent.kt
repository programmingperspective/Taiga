package me.chill.events

import me.chill.arguments.parseArguments
import me.chill.arguments.types.Sentence
import me.chill.credentials
import me.chill.database.operations.*
import me.chill.exception.TaigaException
import me.chill.framework.Command
import me.chill.framework.CommandContainer
import me.chill.json.help.findCommand
import me.chill.json.help.syntax
import me.chill.logging.normalLog
import me.chill.raidManger
import me.chill.settings.noWay
import me.chill.settings.red
import me.chill.settings.shock
import me.chill.utility.jda.embed
import me.chill.utility.jda.failureEmbed
import me.chill.utility.jda.send
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.util.*

class InputEvent : ListenerAdapter() {
	override fun onMessageReceived(event: MessageReceivedEvent?) {
		event ?: throw TaigaException("Event object was null during message receive")

		if (event.member == null) return
		if (event.member!!.user.isBot) return

		val message = event.message.contentRaw.trim()
		val messageChannel = event.channel
		val server = event.guild
		val invoker = event.member

		val serverPrefix = getPrefix(server.id)
		if (!message.startsWith(serverPrefix)) {
			val isExcludedFromRaidControl =
				invoker.roles.isNotEmpty()
					&& getRaidRoleExcluded(server.id) != null
					&& invoker.roles[0].position >= server.getRoleById(getRaidRoleExcluded(server.id)).position
			val isAlreadyCaught = hasRaider(server.id, invoker.user.id)
			if (isExcludedFromRaidControl || isAlreadyCaught) {
				return
			} else {
				raidManger!!.manageRaid(server, messageChannel, invoker)
				return
			}
		}

		val commandParts = message.substring(serverPrefix.length).split(" ").toTypedArray()
		val attemptedCommandMacro = commandParts[0]

		if (hasMacro(server.id, attemptedCommandMacro)) {
			if (commandParts.size == 1) messageChannel.send(getMacro(server.id, attemptedCommandMacro))
			return
		}

		if (!CommandContainer.hasCommand(attemptedCommandMacro)) {
			messageChannel.send(
				failureEmbed(
					"Invalid Command/Macro",
					"Command/Macro: **$attemptedCommandMacro** does not exist"
				)
			)
			return
		}

		val command = CommandContainer.getCommand(attemptedCommandMacro)
		val commandName = command.name

		if (!checkPermissions(commandName, server, invoker)) {
			messageChannel.send(
				failureEmbed(
					"Insufficient Permission",
					"You cannot invoke **$commandName**, nice try",
					thumbnail = noWay
				)
			)
			return
		}

		val expectedArgsSize = command.argumentTypes.size
		var arguments = formArguments(
			commandParts,
			messageChannel,
			serverPrefix,
			command,
			expectedArgsSize
		) ?: return

		if (arguments.size != expectedArgsSize) {
			messageChannel.send(insufficientArgumentsEmbed(serverPrefix, command, expectedArgsSize))
			return
		}

		if (command.argumentTypes.isNotEmpty()) {
			val parseMap = parseArguments(command, server, arguments)
			if (!parseMap.status) {
				messageChannel.send(invalidArgumentsEmbed(serverPrefix, command, parseMap.errMsg))
				return
			}

			arguments = parseMap.parsedValues.toTypedArray()
		}

		try {
			command.run(serverPrefix, event.jda, event.guild, event.member, messageChannel, arguments)
			event.message.addReaction("\uD83D\uDC40").complete()
			normalLog(command)
		} catch (e: InsufficientPermissionException) {
			messageChannel.send(
				failureEmbed(
					"Failed to invoke command",
					"You need the permission: **${e.permission.getName()}** to use **$commandName**"
				)
			)
		}
	}
}

private fun checkPermissions(commandName: String, server: Guild, invoker: Member): Boolean {
	val serverId = server.id
	val everyoneRoleId = server.getRolesByName("@everyone", false)[0].id
	if (hasPermission(commandName, serverId)) {
		val expectedPermission = getPermission(commandName, serverId)
		val expectedPermissionPosition = server.getRoleById(expectedPermission).position

		val invokerRolelessHasPermission = invoker.roles.isNotEmpty() && invoker.roles[0].position < expectedPermissionPosition
		val invokerRoleHasPermission = invoker.roles.isEmpty() && expectedPermission != everyoneRoleId

		if (invokerRoleHasPermission || invokerRolelessHasPermission) {
			return false
		}
	} else {
		val highestRolePosition = server.roles[0].position
		val canInvoke = invoker.roles.isNotEmpty() && invoker.roles[0].position >= highestRolePosition
		val isBotOwner = invoker.user.id == credentials!!.botOwnerId
		if (!(isBotOwner || invoker.isOwner || canInvoke)) return false
	}

	return true
}

private fun formArguments(commandParts: Array<String>, messageChannel: MessageChannel,
						  serverPrefix: String, c: Command,
						  expectedArgsSize: Int): Array<String>? {
	var arguments = emptyArray<String>()
	if (commandParts.size > 1) {
		val argTypes = c.argumentTypes
		arguments = if (argTypes.any { it is Sentence }) {
			val sentenceArgPosition = argTypes.size

			if (commandParts.size - 1 < sentenceArgPosition) {
				messageChannel.send(insufficientArgumentsEmbed(serverPrefix, c, expectedArgsSize))
				return null
			}

			val sentence = Arrays
				.copyOfRange(
					commandParts,
					sentenceArgPosition,
					commandParts.size)
				.joinToString(" ")

			val tempArgs = Arrays.copyOfRange(commandParts, 1, sentenceArgPosition).toMutableList()
			tempArgs.add(sentence)
			tempArgs.toTypedArray()
		} else {
			Arrays.copyOfRange(commandParts, 1, commandParts.size)
		}
	}

	return arguments
}

private fun invalidArgumentsEmbed(serverPrefix: String, command: Command, errMsg: String) =
	embed {
		title = "Invalid Arguments"
		description = "Invalid arguments passed to the command: **${command.name}**"
		color = red
		thumbnail = shock

		field {
			title = "Error"
			description = errMsg
			inline = false
		}

		field {
			title = "Syntax"
			description = command.syntax
			inline = false
		}

		field {
			title = "Learn more"
			description = "Use the `${serverPrefix}help ${command.name}` to learn more about the command"
			inline = false
		}
	}

private fun insufficientArgumentsEmbed(serverPrefix: String, command: Command, expected: Int) =
	embed {
		title = "Insufficient Arguments"
		color = red
		thumbnail = shock
		description = "Command: **${command.name}** requires **$expected** arguments"

		field {
			title = "Syntax"
			description = "$serverPrefix${findCommand(command.name).syntax}"
			inline = false
		}

		field {
			title = "Example"
			description = "$serverPrefix${findCommand(command.name).example}"
			inline = false
		}
	}