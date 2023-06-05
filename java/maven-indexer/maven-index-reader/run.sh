#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

make -C $SCRIPT_DIR >&2

java -cp $SCRIPT_DIR/target/codeql-java-maven-index-reader-0.0.1-SNAPSHOT.jar:$SCRIPT_DIR/target/lib/lucene-core-4.10.2.jar:$SCRIPT_DIR/target/lib/lucene-codecs-4.10.2.jar com.github.codeql.maven.index.reader.Main "$@"
