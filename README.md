# cljsbuild-ui

A user interface for the ClojureScript Compiler.

<img src="screenshots/2014-10-30-preview.png">

## Goal

Getting started with ClojureScript is one of the more difficult things about
the language. JS devs are often unfamiliar with Leiningen and project.clj seems
very foreign initially. The goal of this project is to reduce the barrier to
entry for new ClojureScript devs.

We also want to improve the usability of the compiler in day-to-day workflows.
The user should be able to build/clean their projects with a few clicks, and
they should see meaningful errors and warnings at a glance.

## Development Setup

Development is currently in a very early stage and things should be expected to
break on a regular basis.

1. Install [Leiningen] and [Node.js].
1. Run the following in the project directory

    ```sh
    # install node.js dependencies
    npm install

    # install grunt (may need sudo)
    npm install -g grunt-cli

    # downloads lein jar to be packaged in our app
    grunt curl

    # downloads atom-shell
    grunt download-atom-shell

    # compile LESS file
    grunt less

    # compile ClojureScript files (this may take a minute)
    lein cljsbuild once

    # launch on Mac/Linux/Cygwin
    ./launch.sh

    # launch on Windows
    launch.bat
    ```

If you want Chrome Dev Tools to auto-open when you start the program, add a
`config.json` file in the `cljsbuild-ui/` folder. An `example.config.json` is
provided.

## License

All code licensed under the terms of the [MIT
License](https://github.com/oakmac/cljsbuild-ui/blob/master/LICENSE.md).

[Leiningen]:http://leiningen.org
[Node.js]:http://nodejs.org
[Atom Shell]:https://github.com/atom/atom-shell
