#!/usr/bin/python3
#
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import sys
import argparse
import xml.etree.ElementTree as et
import networkx as nx
import os
import io
from pathlib import *
import multiprocessing
import concurrent
import subprocess
import tempfile
import shutil
import re
import colorsys
import graphviz # 0.8.4
import html
import matplotlib
import matplotlib.cm as cm
from itertools import chain

# Helper functions for traversing the XML graph.

def find_node(graph, idx):
    for node in graph.find('nodes'):
        if int(node.attrib['id']) == idx:
            return node
    assert False

def find_node_properties(graph, idx):
    node = find_node(graph, idx)
    assert node != None
    ps = {}
    for p in node.find('properties'):
        ps[p.attrib['name']] = p.text.strip()
    ps.pop('idx', None)
    return ps

def xml2graphs(xml_root, args):
    graphs = {}
    graph_id = 0
    for group in xml_root:
        method = group.find('method')
        group_name = method.attrib['name']
        short_group_name = method.attrib['shortName'].strip()
        bci = int(method.attrib['bci'])
        for graph in group.findall('graph'):
            graph_name = graph.attrib['name']
            if not matches((graph_id, short_group_name, graph_name), args.filter):
                graph_id += 1
                continue
            # Load the entire graph first.
            G = nx.MultiDiGraph()
            if not args.list:
                for node in graph.find('nodes'):
                    idx = int(node.attrib['id'])
                    properties = find_node_properties(graph, idx)
                    G.add_node(idx, **properties)
                for edge in graph.find('edges'):
                    src = int(edge.attrib['from'])
                    dst = int(edge.attrib['to'])
                    ind = int(edge.attrib['index'])
                    # The XML file sometimes contains (src,dst,ind) duplicates.
                    if not G.has_edge(src, dst, key=ind):
                        G.add_edge(src, dst, key=ind)
            # Load the control-flow graph, if available.
            CFG = None
            if graph.find('controlFlow'):
                CFG = nx.DiGraph()
                if not args.list:
                    for xmlblock in graph.find('controlFlow'):
                        block = int(xmlblock.attrib['name'])
                        # The node order reflects the local schedule.
                        nodes = []
                        for xmlnode in xmlblock.find('nodes'):
                            node = int(xmlnode.attrib['id'])
                            nodes.append(node)
                        CFG.add_node(block, **{'nodes' : nodes})
                        for xmlsucc in xmlblock.find('successors'):
                            succ = int(xmlsucc.attrib['name'])
                            CFG.add_edge(block, succ)
            graphs[graph_id] = ((short_group_name, graph_name), G, CFG)
            graph_id += 1
    return graphs

def check(job_args):
    (job_type, graph_id, graphs, args) = job_args
    if job_type == 'sea':
        check_sea(graph_id, graphs, args)
    elif job_type == 'cfg':
        check_cfg(graph_id, graphs, args)
    else:
        assert(False)

filter_symbols = {'g' : 'int g',
                  'method' : 'str method(int)',
                  'phase' : 'str phase(int)'}
def matches(graph_tuple, filter):
    (g, m, p) = graph_tuple
    method = lambda g : m
    phase  = lambda g : p
    loc = locals()
    filter_locals = dict([(sym, loc[sym]) for sym in filter_symbols.keys()])
    return eval(filter, {}, filter_locals)

highlight_symbols = {'n' : 'int n',
                     'name' : 'str name(int)',
                     'type' : 'str type(int)',
                     'category' : 'str category(int)',
                     'is_cfg' : 'bool is_cfg(int)'}

def check_sea(graph_id, graph, args):
    """
    TODO: comment
    """
    ((short_group_name, graph_name), G) = graph
    # TODO: do something

def block_distance(CFG, b):
    return min([n.get('distance', 0) for (n, _) in CFG.nodes[b]['nodelist']],
               default=1)

