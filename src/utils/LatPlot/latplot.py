#!/usr/bin/python3
#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

import argparse
import warnings
import json
import matplotlib.pyplot as plt # v. 3.7.2
import pandas as pd # v. 2.0.3
import numpy as np # v. 1.25.2

non_application_threads = ['Common-Cleaner',
                           'Reference Handler',
                           'Finalizer',
                           'JFR Periodic Tasks',
                           'JFR Recorder Thread',
                           'JFR Shutdown Hook']

def print_duration(duration):
    microseconds = duration.seconds * 1000000 + duration.microseconds
    miliseconds = microseconds / 1000
    return str(round(miliseconds, 1)) + "ms"

def plot_timeline(thread_events, earliest_start):
    fig, ax = plt.subplots()

    for thread in thread_events.keys():
        for (start, duration, end) in thread_events[thread]:
            relative_start = start - earliest_start
            ax.barh(thread,
                    duration.value,
                    left=relative_start.value,
                    color="gray",
                    edgecolor="black",
                    linewidth=1)
            if duration.seconds > 0 or duration.microseconds > 500000:
                ax.text(relative_start.value + duration.value/2,
                        thread,
                        print_duration(duration),
                        horizontalalignment='center',
                        verticalalignment='center')

    ax.xaxis.set_major_locator(plt.MaxNLocator(12))

    (ticks, labels) = plt.xticks()
    labels2 = [print_duration(pd.Timedelta(t)) for t in ticks]
    ax.set_xticks(ticks, labels=labels2, rotation='vertical')

    plt.subplots_adjust(left=0.3, bottom=0.3)
    plt.show()

def plot_histogram(durations):
    x = sorted([round(d.value / 1000) for d in durations])
    plt.hist(x)
    plt.show()

def print_micros(ns):
    return round(ns/1000)

def print_metrics(durations):
    x = [d.value for d in durations]
    print("90th percentile: ", print_micros(np.percentile(x, 90)), "us")
    print("95th percentile: ", print_micros(np.percentile(x, 95)), "us")
    print("99th percentile: ", print_micros(np.percentile(x, 99)), "us")
    print("max:             ", print_micros(max(x)), "us")

def add_feature_argument(parser, feature, help_msg, default):
    """
    Add a Boolean, mutually-exclusive feature argument to a parser.
    """
    if default:
        default_option = '--' + feature
    else:
        default_option = '--no-' + feature
    help_string = help_msg + " (default: " + default_option + ")"
    feature_parser = parser.add_mutually_exclusive_group(required=False)
    feature_lower = feature.replace('-', '_')
    feature_parser.add_argument('--' + feature,
                                dest=feature_lower,
                                action='store_true',
                                help=help_string)
    feature_parser.add_argument('--no-' + feature,
                                dest=feature_lower,
                                action='store_false',
                                help=argparse.SUPPRESS)
    parser.set_defaults(**{feature_lower:default})

def main():
    parser = argparse.ArgumentParser(
        description="Plots VM pause events from a JFR JSON file.",
        formatter_class=argparse.RawTextHelpFormatter,
        add_help=False,
        usage='%(prog)s [options] PLOT_TYPE JSON_FILE')

    io = parser.add_argument_group('input/output options')
    io.add_argument('PLOT_TYPE',
                    help="Type of plot (one of [timeline|histogram|metrics])")
    io.add_argument('JSON_FILE',
                    help="JSON file obtained by running 'jfr print --json' on a JFR file")
    io.add_argument('--help',
                    action='help',
                    default=argparse.SUPPRESS,
                    help='Show this help message and exit')
    df = parser.add_argument_group('data filtering options')
    add_feature_argument(df,
                         'application-threads-only',
                         "Filter out non-application Java threads",
                         True)
    df.add_argument('--min-duration',
                    type=int,
                    default=0,
                    help="minimum event duration to be displayed in the timeline, in micro-seconds (default: %(default)s)")
    args = parser.parse_args()

    with open(args.JSON_FILE, 'r') as json_file:
        data = json.load(json_file)

    thread_key = 'osName'
    thread_events = {}
    earliest_start = None
    durations = []

    for event in data['recording']['events']:
        if event['type'] != 'jdk.VMPause':
            continue
        values = event['values']
        if values['eventThread'] == None:
            warnings.warn("VMPause event with empty eventThread information")
            continue
        thread_id = values['eventThread'][thread_key]
        if args.application_threads_only and (thread_id in non_application_threads):
            continue
        duration = pd.to_timedelta(values['duration'])
        if round(duration.value/1000) < args.min_duration:
            continue
        durations.append(duration)
        if not thread_id in thread_events:
            thread_events[thread_id] = []
        # Remove e.g. '+02:00' suffix, if any
        startTime = values['startTime'].split('+')[0]
        start = pd.to_datetime(startTime,
                               format="%Y-%m-%dT%H:%M:%S.%f")
        if earliest_start == None or start < earliest_start:
            earliest_start = start
        end = start + duration
        event_data = (start, duration, end)
        thread_events[thread_id].append(event_data)

    for thread in thread_events.keys():
        thread_events[thread].sort()

    if args.PLOT_TYPE == 'timeline':
        plot_timeline(thread_events, earliest_start)
    elif args.PLOT_TYPE == 'histogram':
        plot_histogram(durations)
    else:
        print_metrics(durations)

if __name__ == '__main__':
    main()
