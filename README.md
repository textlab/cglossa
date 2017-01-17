# cglossa

## Installation
### 
Glossa is written in Clojure, which compiles to Java bytecode and runs on the 
Java Virtual Machine (JVM). This means that Java needs to be installed on your
machine, which is probably already the case;  otherwise it can be downloaded from
https://www.java.com or (preferably) installed via the package manager for your system.

Glossa also requires MySQL, which can probably also be installed via your package
manager or alternatively downloaded from http://dev.mysql.com/downloads/mysql/.
Glossa has been tested with MySQL versions >= 5.1.73.

Finally, the default search engine used by Glossa is the IMS Open Corpus Workbench 
(CWB). Some packages are required to compile this system. In Debian-based distributions 
you may need to run:

    apt-get install flex libglib2.0-dev gawk

Follow the installation instructions of [The IMS Open Corpus Workbench
(CWB)](http://cwb.sourceforge.net/download.php#svn). Check out the latest
version from SVN, as described at the bottom of the page, to get support for
UTF-8-encoded corpora.

### Database setup
Glossa uses a core database as well as a separate database for each corpus.
This makes it easy to copy a corpus to a different server (just dump its database 
on the old machine and run the create_corpus.sh script and subsequently import the 
database on the new machine) or to remove a particular corpus (just drop its database
and remove the relevant row in the core database). It also leads to a significant
simplification and speedup of all code related to importing and searching corpora.

In order to set up the core database, run the `src/mysql/setup.sh` script. This
will create the core database with the name `glossa__core`. If you want to change
the prefix used for the core and all the corpus-specific databases to something
other than **glossa**, set the environment variable **GLOSSA_PREFIX**.

The variables **GLOSSA_DB_USER** (default: "glossa") and **GLOSSA_DB_PASSWORD**
also need to be set accordingly.

For example, the following commands:

    export GLOSSA_PREFIX=myglossa
    cd src/mysql
    ./setup.sh

will create the core database with the name **myglossa__core**, and all corpus-specific databases
will also get a **myglossa** prefix instead of **glossa**.

### Creating a corpus
The script `src/mysql/create_corpus.sh` creates a row for the new corpus in the
core database as well as a separate database for the corpus. For example, the command
`src/mysql/create_corpus.sh mycorpus` will create a row in the core database for
the **mycorpus** corpus as well as a database called **glossa_mycorpus** (or optionally with
a different prefix, as explained above). For corpora encoded with the IMS Open Corpus 
Workbench (the default), Glossa will expect to find a corpus with the ID **mycorpus** in 
the CWB registry.

If the corpus has metadata, they can be imported using the scripts 
`src/mysql/import_metadata_categories.sh CORPUS CATEGORIES.TSV` and 
`src/mysql/import_metadata_values.sh CORPUS VALUES.TSV CATEGORIES.TSV`, where *CORPUS*
is the "short name" of the corpus (e.g. **mycorpus**). **TODO**: Describe the format
of the TSV files.

### Creating local users
New users can be added using `src/mysql/adduser.sh`. For the script to work,
the environment variables, described above, need to be set properly.

## Development on Glossa itself

### Quick start:
A simple startup script for the development mode may look as follows:

    #!/bin/sh
    # Set up the environment (you may set other variables here, if needed):
    export GLOSSA_DB_PASSWORD='glossa'
    # Start the server and figwheel:
    lein run >>server.log &
    rlwrap lein figwheel
    # Kill the server after the fighweel session is finished:
    pkill -f " -Dleiningen.original.pwd=`pwd` .* run$"

The webserver is started by default at port 10555. When you see the line
`Successfully compiled "resources/public/app.js" in 21.36 seconds.`, you're
ready to go. Browse to `http://localhost:10555` and enjoy.

Figwheel may also be started without `rlwrap`, but it will then be missing line
editing, persistent history and completion.

### REPL

Clojure REPL can be started in a terminal: `lein repl`, or from Emacs: open a
clj/cljs file in the project, then do `M-x cider-jack-in` (make sure
CIDER is up to date). You may call `(run)` in REPL, which is equivalent to
`lein run` in the startup script.

### External SAML login

External SAML login (used e.g. by Feide) is done via an external daemon that
creates a new session (and possibly a new user) after successful
authentication. To enable external login, `SAML_LOGIN_URL` and
`SAML_LOGOUT_URL` must be set in the startup script. At the Text Laboratory,
the URLs are as follows:

    export SAML_LOGIN_URL='https://www.tekstlab.uio.no/glossa2/login/feide'
    export SAML_LOGOUT_URL='https://www.tekstlab.uio.no/glossa2/logout/feide'

### Deployment

Build Glossa in the production mode:

    lein uberjar

To start the server, set up the environment variables and run:

    java -jar target/cglossa.jar

To stop the server, run:

    pkill -f 'java -jar target/cglossa.jar'

## License

Copyright © 2015-2017 The Text Laboratory, University of Oslo

Distributed under the <a href="http://www.opensource.org/licenses/MIT">MIT License</a>.
