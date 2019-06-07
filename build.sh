#!/usr/bin/env bash

set -ex

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
OPT_DIR=${DIR}/opt

mkdir -p ${OPT_DIR}
pushd ${OPT_DIR}


JDK8_DIR="${OPT_DIR}/jdk1.8.0_131"
if [ ! -d ${JDK8_DIR} ];then
wget -O- --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" \
    http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.tar.gz | tar -zxvf -
fi

export PATH=${JDK8_DIR}/bin:${PATH}


MAVEN_DIR=${OPT_DIR}/apache-maven-3.5.4
if [[ ! -d ${MAVEN_DIR} ]]; then
wget -O- http://mirrors.sonic.net/apache/maven/maven-3/3.5.4/binaries/apache-maven-3.5.4-bin.tar.gz | tar zxvf -
fi
ANT_DIR=${OPT_DIR}/apache-ant-1.9.13
if [[ ! -d ${ANT_DIR} ]]; then
wget -O- https://www.apache.org/dist/ant/binaries/apache-ant-1.9.13-bin.tar.gz | tar zxvf -
fi

# Download samtools and index reference
samtools_version="1.3.1"
SAMTOOLS_DIR=${OPT_DIR}/samtools-${samtools_version}
if [[ ! -d $SAMTOOLS_DIR ]]; then
    wget -O- https://github.com/samtools/samtools/releases/download/$samtools_version/samtools-$samtools_version.tar.bz2 | tar xfj -
    pushd ${SAMTOOLS_DIR} && make
    popd
fi

# Download ART
ART_DIR=${OPT_DIR}/ART
if [[ ! -d ${ART_DIR} ]]; then
    mkdir -p ${ART_DIR}
    pushd ${ART_DIR}
    wget -O- https://www.niehs.nih.gov/research/resources/assets/docs/artbinmountrainier2016.06.05linux64.tgz | tar xfz -
    popd
fi

popd
export JAVA_HOME=${OPT_DIR}/jdk1.8.0_131
version=$(git describe | sed 's/^v//')
${MAVEN_DIR}/bin/mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$version
${MAVEN_DIR}/bin/mvn package

git submodule init
git submodule update
pushd rtg-tools
rm -rf rtg-tools-*
${ANT_DIR}/bin/ant zip-nojre
unzip dist/rtg-tools*.zip
cp rtg-tools*/RTG.jar $DIR
popd
