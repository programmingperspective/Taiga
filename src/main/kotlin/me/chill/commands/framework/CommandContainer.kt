package me.chill.commands.framework

import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner

class CommandContainer private constructor() {
	init {
		val reflections = Reflections("me.chill.commands.type", MethodAnnotationsScanner())
		reflections
			.getMethodsAnnotatedWith(CommandCategory::class.java)
			.forEach { it.invoke(null) }
	}

	companion object {
		val commandSets = mutableListOf<CommandSet>()

		fun loadContainer() = CommandContainer()

		fun hasCommand(command: String) = commandSets.stream().anyMatch { it.hasCommand(command) }

		fun getCommand(command: String) = createCommandList().stream().filter { it.name == command }.toArray()[0]!!

		fun getCommandNames() = createCommandList().map { it.name }.toTypedArray()

		private fun createCommandList(): MutableList<Command> {
			val commandsFlattened = mutableListOf<Command>()
			commandSets.forEach { commandsFlattened.addAll(it.commands) }
			return commandsFlattened
		}
	}
}

fun commands(create: CommandSet.() -> Unit) {
	val set = CommandSet()
	set.create()
	CommandContainer.commandSets.add(set)
}