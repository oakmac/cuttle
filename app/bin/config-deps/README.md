# Configure Dependencies

When cljsbuild-ui starts up, we need to make sure the following lein dependencies are available:

- `lein-pprint` for extracting cljsbuild config options project.clj
- `lein-cljsbuild-lib` for a fork of cljsbuild that allows a direct interface to our UI

We want to be able use these without injecting them into user project.clj
files, which may be too intrusive.

To this end, we aim to use this app to add dependencies to
`~/.lein/profiles.clj`, which contains [profiles] to be used across all projects.

We can either inject them into the `:user` profile, or create our own
`:cljsbuild-ui` to avoid handling collisions with existing dependencies in the
user profile.

The user profile is used by default on all projects.  But if we go with the
custom profile, we can use it with:

```
lein with-profile +cljsbuild-ui ...
```

The plus sign in `+cljsbuild-ui` indicates that it is adding rather than
overwriting the default profiles.

[profiles]: https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
