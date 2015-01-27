![Cuttle](cuttle-banner.png)

Cuttle is a standalone application that aims to be the simplest way for
newcomers to try ClojureScript.  It provides a user-friendly interface,
allowing you to build projects by clicking a button, and to see meaningful
warnings and errors at a glance.  In a way, it provides the builder parts of an
IDE without making you leave your favorite editor.

<img src="screenshots/2015-01-27-init.png" width="350px"> <img src="screenshots/2015-01-27-error.png" width="350px">

Under the hood, Cuttle is not hiding a lot of magic from you.  It uses standard
ClojureScript workflow tools to perform its operations.  It uses Leiningen with
the lein-cljsbuild plugin, and the mies template for creating new projects.
Thus, it should produce the same expected behavior as these standard tools.

Cuttle is itself a ClojureScript application which runs on a Node/Chromium
amalgamation called Atom Shell. :)  We hope this tool encourages you to explore
building ClojureScript apps for the browser, Node, and other JS-targetted
platforms.

## Installation

1. [Install JRE 7+](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)
1. [Get latest Cuttle for your OS](https://github.com/oakmac/cuttle/releases)

## Future

We welcome your ideas, bug reports, and pull requests!

With the ClojureScript tooling community rapidly growing, we hope to keep
improving the user experience to integrate new workflows, while also keeping
its behavior simple and predictable.  Some ideas we are thinking about:

- [Integration with Figwheel](https://github.com/oakmac/cuttle/issues/53)
- a basic REPL for experimenting
- [a full project-connected REPL](https://github.com/oakmac/cuttle/issues/54)
- an interface for reading/modifying project.clj
- [a test-runner for cljs.test](https://github.com/oakmac/cuttle/issues/28)

## Development Setup

To setup a environment for building Cuttle:  (you can of course replace the
leiningen bits with Cuttle ;p)

1. Install [Leiningen] and [Node.js].
1. One-time setup. Run from the project directory:

    ```sh
    # setup on linux/mac
    scripts/setup.sh

    # setup on windows
    scripts/setup.bat
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
    scripts/launch.sh

    # launch on Windows
    scripts/launch.bat
    ```

1. Assemble a release (can only do this for your OS):

    ```sh
    # Create a release folder
    ./release.sh

    # Create a release zip
    ./release.sh -z
    ```

## About the Name

Cuttle is named after the [Cuttlefish] because [Shaun] likes cephalopods.
The logo is modeled after its uniquely shaped pupils.

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
[Cuttlefish]:https://flic.kr/p/8oVLuC
[Shaun]:https://github.com/shaunlebron
