package gitflow.integration.core

/** Settings for starting a flow branch. */
class StartOptions {

    /** Fetch the remote first, so the new branch starts from up-to-date commits. */
    var fetch: Boolean = false
}
