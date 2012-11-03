#!/bin/sh

# Python
########################################################################
rm -rf ../python/plow/rpc
rm -rf ../python/plow/rndaemon/rpc

thrift --gen py:new_style:utf8strings -out ../python/plow plow.thrift
thrift --gen py:new_style:utf8strings -out ../python/plowapp/rndaemon rnd.thrift

thrift --gen py:new_style:utf8strings -out ../python/plow/rpc common.thrift
thrift --gen py:new_style:utf8strings -out ../python/plowapp/rndaemon/rpc common.thrift

cp plow__init__.py ../python/plow/__init__.py

# Java
########################################################################
rm -rf ../java/src/main/java/
mkdir -p ../java/src/main/java
thrift -gen java:java5 --out ../java/src/main/java common.thrift
thrift -gen java:java5 -out ../java/src/main/java plow.thrift
thrift -gen java:java5 -out ../java/src/main/java rnd.thrift

# C++
########################################################################

OUT="../cpp/src/core/rpc"
EXP="../cpp/src/export/plow"

rm -rf $OUT
mkdir $OUT

thrift --gen cpp --out $OUT common.thrift
thrift --gen cpp --out $OUT plow.thrift

mv $OUT/common_*.h $EXP
mv $OUT/plow_*.h $EXP

rm $OUT/RpcService_server.skeleton.cpp