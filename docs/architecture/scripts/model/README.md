# docs/architecture/scripts/model/

Fragments of the model builder that [`generate-architecture-model.sh`](../generate-architecture-model.sh)
sources at top level, one after another. They are slices of one script, not entry points: each uses the
variables and functions of the fragments sourced before it, so the order in the generator matters.

A fragment is never sourced from inside a function, because `declare` would then make its tables local.
Under `set -e` a fragment must not end on a command that can fail; a fragment that needs it ends with
`return 0`, never `|| true`.
