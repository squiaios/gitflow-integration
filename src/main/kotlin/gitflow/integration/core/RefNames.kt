package gitflow.integration.core

/** Validation of branch names, following the rules of `git check-ref-format --branch`. */
object RefNames {

    private const val FORBIDDEN = "~^:?*[\\"

    /** Returns why [name] cannot be a branch name, or `null` when it is valid. */
    fun validateBranchName(name: String?): Msg? {
        if (name.isNullOrBlank()) {
            return Msg("validate.name.empty")
        }
        for (c in name) {
            if (c == ' ') {
                return Msg("validate.name.space")
            }
            if (c.code < 0x20 || c.code == 0x7f) {
                return Msg("validate.name.control")
            }
            if (c in FORBIDDEN) {
                return Msg("validate.name.illegalChar", c.toString())
            }
        }
        if (name.startsWith("-") || name.startsWith("/") || name.endsWith("/") || name.endsWith(".")
            || name.contains("..") || name.contains("//") || name.contains("@{") || name == "@"
        ) {
            return Msg("validate.name.malformed")
        }
        for (component in name.split("/")) {
            if (component.startsWith(".") || component.endsWith(".lock")) {
                return Msg("validate.name.malformed")
            }
        }
        return null
    }
}