def check_cfg(graph_id, graph, args):
    """
    TODO: comment
    """
    ((short_group_name, graph_name), G, CFG) = graph
    if args.verbose:
        print("check_cfg: " + str(graph_id) + " " + short_group_name + " " + graph_name)
    actual = {}
    root = None
    for b in list(CFG.nodes()):
        if root == None:
            root = b
            idom = b
        else:
            assert len(CFG.nodes[b]['nodes']) > 0
            head = CFG.nodes[b]['nodes'][0]
            raw_idom = G.nodes[head].get('idom')
            if raw_idom == None:
                idom = None
            else:
                idom = int(raw_idom)
        actual[b] = idom
    expected = nx.immediate_dominators(CFG, root)
    if actual != expected:
        print("Wrong dominator tree for " + str(graph_id) + " " + short_group_name + " " + graph_name)
        print("expected: " + str(sorted(list(expected.items()))))
        print("actual:   " + str(sorted(list(actual.items()))))

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
        description="Checks different properties in XML graph files.",
        formatter_class=argparse.RawTextHelpFormatter,
        add_help=False,
        usage='%(prog)s [options] XML_FILE')

    io = parser.add_argument_group('input/output options')
    io.add_argument('XML_FILE',
                    help="XML graph file emitted by the HotSpot JVM")
    io.add_argument('--outdir',
                    metavar='DIR',
                    default=os.getcwd(),
                    help="output directory (default: %(default)s)")
    add_feature_argument(io,
                         'dot',
                         "output DOT files for each graph",
                         False)
    add_feature_argument(io,
                         'log',
                         "output log files (.out and .err) for each graph",
                         False)
    add_feature_argument(io,
                         'clean',
                         "remove intermediate files",
                         True)
    add_feature_argument(io,
                         'verbose',
                         "print debug information to the standard output",
                         False)
    io.add_argument('--jobs',
                    metavar='N',
                    type=int,
                    default=multiprocessing.cpu_count(),
                    help="maximum number of parallel jobs (default: %(default)s)")
    io.add_argument('--help',
                    action='help',
                    default=argparse.SUPPRESS,
                    help='Show this help message and exit')
    list_filter = parser.add_argument_group('listing and filtering options')
    add_feature_argument(list_filter,
                         'list',
                         "list properties of each graph and terminate",
                         False)
    list_filter.add_argument('--filter',
                             metavar='EXP',
                             default='True',
                             help=
"""predicate telling whether to check graph g (default: %(default)s)
-- arbitrary Python expression combining the following elements:
""" + '\n'.join(filter_symbols.values()))

    args = parser.parse_args()

    try:
        # Parse XML file.
        if args.verbose:
            print("parsing input file " + args.XML_FILE + " ...")
        tree = et.parse(args.XML_FILE)
        root = tree.getroot()

        # Convert XML to a map from id to ((method, phase), NetworkX graph,
        # maybe CFG) tuples.
        if args.verbose:
            print("converting XML to graphs ...")
        graphs = xml2graphs(root, args)

        # If asked for, list the graphs (id, method, phase).
        if args.verbose or args.list:
            table = [('id', 'method', 'phase')] + \
                [(graph_id, method, phase)
                 for (graph_id, ((method, phase), _, __)) in graphs.items()]
            ws = [max(map(len, map(str, c))) for c in zip(*table)]
            for r in table:
                print('  '.join((str(v).ljust(w) for v, w in zip(r, ws))))
        # If asked explicitly, terminate at this point.
        if args.list:
            return

        # Do the rest of the work in parallel.
        ex = concurrent.futures.ProcessPoolExecutor(max_workers=int(args.jobs))
        jobs = []
        for (graph_id, (name, G, CFG)) in graphs.items():
            jobs.append(['sea', graph_id, (name, G), args])
            if CFG != None:
                jobs.append(['cfg', graph_id, (name, G, CFG), args])
        # The list conversion is just to force evaluation.
        list(ex.map(check, jobs))
    except Exception as error:
        print('Exception: {}'.format(error))
    finally:
        return

if __name__ == '__main__':
    main()
