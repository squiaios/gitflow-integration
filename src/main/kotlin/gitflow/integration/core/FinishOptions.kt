package gitflow.integration.core

/** Settings for finishing a flow branch. Options that do not apply to the branch type are ignored. */
class FinishOptions {

    /** Fetch the remote before merging. */
    var fetch: Boolean = false

    /** Push the updated long-lived branches (and the tag) afterwards, and delete the remote flow branch. */
    var push: Boolean = false

    /** Keep the flow branch instead of deleting it. */
    var keep: Boolean = false

    /** Feature and bugfix: always create a merge commit, even when a fast-forward is possible. */
    var noFastForward: Boolean = true

    /** Feature and bugfix: squash the branch into a single commit. */
    var squash: Boolean = false

    /** Feature and bugfix: rebase the branch onto develop before merging. */
    var rebase: Boolean = false

    /** Release and hotfix: tag the merge into the production branch. */
    var tag: Boolean = true

    /** Release and hotfix: message of the annotated tag; a default is used when blank. */
    var tagMessage: String = ""

    /** Release and hotfix: merge the result back into the integration branch. */
    var backMerge: Boolean = true
}
