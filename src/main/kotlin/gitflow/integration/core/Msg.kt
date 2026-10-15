package gitflow.integration.core

/**
 * A user-facing message expressed as a resource-bundle key plus arguments.
 *
 * The engine never produces display text: the IDE layer renders each message through
 * `GitFlowBundle`, which keeps the engine free of IDE dependencies and localizable.
 */
class Msg(val key: String, vararg val args: Any?) {

    override fun toString(): String = if (args.isEmpty()) key else key + args.contentToString()
}
