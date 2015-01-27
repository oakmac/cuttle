# Cuttle

A user interface for the ClojureScript Compiler.

<img src="screenshots/2015-01-26 cuttle v1.0.png">

Desktop notifications on errors and warnings:

<img src="screenshots/2015-01-26 linux-desktop-notifications.png">

## Goal

Getting started with ClojureScript is one of the more difficult things about the
language. JavaScript developers are often unfamiliar with Leiningen and
project.clj seems very foreign initially. The goal of this project is to reduce
the barrier to entry for new ClojureScript devs.

We also want to improve the usability of the compiler in day-to-day workflows.
The user should be able to build/clean their projects with a few clicks, and
they should see meaningful errors and warnings at a glance.

## Development Setup

1. Install [Leiningen] and [Node.js].
1. One-time setup. Run from the project directory:

    ```sh
    # setup on linux/mac
    ./setup.sh

    # setup on windows
    setup.bat
    ```

1. Compile when files change:

    ```sh
    # compile LESS file
    grunt less

    # compile ClojureScript files (this may take a minute)
    lein cljsbuild once
    ```

1. Launch to try it out:

    ```sh
    # launch on Mac/Linux/Cygwin
    ./launch.sh

    # launch on Windows
    launch.bat
    ```

1. Assemble a release (can only do this for your OS):

    ```sh
    # Create a release folder
    ./release.sh

    # Create a release zip
    ./release.sh -z
    ```

If you want Chrome Dev Tools to auto-open when you start the program, add a
`config.json` file in the `app/` folder. An `example.config.json` is
provided. This is disabled in the release.

## About the Name

Cuttle is named after the [Cuttlefish] because [Shaun] likes cephalopods.

The first three letters of Cuttle might stand for "ClojureScript User Tool", but
the last three letters don't stand for anything because Cuttle is not an
acronym.

Cuttle should be capitalized like a proper noun when used in a sentence.

## License

All code licensed under the terms of the [MIT
License](https://github.com/oakmac/cuttle/blob/master/LICENSE.md).

[Leiningen]:http://leiningen.org
[Node.js]:http://nodejs.org
[Atom Shell]:https://github.com/atom/atom-shell
[Cuttlefish]:http://en.wikipedia.org/wiki/Cuttlefish
[Shaun]:https://github.com/shaunlebron
