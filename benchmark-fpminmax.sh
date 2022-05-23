#!/bin/bash
#
# Run FP min/max benchmarks using different modes.
#
# Pre-requisites: OpenJDK release build with JMH support, e.g. using the
#                 following commands:
#   - bash make/devkit/createJMHBundle.sh
#   - bash configure --with-boot-jdk=$BOOT_JDK_DIR --enable-ccache --with-jtreg=$JTREG_DIR --with-gtest=$GTEST_DIR --with-jmh=build/jmh/jars
#   - make images CONF=release
#   - make build-microbenchmark
#

BUILD_DIR=./build/linux-x86_64-server-release
BENCHMARKS_JAR=$BUILD_DIR/images/test/micro/benchmarks.jar
JVM=$BUILD_DIR/jdk/bin/java
BENCHMARK_CLASS=org.openjdk.bench.vm.compiler.FpMinMaxIntrinsics
WARMUP_ITERATIONS=1
MEASUREMENT_ITERATIONS=1
SIMPLIFY_OPTIONS="-XX:LoopMaxUnroll=0"

for METHOD in fMin fMinReduce
do
    for MODE in branchy branchless
    do
        if [ $MODE == branchy ]; then
            MODE_OPTIONS="-XX:-UseBranchlessFPMinMax"
        fi
        if [ $MODE == branchless ]; then
            MODE_OPTIONS="-XX:+UseBranchlessFPMinMax"
        fi
        OUTPUT_FILE=$METHOD-$MODE.out
        echo "Benchmarking $METHOD in mode '$MODE', see $OUTPUT_FILE..."
        $JVM -jar $BENCHMARKS_JAR $BENCHMARK_CLASS.$METHOD$ \
             -f 1 -wi $WARMUP_ITERATIONS -i $MEASUREMENT_ITERATIONS \
             --jvmArgs "$SIMPLIFY_OPTIONS $MODE_OPTIONS" \
             > $OUTPUT_FILE
    done
done
