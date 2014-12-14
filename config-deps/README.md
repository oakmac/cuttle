# Configure Dependencies

When cljsbuild-ui starts up, we need to make sure the following lein dependencies are available:

- `lein-pprint` for extracting cljsbuild config options project.clj
- `lein-cljsbuild-lib` for a fork of cljsbuild that allows a direct interface to our UI

We want to be able use these without injecting them into user project.clj
files, which may be too intrusive.

To this end, we aim to use this app to add plugins/dependencies to
`~/.lein/profiles.clj`, which contains [profiles] to be used across all projects.

We could inject them into the `:user` profile which is included by default on
all projects.  But we decided to avoid handling potential collisions by
creating our own profile `:cljsbuild-ui`.

cljsbuild-ui will use this custom profile when running `lein`:

```
lein with-profile +cljsbuild-ui <command>
```

The plus sign in `+cljsbuild-ui` indicates that it is adding rather than
overwriting the default profiles.

[profiles]: https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
