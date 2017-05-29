    #!/bin/sh

    appdir=$(mktemp -d /tmp/app.XXXXXX)

    cp $(dirname $0)/* $appdir/

    pushd $appdir > /dev/null

    npm install
    if [ $? -ne 0 ]
    then
      echo "FAILD TO BUILD THE CONFIG SCRIPT"
      exit 1
    fi

    npm start
    if [ $? -ne 0 ]
    then
      echo "FAILD TO RUN THE CONFIG SCRIPT"
      exit 2
    fi

    popd > /dev/null

    rm -rf $appdir
