# Configure Dependencies

This is a project that cljsbuild-ui uses to programmatically add a [leiningen profile]
to `~/.lein/profiles.clj`. This allows cljsbuild-ui to use lein
plugins/dependencies across user projects without polluting their project.clj.

On startup, cljsbuild-ui effectively runs

```
lein run '{:cljsbuild-ui {:plugins [[lein-pprint "1.1.1"]]}}'
```

so it can include these dependencies on any project with:

```
lein with-profile +cljsbuild-ui <command>
```

## Details

We could inject plugins/dependencies into the `:user` profile which is included
by default on all projects.  But we decided to avoid handling potential
collisions by creating our own profile `:cljsbuild-ui`, which is what this tool
helps us do.

The plus sign in `lein with-profile +cljsbuild-ui` indicates that it is adding
to (rather than overwriting) the default profiles.

[leiningen profile]: https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
