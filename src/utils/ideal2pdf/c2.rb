# coding: utf-8
#
# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

module Seafoam
  module Annotators
    # Seafoam annotator for C2's Ideal graph representation. Used by the
    # 'ideal2pdf' script in the same directory.
    class C2Annotator < Annotator
      def self.applies?(graph)
        graph.props.values.any? do |v|
          TRIGGERS.any? { |t| v.to_s.include?(t) }
        end
      end

      def annotate(graph)
        annotate_nodes graph
        annotate_edges graph
        hide_root graph if not @options[:show_root]
        hide_frame_pointer graph if not @options[:show_frame_pointer]
        hide_return_address graph if not @options[:show_return_address]
        hide_io graph if not @options[:show_io]
        reduce_edges graph if @options[:reduce_edges]
      end

      private

      # Whether the node is a simple input one to be inlined.
      def is_simple_input(node)
        node_name = node.props['name']
        if node_name == 'Parm' &&
           node.props['type'] != 'control' &&
           node.inputs.all? { |edge| edge.from.props['name'] == 'Start' }
          return true
        end
        if ['Con', 'ConI', 'ConL', 'ThreadLocal'].include?(node.props['name'])
          return true
        end
        return false
      end

      def node_label(node)
        node_name = node.props['name']
        node_type = node.props['type']
        node_dump_spec = node.props['dump_spec']
        if is_simple_input(node) and node.props['is_con'] and node_dump_spec
          if node_type == 'top'
            value = '⊤'
          else
            value = node_dump_spec.split(':')[1]
            if node_type == 'long:'
              value += 'L'
            end
          end
          return 'C(' + value + ')'
        end
        if is_simple_input(node) and node_name == 'Parm' and
          node.props['short_name']
          return 'P(' + node.props['short_name'].to_s + ')'
        end
        if node_name == 'CallStaticJava'
          dump_components = node_dump_spec.split
          if dump_components.length >= 3
            trap_re = /uncommon_trap\(reason=\'(\w*)\'/
            if dump_components[2] =~ trap_re
              callee = "trap: #{Regexp.last_match(1)}"
            else
              callee = dump_components[2]
            end
              return node_name + '\n(' + callee + ')'
          end
        end
        if node_name == 'CreateEx'
          dump_components = node_dump_spec.split
          if dump_components.length >= 1
            ex_components = dump_components[0].split(':')
            if ex_components.length >= 2
              return node_name + '\n(' + ex_components[1] + ')'
            end
          end
        end
        node_name
      end

      def has_two_or_less_inputs(node)
        [
          'Proj',
          'Bool',
          'If',
          'CreateEx',
          'CountedLoopEnd',
          'Catch'
        ].include?(node.props['name'])
      end

      # Node kind can be one of {info, input, control, effect, virtual, calc,
      # guard, other}.
      def node_kind(node)
        node_name = node.props['name']
        node_type = node.props['type']
        # Scalar type.
        if ['int:',
            'long:',
            'return_address',
            'rawptr:',
            'inst:'].include?(node_type)
          return 'input'
        end
        # Memory type.
        if node_type == 'memory'
          return 'calc'
        end
        # Control type: nodes with type 'control' and nodes with type 'tuple:'
        # where all types in the tuple are known to be control.
        if node_type == 'control' or
          (node_type == 'tuple:' and
           [
             'If',
             'Catch',
             'CountedLoopEnd',
             'OuterStripMinedLoopEnd'
           ].include?(node_name))
          return 'control'
        end
        # Mixed type.
        if node_type == 'tuple:'
          return 'effect'
        end
        # Other type.
        if ['abIO', 'bottom', 'top', 'ary:'].include?(node_type)
          return 'virtual'
        end
        raise "unmatched type: " + node_type
      end

      # Edge kind can be one of {info, control, loop, data}.
      EDGE_KIND_MAP = {
        # Scalar type.
        'input'   => 'info',
         # Memory type.
        'calc'    => 'data',
         # Control type.
        'control' => 'control',
        # Mixed type.
        'effect'  => 'loop',
        # Other type
        'virtual' => 'none'
      }

      # Edge kind based on 'from' node.
      def edge_kind(edge)
        EDGE_KIND_MAP[node_kind(edge.from)] || 'other'
      end

      # Annotate nodes with their label and kind.
      def annotate_nodes(graph)
        graph.nodes.each_value do |node|
          if node.props[:label].nil?
            node.props[:label] = node_label(node)
          end
          node.props[:kind] ||= node_kind(node)
        end
      end

      # Annotate edges with their label and kind.
      def annotate_edges(graph)
        graph.edges.each do |edge|
          kind = edge_kind(edge)
          edge.props[:kind] ||= kind
          if kind != 'control' \
            and not has_two_or_less_inputs(edge.to)
            edge.props[:label] = edge.props[:name]
          end
        end
      end

      # Hide root node.
      def hide_root(graph)
        graph.nodes.each_value do |node|
          if node.props['name'] == 'Root'
            node.props[:hidden] = true
          end
        end
      end

      # Hide frame pointer nodes.
      def hide_frame_pointer(graph)
        graph.nodes.each_value do |node|
          if node.props['dump_spec'] == 'FramePtr'
            node.props[:hidden] = true
          end
        end
      end

      # Hide return address nodes.
      def hide_return_address(graph)
        graph.nodes.each_value do |node|
          if node.props['dump_spec'] == 'ReturnAdr'
            node.props[:hidden] = true
          end
        end
      end

      # Hide I/O nodes.
      def hide_io(graph)
        graph.nodes.each_value do |node|
          if node.props['dump_spec'] == 'I_O'
            node.props[:hidden] = true
          end
        end
      end

      # Reduce edges to simple constants and parameters by inlining them.
      def reduce_edges(graph)
        graph.nodes.each_value do |node|
          if is_simple_input(node)
            node.props[:inlined] = true
          end
        end
      end

      # Graph property values that indicate it is a C2 graph.
      TRIGGERS = %w[
        C2Compiler
      ]

    end
  end
end
