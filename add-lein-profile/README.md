# Add Lein Profile

This is a project that Cuttle uses to programmatically add a [leiningen profile]
to `~/.lein/profiles.clj`. This allows Cuttle to use lein
plugins/dependencies across user projects without polluting their project.clj.

On startup, Cuttle effectively runs

```
lein run '{:cuttle {:plugins [[lein-pprint "1.1.1"]]}}'
```

so it can include these dependencies on any project with:

```
lein with-profile +cuttle <command>
```

## Details

We could inject plugins/dependencies into the `:user` profile which is included
by default on all projects.  But we decided to avoid handling potential
collisions by creating our own profile `:cuttle`, which is what this tool
helps us do.

The plus sign in `lein with-profile +cuttle` indicates that it is adding
to (rather than overwriting) the default profiles.

[leiningen profile]: https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
